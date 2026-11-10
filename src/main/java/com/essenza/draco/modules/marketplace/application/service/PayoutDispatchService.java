package com.essenza.draco.modules.marketplace.application.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import com.essenza.draco.modules.marketplace.application.MarketplacePermissions;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.BankMatchDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.BatchDetailDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.ProviderBankDto;
import com.essenza.draco.modules.marketplace.application.port.PayoutDispatcher;
import com.essenza.draco.modules.marketplace.application.port.PayoutDispatcher.DispatchException;
import com.essenza.draco.modules.marketplace.application.port.PayoutDispatcher.DispatchItem;
import com.essenza.draco.modules.marketplace.application.port.PayoutDispatcher.DispatchRequest;
import com.essenza.draco.modules.marketplace.application.port.PayoutDispatcher.Dispatched;
import com.essenza.draco.modules.marketplace.application.port.PayoutDispatcher.TxStatus;
import com.essenza.draco.modules.marketplace.application.port.PayoutStore;
import com.essenza.draco.modules.marketplace.application.port.PayoutStore.BatchKey;
import com.essenza.draco.modules.marketplace.application.port.PayoutStore.BatchRow;
import com.essenza.draco.modules.marketplace.application.port.PayoutStore.DispatchRow;
import com.essenza.draco.modules.marketplace.application.port.SellerLedgerStore;
import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutDispatchRules;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutRules;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutRules.BatchStatus;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutRules.ItemStatus;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Dispersión automática de un lote de pagos (Wompi Pagos a terceros): envía los pagos pendientes al
 * proveedor y, al sincronizar, asienta SETTLEMENT (clave PAYOUT:{pago}) por cada pago aprobado y deja
 * rechazados los fallidos. La llamada al proveedor va fuera de transacción: antes se marca el lote como
 * enviado (bloquea el pago manual) y después se guarda el resultado. Un envío con resultado incierto
 * (timeout, 5xx) se resuelve buscando el lote por referencia antes de volver a enviarlo. El webhook solo
 * dispara la consulta al proveedor: nunca se confía en su contenido. Permiso: COMISIONES.GESTIONAR.
 */
@Service
public class PayoutDispatchService {

    private static final Logger log = LoggerFactory.getLogger(PayoutDispatchService.class);
    static final String SYSTEM_ID = "system:payouts";
    /** Sin rastro del lote en el proveedor tras este tiempo, el envío se da por fallido. */
    static final Duration LOST_AFTER = Duration.ofMinutes(30);

    private final PayoutStore store;
    private final SellerLedgerStore ledger;
    private final PayoutService payouts;
    private final PayoutDispatcher dispatcher;
    private final ActorProvider actors;
    private final Clock clock;
    private final TransactionOperations tx;

    @Autowired
    public PayoutDispatchService(PayoutStore store, SellerLedgerStore ledger, PayoutService payouts, PayoutDispatcher dispatcher,
                                 ActorProvider actors, Clock marketplaceClock, PlatformTransactionManager transactions) {
        this(store, ledger, payouts, dispatcher, actors, marketplaceClock, new TransactionTemplate(transactions));
    }

    public PayoutDispatchService(PayoutStore store, SellerLedgerStore ledger, PayoutService payouts, PayoutDispatcher dispatcher,
                                 ActorProvider actors, Clock marketplaceClock, TransactionOperations tx) {
        this.store = store;
        this.ledger = ledger;
        this.payouts = payouts;
        this.dispatcher = dispatcher;
        this.actors = actors;
        this.clock = marketplaceClock;
        this.tx = tx;
    }

    private record Prepared(DispatchRequest request, String idempotencyKey) {
    }

