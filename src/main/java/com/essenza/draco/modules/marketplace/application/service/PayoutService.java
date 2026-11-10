package com.essenza.draco.modules.marketplace.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.marketplace.application.MarketplacePermissions;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.BankAccountDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.BankDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.ColumnDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.ExportFile;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.FormatDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.FormatPreviewDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.LayoutDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.SaveFormatRequest;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutFileLayout;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.SaveBankRequest;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.BatchDetailDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.BatchSummaryDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.CreateBatchRequest;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.CreateItem;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.ItemDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.PayRequest;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.PayeeDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.PreviewDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.ReasonRequest;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.SaveBankAccountRequest;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.DispatchDto;
import com.essenza.draco.modules.marketplace.application.port.PayoutDispatcher;
import com.essenza.draco.modules.marketplace.application.port.PayoutStore;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutDispatchRules;
import com.essenza.draco.modules.marketplace.application.port.PayoutStore.BatchRow;
import com.essenza.draco.modules.marketplace.application.port.PayoutStore.SellerRef;
import com.essenza.draco.modules.marketplace.application.port.PayoutStore.Stored;
import com.essenza.draco.modules.marketplace.application.port.SellerLedgerStore;
import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutRules;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutRules.BankAccount;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutRules.BatchStatus;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutRules.ItemStatus;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Pagos a vendedores por lote: lo que la tienda les debe por lo que cobró por ellos, menos lo
 * retenido de órdenes no entregadas o entregadas hace menos de {@code essenza.marketplace.payouts.hold-days}.
 * El lote copia la cuenta bancaria de cada vendedor; marcar un pago como hecho asienta SETTLEMENT en
 * su estado de cuenta (clave PAYOUT:{pago}). No mueve dinero: la transferencia la hace la tienda en su banco
 * (el lote se descarga en CSV). Permisos: COMISIONES.VER y COMISIONES.GESTIONAR.
 */
@Service
@Transactional
public class PayoutService {

    static final int MAX_ITEMS = 500;

    private final PayoutStore store;
    private final SellerLedgerStore ledger;
    private final ActorProvider actors;
    private final Clock clock;
    private final int holdDays;
    private final PayoutDispatcher dispatcher;

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();
    static final Set<String> EXTENSIONS = Set.of("csv", "txt", "prn", "dat");
    static final Set<String> ENCODINGS = Set.of("UTF-8", "ISO-8859-1", "windows-1252");

    public PayoutService(PayoutStore store, SellerLedgerStore ledger, ActorProvider actors, Clock marketplaceClock, int holdDays) {
        this(store, ledger, actors, marketplaceClock, holdDays, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PayoutService(PayoutStore store, SellerLedgerStore ledger, ActorProvider actors, Clock marketplaceClock,
                         @Value("${essenza.marketplace.payouts.hold-days:7}") int holdDays, PayoutDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        this.store = store;
        this.ledger = ledger;
        this.actors = actors;
        this.clock = marketplaceClock;
        this.holdDays = Math.max(0, Math.min(holdDays, 90));
    }

    /** Hasta qué día (inclusive) se liberan las órdenes entregadas. */
    LocalDate releasedThrough() {
        return LocalDate.now(clock).minusDays(holdDays + 1L);
    }

    @Transactional(readOnly = true)
    public PreviewDto preview() {
        RequestActor actor = view();
        boolean full = actor.has(MarketplacePermissions.BILLING_MANAGE);
        List<PayeeDto> payees = payees(actor.companyId(), full);
        BigDecimal owed = payees.stream().map(PayeeDto::owed).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal payable = payees.stream().map(PayeeDto::payable).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PreviewDto(holdDays, releasedThrough(), owed, payable, payees);
    }

    private List<PayeeDto> payees(long companyId, boolean full) {
        Map<Long, BigDecimal> balances = ledger.balances(companyId);
        Map<Long, BigDecimal> held = store.held(companyId, releasedThrough().plusDays(1).atStartOfDay());
        Map<Long, Stored> accounts = store.bankAccounts(companyId);
        Map<Long, PayoutStore.PendingRef> pending = store.pendingBySeller(companyId);
        List<PayeeDto> result = new ArrayList<>();
        for (SellerRef seller : store.sellers(companyId).values()) {
            BigDecimal owed = PayoutRules.owed(balances.get(seller.id()));
            if (owed.signum() <= 0) {
                continue;
            }
            BigDecimal retained = held.getOrDefault(seller.id(), BigDecimal.ZERO).max(BigDecimal.ZERO).min(owed);
            PayoutStore.PendingRef open = pending.get(seller.id());
            result.add(new PayeeDto(seller.id(), seller.name(), seller.doc(), owed, retained,
                    PayoutRules.payable(balances.get(seller.id()), retained), dto(accounts.get(seller.id()), full),
                    open == null ? null : open.batchId(), open == null ? null : open.number()));
        }
        return result;
    }

    public BatchDetailDto create(CreateBatchRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_MANAGE);
        MarketplaceRuleViolation.check(request != null && request.items() != null && !request.items().isEmpty(),
                "Elige al menos un vendedor para el lote.");
        MarketplaceRuleViolation.check(request.items().size() <= MAX_ITEMS, "Un lote admite máximo " + MAX_ITEMS + " vendedores.");
        Map<Long, PayeeDto> payable = new java.util.HashMap<>();
        payees(actor.companyId(), true).forEach(p -> payable.put(p.sellerId(), p));
        Map<Long, SellerRef> sellers = store.sellers(actor.companyId());
        Map<Long, Stored> accounts = store.bankAccounts(actor.companyId());
        Set<Long> seen = new HashSet<>();
        record Line(SellerRef seller, BigDecimal amount, BankAccount account) {
        }
        List<Line> lines = new ArrayList<>();
        for (CreateItem item : request.items()) {
            MarketplaceRuleViolation.check(item != null && item.sellerId() != null, "Cada pago debe indicar el vendedor.");
            MarketplaceRuleViolation.check(seen.add(item.sellerId()), "El vendedor " + item.sellerId() + " está repetido.");
            SellerRef seller = sellers.get(item.sellerId());
            if (seller == null) {
                throw new NotFoundException("No se encontró el vendedor " + item.sellerId() + ".");
            }
            PayeeDto payee = payable.get(seller.id());
            if (payee != null && payee.pendingBatchId() != null) {
                throw new ConflictException(seller.name() + " ya tiene un pago pendiente en el lote " + payee.pendingBatchNumber() + ".");
            }
            BigDecimal amount = PayoutRules.itemAmount(item.amount(), payee == null ? BigDecimal.ZERO : payee.payable(), seller.name());
            Stored account = accounts.get(seller.id());
            MarketplaceRuleViolation.check(account != null, seller.name() + " no tiene cuenta bancaria registrada.");
            lines.add(new Line(seller, amount, account.account()));
        }
        Instant now = clock.instant();
        long id = store.insertBatch(actor.companyId(), holdDays, clip(request.note(), 500), actor.userId(), actor.displayName(), now);
        store.setBatchNumber(id, PayoutRules.number(id));
        for (Line line : lines) {
            try {
                store.insertItem(id, line.seller(), line.amount(), "COP", line.account(), now);
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                throw new ConflictException(line.seller().name() + " ya tiene un pago pendiente en otro lote.");
            }
        }
        return detail(actor, id);
    }

    /** Marca un pago como hecho: asienta la liquidación (no más de lo que la tienda le debe hoy). */
    public BatchDetailDto pay(long batchId, long itemId, PayRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_MANAGE);
        BatchRow batch = openBatch(actor, batchId);
        ItemDto item = store.lockItem(batchId, itemId).orElseThrow(() -> new NotFoundException("Pago no encontrado: " + itemId));
        settle(actor, batch, item, request);
        refresh(batchId, null);
        return detail(actor, batchId);
    }