    /** Envía los pagos pendientes del lote al proveedor. */
    public BatchDetailDto dispatch(long batchId) {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_MANAGE);
        MarketplaceRuleViolation.check(dispatcher.configured(), "La dispersión automática no está configurada (llaves de "
                + dispatcher.provider() + " o ambiente de producción sin habilitar).");
        BatchRow current = store.batch(actor.companyId(), batchId, false).orElseThrow(() -> notFound(batchId));
        // Un envío anterior marcado como fallido pudo crear el lote: si el proveedor lo tiene, se adopta en vez de reenviar.
        if (current.dispatch() != null && "FAILED".equals(current.dispatch().status())) {
            Optional<String> existing = dispatcher.findByReference(current.summary().number());
            if (existing.isPresent()) {
                inTx(() -> store.markDispatch(batchId, dispatcher.provider(), "SENT", existing.get(), null,
                        "El proveedor ya tenía el lote: se retoma.", actor.userId(), clock.instant()));
                syncBatch(actor.companyId(), batchId);
                return payouts.get(batchId);
            }
        }
        Prepared prepared = tx.execute(s -> prepare(actor, batchId));
        try {
            Dispatched sent = dispatcher.dispatch(prepared.request());
            inTx(() -> store.markDispatch(batchId, dispatcher.provider(), "SENT", sent.payoutId(), null,
                    "Enviado (" + (sent.status() == null ? "sin estado" : sent.status()) + ").", actor.userId(), clock.instant()));
            log.info("Lote de pagos {} enviado a {} ({}): {}", batchId, dispatcher.provider(), dispatcher.environment(), sent.payoutId());
        } catch (RuntimeException failure) {
            DispatchException ex = failure instanceof DispatchException d ? d
                    : new DispatchException("Error inesperado al enviar (" + failure.getClass().getSimpleName() + ").", true);
            String detail = ex.uncertain()
                    ? "Resultado incierto: " + ex.getMessage() + " Sincroniza para confirmarlo antes de cualquier otra acción."
                    : "No se pudo enviar: " + ex.getMessage();
            inTx(() -> store.markDispatch(batchId, dispatcher.provider(), ex.uncertain() ? "SENT" : "FAILED", null,
                    null, detail, actor.userId(), clock.instant()));
            log.warn("Envío del lote de pagos {} a {}: {}", batchId, dispatcher.provider(), detail);
        }
        return payouts.get(batchId);
    }

    private Prepared prepare(RequestActor actor, long batchId) {
        BatchRow batch = store.batch(actor.companyId(), batchId, true).orElseThrow(() -> notFound(batchId));
        MarketplaceRuleViolation.check(BatchStatus.OPEN.name().equals(batch.summary().status()),
                "El lote " + batch.summary().number() + " ya está cerrado o anulado.");
        String previous = batch.dispatch() == null ? null : batch.dispatch().status();
        if ("SENT".equals(previous) || "DONE".equals(previous)) {
            throw new ConflictException("El lote " + batch.summary().number() + " ya se envió a dispersión.");
        }
        List<DispatchRow> rows = store.dispatchRows(batchId).stream().filter(r -> ItemStatus.PENDING.name().equals(r.status())).toList();
        MarketplaceRuleViolation.check(!rows.isEmpty(), "El lote no tiene pagos pendientes.");
        List<String> problems = payouts.dispatchProblems(batchId);
        MarketplaceRuleViolation.check(problems.isEmpty(), "Faltan datos para la dispersión: " + String.join("; ", problems) + ".");
        // Igual que el pago manual: nunca se envía más de lo que la tienda le debe hoy a cada vendedor.
        Map<Long, BigDecimal> balances = ledger.balances(actor.companyId());
        List<DispatchItem> items = new ArrayList<>();
        for (DispatchRow row : rows) {
            BigDecimal owed = PayoutRules.owed(balances.get(row.sellerId()));
            if (row.amount().compareTo(owed) > 0) {
                throw new ConflictException("Al vendedor " + row.sellerId() + " hoy se le deben " + owed.toPlainString()
                        + ", menos que su pago en el lote (" + row.amount().toPlainString() + "). Recházalo antes de enviar.");
            }
            String reference = PayoutDispatchRules.reference(batch.summary().number(), row.itemId());
            store.setItemProvider(row.itemId(), reference, null, null, null);
            boolean key = "BRE_B_KEY".equals(row.accountType());
            items.add(new DispatchItem(reference, PayoutDispatchRules.cents(row.amount()), row.holderName(), row.email(),
                    row.holderDocType(), row.holderDocNumber(), key ? row.accountNumber() : null, key ? null : row.providerBankId(),
                    key ? null : PayoutDispatchRules.accountType(row.accountType()), key ? null : row.accountNumber()));
        }
        // Tras un fallo definitivo (el proveedor no creó nada) va una llave nueva: la anterior devolvería el mismo error 24 h.
        String key = PayoutDispatchRules.idempotencyKey(batch.summary().number(), batchId);
        if ("FAILED".equals(previous)) {
            key = clip(key, 52) + "-R" + (clock.instant().getEpochSecond() % 10_000_000_000L);
        }
        store.markDispatch(batchId, dispatcher.provider(), "SENT", null, key, "Enviando…", actor.userId(), clock.instant());
        return new Prepared(new DispatchRequest(batch.summary().number(), key, items), key);
    }

    /** Consulta el resultado en el proveedor (botón del lote). */
    public BatchDetailDto sync(long batchId) {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_MANAGE);
        store.batch(actor.companyId(), batchId, false).orElseThrow(() -> notFound(batchId));
        syncBatch(actor.companyId(), batchId);
        return payouts.get(batchId);
    }

    /** Sincronizador programado: todos los lotes en dispersión. Uno que falle no detiene los demás. */
    public int syncAll() {
        int done = 0;
        for (BatchKey key : store.sentBatches(dispatcher.provider())) {
            try {
                if (syncBatch(key.companyId(), key.batchId())) {
                    done++;
                }
            } catch (RuntimeException ex) {
                log.warn("No se pudo sincronizar el lote de pagos {}: {}", key.batchId(), ex.getMessage());
            }
        }
        return done;
    }

    /** Aviso del proveedor: solo dispara la consulta del lote al que se refiere (si existe). */
    public void onProviderEvent(List<String> candidateIds) {
        for (String id : candidateIds) {
            if (id == null || id.isBlank() || id.length() > 64) {
                continue;
            }
            Optional<BatchKey> key = store.batchByProvider(dispatcher.provider(), id.trim());
            if (key.isPresent()) {
                syncBatch(key.get().companyId(), key.get().batchId());
                return;
            }
        }
    }

    /** @return true si el lote quedó con todos sus pagos resueltos */
    boolean syncBatch(long companyId, long batchId) {
        BatchRow batch = store.batch(companyId, batchId, false).orElseThrow(() -> notFound(batchId));
        if (batch.dispatch() == null || !"SENT".equals(batch.dispatch().status())) {
            return false;
        }
        String payoutId = batch.dispatch().providerBatchId();
        if (payoutId == null) {
            Optional<String> found = dispatcher.findByReference(batch.summary().number());
            if (found.isEmpty()) {
                Instant sentAt = batch.dispatch().dispatchedAt();
                if (sentAt != null && sentAt.plus(LOST_AFTER).isBefore(clock.instant())) {
                    inTx(() -> store.markDispatch(batchId, dispatcher.provider(), "FAILED", null, null,
                            "El proveedor no registra el lote: puedes volver a enviarlo.", SYSTEM_ID, clock.instant()));
                }
                return false;
            }
            payoutId = found.get();
            String id = payoutId;
            inTx(() -> store.markDispatch(batchId, dispatcher.provider(), "SENT", id, null,
                    "Lote confirmado en el proveedor.", SYSTEM_ID, clock.instant()));
        }
        List<TxStatus> statuses = dispatcher.transactions(payoutId);
        String providerBatchId = payoutId;
        Boolean finished = tx.execute(s -> apply(companyId, batchId, providerBatchId, statuses));
        return Boolean.TRUE.equals(finished);
    }

    private boolean apply(long companyId, long batchId, String providerBatchId, List<TxStatus> statuses) {
        BatchRow batch = store.batch(companyId, batchId, true).orElseThrow(() -> notFound(batchId));
        if (batch.dispatch() == null || !"SENT".equals(batch.dispatch().status())) {
            return false;
        }
        Map<String, TxStatus> byReference = new HashMap<>();
        statuses.forEach(t -> {
            if (t.reference() != null) {
                byReference.put(t.reference(), t);
            }
        });
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        String actorName = dispatcher.provider();
        for (DispatchRow row : store.dispatchRows(batchId)) {
            if (!ItemStatus.PENDING.name().equals(row.status()) || row.providerReference() == null) {
                continue;
            }
            TxStatus status = byReference.get(row.providerReference());
            if (status == null) {
                continue;
            }
            store.setItemProvider(row.itemId(), null, status.id(), status.status(), status.detail());
            PayoutDispatchRules.Outcome outcome = PayoutDispatchRules.outcome(status.status());
            if (outcome == PayoutDispatchRules.Outcome.PAID) {
                // El dinero ya salió: se asienta lo pagado aunque el saldo haya cambiado (lo ajusta el estado de cuenta).
                String reference = clip(status.id() == null ? providerBatchId : status.id(), 60);
                ledger.post(companyId, new SellerLedgerStore.NewEntry(row.sellerId(), "SETTLEMENT", row.amount(), "COP", today, null,
                        BigDecimal.ZERO, clip(batch.summary().number() + " · " + actorName + " " + reference, 80), null, null,
                        "PAYOUT:" + row.itemId(), "Dispersión automática"), SYSTEM_ID, actorName, now);
                store.settleItem(row.itemId(), ItemStatus.PAID.name(), reference, today, "Dispersión automática", SYSTEM_ID, actorName, now);
            } else if (outcome == PayoutDispatchRules.Outcome.FAILED) {
                store.settleItem(row.itemId(), ItemStatus.REJECTED.name(), null, null,
                        clip("Rechazado por " + actorName + (status.detail() == null ? "" : ": " + status.detail()), 500), SYSTEM_ID,
                        actorName, now);
            }
        }
        List<DispatchRow> after = store.dispatchRows(batchId);
        int pending = (int) after.stream().filter(r -> ItemStatus.PENDING.name().equals(r.status())).count();
        int paid = (int) after.stream().filter(r -> ItemStatus.PAID.name().equals(r.status())).count();
        if (pending == 0) {
            store.markDispatch(batchId, dispatcher.provider(), "DONE", null, null, "Todos los pagos con resultado.", SYSTEM_ID, now);
            BatchStatus status = PayoutRules.statusOf(pending, paid);
            store.setBatchStatus(batchId, status.name(), null, status == BatchStatus.OPEN ? null : now);
            return true;
        }
        return false;
    }

    // ── Entidades del proveedor ─────────────────────────────────────────────

    /** Entidades del proveedor y cuál del catálogo está ligada a cada una. */
    public List<ProviderBankDto> providerBanks() {
        actors.current().require(MarketplacePermissions.BILLING_MANAGE);
        MarketplaceRuleViolation.check(dispatcher.configured(), "La dispersión automática no está configurada.");
        Map<String, Long> linked = new HashMap<>();
        store.banks(true).forEach(b -> {
            if (b.wompiBankId() != null) {
                linked.put(b.wompiBankId(), b.id());
            }
        });
        return dispatcher.banks().stream().map(b -> new ProviderBankDto(b.id(), b.name(), b.code(), linked.get(b.id()))).toList();
    }

    /** Liga por código o nombre las entidades del catálogo que aún no tienen id del proveedor. */
    public BankMatchDto matchProviderBanks() {
        actors.current().require(MarketplacePermissions.BILLING_MANAGE);
        MarketplaceRuleViolation.check(dispatcher.configured(), "La dispersión automática no está configurada.");
        List<PayoutDispatcher.ProviderBank> banks = dispatcher.banks();
        Map<String, PayoutDispatcher.ProviderBank> byCode = new HashMap<>();
        Map<String, PayoutDispatcher.ProviderBank> byName = new HashMap<>();
        for (PayoutDispatcher.ProviderBank b : banks) {
            if (b.code() != null && !b.code().isBlank()) {
                byCode.putIfAbsent(b.code().trim(), b);
            }
            if (b.name() != null) {
                byName.putIfAbsent(normalize(b.name()), b);
            }
        }
        return tx.execute(s -> {
            int matched = 0;
            List<String> unmatched = new ArrayList<>();
            Map<String, Long> linked = new HashMap<>();
            for (PayoutStore.BankRow row : store.banks(true)) {
                if (row.wompiBankId() != null) {
                    linked.put(row.wompiBankId(), row.id());
                    continue;
                }
                PayoutDispatcher.ProviderBank hit = row.achCode() == null ? null : byCode.get(row.achCode().trim());
                if (hit == null) {
                    hit = byName.get(normalize(row.name()));
                }
                if (hit != null && hit.id() != null && hit.id().matches("[A-Za-z0-9-]{1,40}") && !linked.containsKey(hit.id())) {
                    store.setProviderBankId(row.id(), hit.id());
                    linked.put(hit.id(), row.id());
                    matched++;
                } else if (row.active()) {
                    unmatched.add(row.name());
                }
            }
            List<ProviderBankDto> dto = banks.stream().map(b -> new ProviderBankDto(b.id(), b.name(), b.code(), linked.get(b.id())))
                    .toList();
            return new BankMatchDto(matched, unmatched, dto);
        });
    }

    private void inTx(Runnable work) {
        tx.execute(s -> {
            work.run();
            return null;
        });
    }

    static String normalize(String name) {
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
        return plain.replaceAll("\\b(S\\.?A\\.?S?|BANCO|BANK|COLOMBIA)\\b", " ").replaceAll("[^A-Z0-9]+", "");
    }

    private static NotFoundException notFound(long id) {
        return new NotFoundException("Lote de pagos no encontrado: " + id);
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