    /** Todos los pagos pendientes del lote (todo o nada). */
    public BatchDetailDto payAll(long batchId, PayRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_MANAGE);
        BatchRow batch = openBatch(actor, batchId);
        List<ItemDto> pending = store.items(batchId).stream().filter(i -> ItemStatus.PENDING.name().equals(i.status()))
                .sorted(java.util.Comparator.comparingLong(ItemDto::id)).toList();
        MarketplaceRuleViolation.check(!pending.isEmpty(), "El lote no tiene pagos pendientes.");
        for (ItemDto item : pending) {
            settle(actor, batch, store.lockItem(batchId, item.id()).orElseThrow(), request);
        }
        refresh(batchId, null);
        return detail(actor, batchId);
    }

    /** El banco rechazó la transferencia (o se decide no pagar): no se asienta nada y el vendedor vuelve a quedar libre. */
    public BatchDetailDto reject(long batchId, long itemId, ReasonRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_MANAGE);
        openBatch(actor, batchId);
        String reason = reason(request);
        ItemDto item = store.lockItem(batchId, itemId).orElseThrow(() -> new NotFoundException("Pago no encontrado: " + itemId));
        MarketplaceRuleViolation.check(ItemStatus.PENDING.name().equals(item.status()), "Ese pago ya no está pendiente.");
        store.settleItem(itemId, ItemStatus.REJECTED.name(), null, null, reason, actor.userId(), actor.displayName(), clock.instant());
        refresh(batchId, null);
        return detail(actor, batchId);
    }

    /** Anula el lote si aún no se pagó nada. */
    public BatchDetailDto cancel(long batchId, ReasonRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_MANAGE);
        BatchRow batch = openBatch(actor, batchId);
        String reason = reason(request);
        MarketplaceRuleViolation.check(batch.summary().paid() == 0, "El lote ya tiene pagos hechos: rechaza los pendientes uno a uno.");
        Instant now = clock.instant();
        for (ItemDto item : store.items(batchId)) {
            if (ItemStatus.PENDING.name().equals(item.status())) {
                store.settleItem(item.id(), ItemStatus.REJECTED.name(), null, null, reason, actor.userId(), actor.displayName(), now);
            }
        }
        store.setBatchStatus(batchId, BatchStatus.CANCELLED.name(), reason, now);
        return detail(actor, batchId);
    }

    @Transactional(readOnly = true)
    public BatchDetailDto get(long batchId) {
        return detail(view(), batchId);
    }

    @Transactional(readOnly = true)
    public Page<BatchSummaryDto> search(String status, Pageable pageable) {
        RequestActor actor = view();
        String s = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (s != null) {
            try {
                BatchStatus.valueOf(s);
            } catch (IllegalArgumentException ex) {
                throw new MarketplaceRuleViolation("Estado no válido: " + status + ".");
            }
        }
        return store.search(actor.companyId(), s, pageable);
    }

    /** CSV genérico (números completos: solo COMISIONES.GESTIONAR). */
    @Transactional(readOnly = true)
    public String exportCsv(long batchId) {
        return new String(export(batchId, null, null).content(), java.nio.charset.StandardCharsets.UTF_8).replace("\uFEFF", "");
    }

    /**
     * Archivo para cargar en el banco pagador con el formato elegido (vacío o 0 = genérico CSV). Sin los
     * pagos rechazados. {@code paymentDate}: fecha de aplicación (vacía = hoy). Solo COMISIONES.GESTIONAR.
     */
    @Transactional(readOnly = true)
    public ExportFile export(long batchId, Long formatId, LocalDate paymentDate) {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_MANAGE);
        BatchRow batch = store.batch(actor.companyId(), batchId, false).orElseThrow(() -> notFound(batchId));
        FormatDto format = formatId == null || formatId == 0 ? genericFormat()
                : store.format(formatId).map(PayoutService::formatDto)
                        .orElseThrow(() -> new NotFoundException("No se encontró el formato " + formatId + "."));
        PayoutFileLayout.Layout layout = toLayout(format.layout());
        List<PayoutFileLayout.Payment> payments = store.items(batchId).stream()
                .filter(i -> !ItemStatus.REJECTED.name().equals(i.status()))
                .sorted(java.util.Comparator.comparingLong(ItemDto::id))
                .map(i -> new PayoutFileLayout.Payment(i.sellerName(), i.sellerDoc(), i.holderName(), i.holderDocType(), i.holderDocNumber(),
                        i.bankName(), i.bankCode(), i.accountType(), i.keyType(), i.accountNumber(), i.amount(), i.currency()))
                .toList();
        MarketplaceRuleViolation.check(!payments.isEmpty(), "El lote no tiene pagos para el archivo.");
        String text = PayoutFileLayout.render(layout, batch.summary().number(), paymentDate == null ? LocalDate.now(clock) : paymentDate,
                payments);
        boolean bom = "UTF-8".equals(format.encoding()) && "csv".equals(format.extension());
        byte[] content = ((bom ? "\uFEFF" : "") + text).getBytes(java.nio.charset.Charset.forName(format.encoding()));
        String fileName = batch.summary().number() + (format.builtIn() ? "" : "-" + PayoutService.codeOf(format.name()).toLowerCase(Locale.ROOT))
                + "." + format.extension();
        return new ExportFile(fileName, format.encoding(), format.extension(), content);
    }

    @Transactional(readOnly = true)
    public BankAccountDto bankAccount(long sellerId) {
        RequestActor actor = view();
        requireSeller(actor, sellerId);
        return store.bankAccount(actor.companyId(), sellerId)
                .map(stored -> dto(stored, actor.has(MarketplacePermissions.BILLING_MANAGE))).orElse(null);
    }

    public BankAccountDto saveBankAccount(long sellerId, SaveBankAccountRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_MANAGE);
        requireSeller(actor, sellerId);
        MarketplaceRuleViolation.check(request != null, "Faltan los datos de la cuenta.");
        PayoutRules.BankRef bank = request.bankId() == null ? null
                : store.bank(request.bankId()).orElseThrow(() -> new NotFoundException("No se encontró la entidad " + request.bankId() + "."));
        BankAccount account = PayoutRules.checkBankAccount(bank, request.accountType(), request.accountNumber(), request.keyType(),
                request.holderName(), request.holderDocType(), request.holderDocNumber());
        store.saveBankAccount(actor.companyId(), sellerId, account, actor.userId(), actor.displayName(), clock.instant());
        return store.bankAccount(actor.companyId(), sellerId).map(stored -> dto(stored, true)).orElseThrow();
    }

    // ── Formatos del archivo de pagos ──────────────────────────────────────────

    /** Genérico + los configurados (activos; con {@code includeInactive} también los desactivados, solo GESTIONAR). */
    @Transactional(readOnly = true)
    public List<FormatDto> formats(boolean includeInactive) {
        RequestActor actor = view();
        List<FormatDto> result = new ArrayList<>();
        result.add(genericFormat());
        store.formats(includeInactive && actor.has(MarketplacePermissions.BILLING_MANAGE)).forEach(f -> result.add(formatDto(f)));
        return result;
    }

    public FormatDto saveFormat(Long id, SaveFormatRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_MANAGE);
        MarketplaceRuleViolation.check(request != null, "Faltan los datos del formato.");
        MarketplaceRuleViolation.check(id == null || id != 0, "El formato genérico no se edita: crea uno nuevo.");
        String name = blank(request.name());
        MarketplaceRuleViolation.check(name != null && name.length() <= 80, "Indica el nombre del formato (máximo 80 caracteres).");
        String ext = request.extension() == null ? "txt" : request.extension().trim().toLowerCase(Locale.ROOT);
        MarketplaceRuleViolation.check(EXTENSIONS.contains(ext), "Extensión no válida: csv, txt, prn o dat.");
        String enc = request.encoding() == null ? "UTF-8" : request.encoding().trim();
        MarketplaceRuleViolation.check(ENCODINGS.contains(enc), "Codificación no válida: UTF-8, ISO-8859-1 o windows-1252.");
        PayoutFileLayout.Layout layout = toLayout(request.layout());
        PayoutFileLayout.check(layout);
        if (store.formatNameExists(name, id)) {
            throw new ConflictException("Ya existe un formato llamado " + name + ".");
        }
        String json = json(request.layout());
        Instant now = clock.instant();
        long saved;
        if (id == null) {
            saved = store.insertFormat(name, ext, enc, json, actor.userId(), actor.displayName(), now);
        } else {
            PayoutStore.FormatRow current = store.format(id).orElseThrow(() -> new NotFoundException("No se encontró el formato " + id + "."));
            store.updateFormat(id, name, ext, enc, json, request.active() == null ? current.active() : request.active(), actor.userId(),
                    actor.displayName(), now);
            saved = id;
        }
        return store.format(saved).map(PayoutService::formatDto).orElseThrow();
    }

    /** Muestra del archivo con dos pagos de ejemplo (para configurar el formato sin un lote real). */
    @Transactional(readOnly = true)
    public FormatPreviewDto previewFormat(LayoutDto dto) {
        actors.current().require(MarketplacePermissions.BILLING_MANAGE);
        PayoutFileLayout.Layout layout = toLayout(dto);
        PayoutFileLayout.check(layout);
        List<PayoutFileLayout.Payment> sample = List.of(
                new PayoutFileLayout.Payment("Moda Andina SAS", "NIT 900123456", "Moda Andina SAS", "NIT", "900123456", "Bancolombia",
                        "1007", "SAVINGS", null, "12345678901", new BigDecimal("1250000.50"), "COP"),
                new PayoutFileLayout.Payment("Ana Pérez", "CC 1020304050", "Ana Pérez", "CC", "1020304050", "Bre-B", null, "BRE_B_KEY",
                        "PHONE", "3001234567", new BigDecimal("89900"), "COP"));
        return new FormatPreviewDto(PayoutFileLayout.render(layout, "LIQ-000123", LocalDate.now(clock), sample));
    }

    static FormatDto genericFormat() {
        return new FormatDto(0L, "Genérico (CSV)", "csv", "UTF-8", true, true, layoutDto(PayoutFileLayout.generic()), null, null);
    }

    private static FormatDto formatDto(PayoutStore.FormatRow f) {
        LayoutDto layout;
        try {
            layout = JSON.readValue(f.layoutJson(), LayoutDto.class);
        } catch (Exception ex) {
            throw new IllegalStateException("El formato " + f.id() + " tiene un diseño ilegible.", ex);
        }
        return new FormatDto(f.id(), f.name(), f.extension(), f.encoding(), f.active(), false, layout, f.updatedByName(), f.updatedAt());
    }

    private static String json(LayoutDto layout) {
        try {
            return JSON.writeValueAsString(layout);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo guardar el diseño del formato.", ex);
        }
    }

    static PayoutFileLayout.Layout toLayout(LayoutDto d) {
        MarketplaceRuleViolation.check(d != null, "Falta el diseño del archivo.");
        PayoutFileLayout.FileType type = parse(PayoutFileLayout.FileType.class, d.type(), "El tipo de archivo");
        String delimiter = d.delimiter() == null ? "," : "TAB".equalsIgnoreCase(d.delimiter()) || "\\t".equals(d.delimiter()) ? "\t" : d.delimiter();
        String lineEnding = "LF".equalsIgnoreCase(d.lineEnding()) ? "\n" : "\r\n";
        return new PayoutFileLayout.Layout(type, delimiter, Boolean.TRUE.equals(d.titleRow()), lineEnding,
                d.amountDecimals() == null ? 2 : d.amountDecimals(), d.amountSeparator() == null ? "." : d.amountSeparator(),
                blank(d.datePattern()) == null ? "yyyyMMdd" : d.datePattern().trim(), Boolean.TRUE.equals(d.asciiOnly()),
                codes(d.accountTypeCodes()), codes(d.keyTypeCodes()), codes(d.docTypeCodes()), columns(d.header()), columns(d.detail()),
                columns(d.footer()));
    }

    private static java.util.Map<String, String> codes(java.util.Map<String, String> map) {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        if (map != null) {
            map.forEach((k, v) -> {
                if (k != null && v != null && !k.isBlank()) {
                    MarketplaceRuleViolation.check(v.length() <= 20, "Los códigos tienen máximo 20 caracteres.");
                    result.put(k.trim().toUpperCase(Locale.ROOT), v.trim());
                }
            });
        }
        return result;
    }

    private static List<PayoutFileLayout.Column> columns(List<ColumnDto> list) {
        if (list == null) {
            return List.of();
        }
        MarketplaceRuleViolation.check(list.size() <= 60, "Máximo 60 columnas por registro.");
        return list.stream().map(c -> {
            MarketplaceRuleViolation.check(c != null, "Hay una columna vacía.");
            return new PayoutFileLayout.Column(parse(PayoutFileLayout.Field.class, c.field(), "El dato de la columna"),
                    c.title() == null ? null : c.title().trim(), c.width() == null ? 0 : c.width(),
                    c.align() == null ? PayoutFileLayout.Align.LEFT : parse(PayoutFileLayout.Align.class, c.align(), "La alineación"),
                    c.pad() == null || c.pad().isEmpty() ? ' ' : c.pad().charAt(0), c.value());
        }).toList();
    }

    private static LayoutDto layoutDto(PayoutFileLayout.Layout l) {
        java.util.function.Function<List<PayoutFileLayout.Column>, List<ColumnDto>> cols = list -> list.stream()
                .map(c -> new ColumnDto(c.field().name(), c.title(), c.width(), c.align().name(), String.valueOf(c.pad()), c.value())).toList();
        return new LayoutDto(l.type().name(), "\t".equals(l.delimiter()) ? "TAB" : l.delimiter(), l.titleRow(),
                "\n".equals(l.lineEnding()) ? "LF" : "CRLF", l.amountDecimals(), l.amountSeparator(), l.datePattern(), l.asciiOnly(),
                l.accountTypeCodes(), l.keyTypeCodes(), l.docTypeCodes(), cols.apply(l.header()), cols.apply(l.detail()),
                cols.apply(l.footer()));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new MarketplaceRuleViolation(label + " no es válido: " + value + ".");
        }
    }

    // ── Catálogo de entidades ──────────────────────────────────────────────────

    /** Activas (para elegir); con {@code includeInactive} también las desactivadas (para administrarlas). */
    @Transactional(readOnly = true)
    public List<BankDto> banks(boolean includeInactive) {
        RequestActor actor = view();
        boolean all = includeInactive && actor.has(MarketplacePermissions.BILLING_MANAGE);
        return store.banks(all).stream().map(PayoutService::bankDto).toList();
    }

    /** Agrega una entidad (cualquier banco, banco digital, billetera o cooperativa que falte en el catálogo). */
    public BankDto createBank(SaveBankRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_MANAGE);
        MarketplaceRuleViolation.check(request != null, "Faltan los datos de la entidad.");
        String kind = upper(request.kind());
        String ach = blank(request.achCode());
        PayoutRules.checkBank(request.name(), kind, ach);
        String name = request.name().trim();
        String code = blank(request.code()) != null ? request.code().trim().toUpperCase(Locale.ROOT) : codeOf(name);
        MarketplaceRuleViolation.check(code.matches("[A-Z0-9_]{2,40}"), "El código interno solo lleva letras, números y _ (2 a 40).");
        if (store.bankCodeExists(code)) {
            throw new ConflictException("Ya existe una entidad con el código " + code + ".");
        }
        long id = store.insertBank(code, name, kind, ach, request.position() == null ? 100 : request.position(), actor.userId(),
                clock.instant());
        if (request.wompiBankId() != null) {
            store.setProviderBankId(id, providerBankId(request.wompiBankId()));
        }
        return store.banks(true).stream().filter(b -> b.id() == id).findFirst().map(PayoutService::bankDto).orElseThrow();
    }

    public BankDto updateBank(long id, SaveBankRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_MANAGE);
        MarketplaceRuleViolation.check(request != null, "Faltan los datos de la entidad.");
        PayoutStore.BankRow current = store.banks(true).stream().filter(b -> b.id() == id).findFirst()
                .orElseThrow(() -> new NotFoundException("No se encontró la entidad " + id + "."));
        String name = blank(request.name()) == null ? current.name() : request.name().trim();
        String kind = request.kind() == null ? current.kind() : upper(request.kind());
        String ach = request.achCode() == null ? current.achCode() : blank(request.achCode());
        PayoutRules.checkBank(name, kind, ach);
        store.updateBank(id, name, kind, ach, request.active() == null ? current.active() : request.active(),
                request.position() == null ? current.position() : request.position(), actor.userId(), clock.instant());
        if (request.wompiBankId() != null) {
            store.setProviderBankId(id, providerBankId(request.wompiBankId()));
        }
        return store.banks(true).stream().filter(b -> b.id() == id).findFirst().map(PayoutService::bankDto).orElseThrow();
    }

    /** Vacío = quitarlo. */
    static String providerBankId(String value) {
        String v = blank(value);
        MarketplaceRuleViolation.check(v == null || v.matches("[A-Za-z0-9-]{1,40}"),
                "El id de la entidad en el proveedor solo lleva letras, números y guiones (máximo 40).");
        return v;
    }

    private static BankDto bankDto(PayoutStore.BankRow b) {
        return new BankDto(b.id(), b.code(), b.name(), b.kind(), b.achCode(), b.active(), b.position(),
                PayoutRules.accountTypesFor(b.kind()).stream().sorted().toList(), b.wompiBankId());
    }

    static String codeOf(String name) {
        String plain = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String code = plain.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        return code.length() > 40 ? code.substring(0, 40) : code;
    }

    private static String upper(String value) {
        return blank(value) == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ── Soporte ─────────────────────────────────────────────────────────────

    private void settle(RequestActor actor, BatchRow batch, ItemDto item, PayRequest request) {
        MarketplaceRuleViolation.check(ItemStatus.PENDING.name().equals(item.status()),
                "El pago a " + item.sellerName() + " ya no está pendiente.");
        String reference = clip(request == null ? null : request.reference(), 60);
        MarketplaceRuleViolation.check(reference != null, "Indica la referencia de la transferencia.");
        LocalDate today = LocalDate.now(clock);
        LocalDate paidOn = request.paidOn() == null ? today : request.paidOn();
        MarketplaceRuleViolation.check(!paidOn.isAfter(today), "La fecha del pago no puede ser futura.");
        // El saldo pudo cambiar desde que se armó el lote (cancelaciones, reembolsos): nunca se liquida más de lo debido.
        BigDecimal owed = PayoutRules.owed(ledger.balances(actor.companyId()).get(item.sellerId()));
        if (item.amount().compareTo(owed) > 0) {
            throw new ConflictException("A " + item.sellerName() + " hoy se le deben " + owed.toPlainString()
                    + ", menos que el pago del lote (" + item.amount().toPlainString() + "). Recházalo y arma uno nuevo.");
        }
        Instant now = clock.instant();
        String ledgerReference = clip(batch.summary().number() + " · " + reference, 80);
        boolean posted = ledger.post(actor.companyId(), new SellerLedgerStore.NewEntry(item.sellerId(), "SETTLEMENT", item.amount(),
                item.currency(), paidOn, null, BigDecimal.ZERO, ledgerReference, null, null, "PAYOUT:" + item.id(),
                clip(request.note(), 500)), actor.userId(), actor.displayName(), now);
        if (!posted || !store.settleItem(item.id(), ItemStatus.PAID.name(), reference, paidOn, clip(request.note(), 500), actor.userId(),
                actor.displayName(), now)) {
            throw new ConflictException("El pago a " + item.sellerName() + " ya estaba registrado.");
        }
    }

    private void refresh(long batchId, String reason) {
        List<ItemDto> items = store.items(batchId);
        int pending = (int) items.stream().filter(i -> ItemStatus.PENDING.name().equals(i.status())).count();
        int paid = (int) items.stream().filter(i -> ItemStatus.PAID.name().equals(i.status())).count();
        BatchStatus status = PayoutRules.statusOf(pending, paid);
        store.setBatchStatus(batchId, status.name(), reason, status == BatchStatus.OPEN ? null : clock.instant());
    }

    private BatchRow openBatch(RequestActor actor, long batchId) {
        BatchRow batch = store.batch(actor.companyId(), batchId, true).orElseThrow(() -> notFound(batchId));
        MarketplaceRuleViolation.check(BatchStatus.OPEN.name().equals(batch.summary().status()),
                "El lote " + batch.summary().number() + " ya está cerrado o anulado.");
        if (batch.dispatch() != null && "SENT".equals(batch.dispatch().status())) {
            throw new ConflictException("El lote " + batch.summary().number()
                    + " está en dispersión automática: el resultado de cada pago llega del proveedor (usa Sincronizar).");
        }
        return batch;
    }

    private BatchDetailDto detail(RequestActor actor, long batchId) {
        BatchRow batch = store.batch(actor.companyId(), batchId, false).orElseThrow(() -> notFound(batchId));
        boolean manage = actor.has(MarketplacePermissions.BILLING_MANAGE);
        List<ItemDto> items = store.items(batchId).stream().map(i -> manage ? i : masked(i)).toList();
        List<String> actions = new ArrayList<>();
        PayoutStore.DispatchState state = batch.dispatch();
        String dispatchStatus = state == null ? null : state.status();
        boolean open = BatchStatus.OPEN.name().equals(batch.summary().status());
        boolean inProvider = "SENT".equals(dispatchStatus);
        List<String> problems = List.of();
        if (manage) {
            if (open && !inProvider) {
                actions.add("PAY");
                actions.add("REJECT");
                if (batch.summary().paid() == 0) {
                    actions.add("CANCEL");
                }
            }
            actions.add("EXPORT");
            if (dispatcher != null && open && batch.summary().pending() > 0 && !inProvider) {
                problems = dispatchProblems(batchId);
                if (dispatcher.configured() && problems.isEmpty()) {
                    actions.add("DISPATCH");
                }
            }
            if (dispatcher != null && inProvider) {
                actions.add("SYNC");
            }
        }
        DispatchDto dispatch = dispatcher == null ? null : new DispatchDto(dispatcher.provider(), dispatcher.environment(),
                dispatcher.configured(), dispatchStatus, state == null ? null : state.providerBatchId(),
                state == null ? null : state.detail(), state == null ? null : state.dispatchedAt(), problems);
        return new BatchDetailDto(batch.summary(), batch.holdDays(), batch.note(), batch.statusReason(), items, actions, dispatch);
    }

    /** Qué le falta a cada pago pendiente del lote para la dispersión automática. */
    @Transactional(readOnly = true)
    public List<String> dispatchProblems(long batchId) {
        List<String> result = new ArrayList<>();
        Map<Long, String> names = new java.util.HashMap<>();
        store.items(batchId).forEach(i -> names.put(i.id(), i.sellerName()));
        for (PayoutStore.DispatchRow row : store.dispatchRows(batchId)) {
            if (!ItemStatus.PENDING.name().equals(row.status())) {
                continue;
            }
            List<String> missing = PayoutDispatchRules.problems(row.accountType(), row.accountNumber(), row.providerBankId(), row.email(),
                    row.holderName(), row.holderDocType());
            if (!missing.isEmpty()) {
                result.add(names.getOrDefault(row.itemId(), "#" + row.itemId()) + ": " + String.join(", ", missing));
            }
        }
        return result;
    }

    private static ItemDto masked(ItemDto i) {
        return new ItemDto(i.id(), i.sellerId(), i.sellerName(), i.sellerDoc(), i.amount(), i.currency(), i.bankName(), i.bankCode(),
                i.accountType(), i.keyType(), PayoutRules.mask(i.accountNumber()), i.holderName(), i.holderDocType(), i.holderDocNumber(), i.status(), i.reference(),
                i.paidOn(), i.note(), i.actorName(), i.updatedAt(), i.providerStatus(), i.providerDetail());
    }

    private static BankAccountDto dto(Stored stored, boolean full) {
        if (stored == null) {
            return null;
        }
        BankAccount a = stored.account();
        return new BankAccountDto(a.bankId(), a.bankCode(), a.bankName(), a.accountType(), a.keyType(),
                full ? a.accountNumber() : PayoutRules.mask(a.accountNumber()), a.holderName(), a.holderDocType(), a.holderDocNumber(),
                !full, stored.updatedByName(), stored.updatedAt());
    }

    private void requireSeller(RequestActor actor, long sellerId) {
        if (!store.sellers(actor.companyId()).containsKey(sellerId)) {
            throw new NotFoundException("No se encontró el vendedor externo " + sellerId + ".");
        }
    }

    private RequestActor view() {
        return actors.current().requireAny(MarketplacePermissions.BILLING_VIEW, MarketplacePermissions.BILLING_MANAGE);
    }

    private static String reason(ReasonRequest request) {
        String reason = clip(request == null ? null : request.reason(), 500);
        MarketplaceRuleViolation.check(reason != null && reason.length() >= 5, "Explica el motivo (mínimo 5 caracteres).");
        return reason;
    }

    private static NotFoundException notFound(long id) {
        return new NotFoundException("Lote de pagos no encontrado: " + id);
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        return v.length() <= max ? v : v.substring(0, max);
    }
}
