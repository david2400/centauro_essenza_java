package com.essenza.draco.modules.marketplace.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.marketplace.application.MarketplacePermissions;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.CancelRequest;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.DisputeDetailDto;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.DisputeStatsDto;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.DisputeSummaryDto;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.OpenDisputeRequest;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.OrderDisputeDto;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.ResolveRequest;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.RespondRequest;
import com.essenza.draco.modules.marketplace.application.port.DisputeStore;
import com.essenza.draco.modules.marketplace.application.port.DisputeStore.DisputeRow;
import com.essenza.draco.modules.marketplace.application.port.DisputeStore.OrderRef;
import com.essenza.draco.modules.marketplace.application.port.SellerLedgerStore;
import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.domain.dispute.DisputeWorkflow;
import com.essenza.draco.modules.marketplace.domain.dispute.DisputeWorkflow.Reason;
import com.essenza.draco.modules.marketplace.domain.dispute.DisputeWorkflow.Resolution;
import com.essenza.draco.modules.marketplace.domain.dispute.DisputeWorkflow.Status;
import com.essenza.draco.modules.marketplace.domain.plan.Money;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Reclamos del comprador contra subpedidos de vendedores externos. Los registra la tienda (el
 * vendedor aún no tiene portal): apertura con plazo de respuesta, respuesta del vendedor y
 * decisión. A favor del comprador puede haber reembolso o cambio (se tramitan con postventa y
 * pagos) y un cargo al vendedor en su estado de cuenta. Empresa y permisos salen del token
 * (DISPUTAS.VER / GESTIONAR / RESOLVER).
 */
@Service
@Transactional
public class DisputeService {

    private final DisputeStore store;
    private final SellerLedgerStore ledger;
    private final ActorProvider actors;
    private final Clock clock;
    private final int responseDays;
    private final SellerProceedsService proceeds;

    /** Para pruebas sin reversa de lo cobrado por la tienda. */
    public DisputeService(DisputeStore store, SellerLedgerStore ledger, ActorProvider actors, Clock marketplaceClock,
                          int responseDays) {
        this(store, ledger, actors, marketplaceClock, responseDays, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DisputeService(DisputeStore store, SellerLedgerStore ledger, ActorProvider actors, Clock marketplaceClock,
                          @Value("${essenza.marketplace.disputes.response-days:3}") int responseDays, SellerProceedsService proceeds) {
        this.store = store;
        this.ledger = ledger;
        this.actors = actors;
        this.clock = marketplaceClock;
        this.responseDays = responseDays;
        this.proceeds = proceeds;
    }

    public DisputeDetailDto open(OpenDisputeRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.DISPUTES_MANAGE);
        MarketplaceRuleViolation.check(request != null && request.orderId() != null, "Indica la orden reclamada.");
        OrderRef order = store.order(actor.companyId(), request.orderId())
                .orElseThrow(() -> new NotFoundException("No se encontró la orden " + request.orderId() + " de un vendedor externo."));
        Reason reason = DisputeWorkflow.parse(Reason.class, request.reason(), "El motivo");
        DisputeWorkflow.checkCanOpen("EXTERNAL".equals(order.sellerKind()), order.state(), store.hasOpen(order.id()),
                request.description());
        BigDecimal claimed = request.claimedAmount() == null ? null : Money.amount(request.claimedAmount(), "COP", "El valor reclamado");
        MarketplaceRuleViolation.check(claimed == null || claimed.compareTo(order.total()) <= 0,
                "El valor reclamado no puede superar el total de la orden.");
        Instant now = clock.instant();
        LocalDate respondBy = DisputeWorkflow.respondBy(LocalDate.now(clock), responseDays);
        long id;
        try {
            id = store.insert(actor.companyId(), order, reason.name(), clip(request.description(), 1000), claimed, respondBy,
                    actor.userId(), actor.displayName(), now);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new ConflictException("Esta orden ya tiene un reclamo abierto.");
        }
        store.setNumber(id, DisputeWorkflow.number(id));
        store.addEvent(id, "OPEN", null, Status.OPEN.name(), reason.name(), actor.userId(), actor.displayName(), now);
        return detail(actor, id);
    }

    public DisputeDetailDto respond(long id, RespondRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.DISPUTES_MANAGE);
        DisputeRow row = load(actor, id, true);
        Status status = Status.valueOf(row.summary().status());
        DisputeWorkflow.checkRespond(status, request == null ? null : request.response());
        Instant now = clock.instant();
        if (!store.respond(id, lock(row, request.lockVersion()), clip(request.response(), 1000), now)) {
            throw stale();
        }
        store.addEvent(id, "RESPOND", status.name(), Status.SELLER_RESPONDED.name(), clip(request.response(), 1000), actor.userId(),
                actor.displayName(), now);
        return detail(actor, id);
    }

    public DisputeDetailDto resolve(long id, ResolveRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.DISPUTES_RESOLVE);
        MarketplaceRuleViolation.check(request != null, "Faltan los datos de la decisión.");
        DisputeRow row = load(actor, id, true);
        Status status = Status.valueOf(row.summary().status());
        Resolution resolution = DisputeWorkflow.parse(Resolution.class, request.resolution(), "La decisión");
        BigDecimal amount = request.amount() == null ? null : Money.amount(request.amount(), "COP", "El reembolso");
        BigDecimal charge = request.sellerCharge() == null ? null : Money.amount(request.sellerCharge(), "COP", "El cargo al vendedor");
        Status next = DisputeWorkflow.checkResolve(status, row.summary().respondBy(), LocalDate.now(clock), resolution, amount,
                charge, row.orderTotal(), request.note());
        if (request.devolutionId() != null) {
            MarketplaceRuleViolation.check(store.devolutionOfOrder(request.devolutionId(), row.summary().orderId()),
                    "La devolución " + request.devolutionId() + " no es de esta orden.");
        }
        Instant now = clock.instant();
        String note = clip(request.note(), 1000);
        if (!store.resolve(id, lock(row, request.lockVersion()), next.name(), resolution.name(), amount, charge, note,
                request.devolutionId(), actor.userId(), now)) {
            throw stale();
        }
        if (charge != null && charge.signum() > 0) {
            // Cargo al vendedor: suma a lo que le debe a la plataforma (vence en la fecha de la decisión).
            LocalDate today = LocalDate.now(clock);
            ledger.post(actor.companyId(), new SellerLedgerStore.NewEntry(row.summary().sellerId(), "DISPUTE_CHARGE", charge, "COP",
                    today, today, BigDecimal.ZERO, row.summary().number(), null, null, "DSP:" + id, note), actor.userId(),
                    actor.displayName(), now);
        }
        if (proceeds != null && resolution == Resolution.REFUND && amount != null && amount.signum() > 0) {
            // Si la tienda cobró la orden, el reembolso al comprador sale de lo que se le abonó al vendedor.
            proceeds.refund(actor.companyId(), row.summary().sellerId(), row.summary().orderId(), row.summary().number(),
                    "DSP-REFUND:" + id, amount, note, actor.userId(), actor.displayName());
        }
        store.addEvent(id, "RESOLVE", status.name(), next.name(), resolution.name() + " · " + note, actor.userId(),
                actor.displayName(), now);
        return detail(actor, id);
    }

    public DisputeDetailDto cancel(long id, CancelRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.DISPUTES_MANAGE);
        DisputeRow row = load(actor, id, true);
        Status status = Status.valueOf(row.summary().status());
        DisputeWorkflow.checkCancel(status, request == null ? null : request.reason());
        Instant now = clock.instant();
        if (!store.cancel(id, lock(row, request.lockVersion()), clip(request.reason(), 1000), actor.userId(), now)) {
            throw stale();
        }
        store.addEvent(id, "CANCEL", status.name(), Status.CANCELLED.name(), clip(request.reason(), 1000), actor.userId(),
                actor.displayName(), now);
        return detail(actor, id);
    }

    @Transactional(readOnly = true)
    public DisputeDetailDto get(long id) {
        return detail(view(), id);
    }

    @Transactional(readOnly = true)
    public Page<DisputeSummaryDto> search(String status, Long sellerId, String q, Pageable pageable) {
        RequestActor actor = view();
        String s = status == null || status.isBlank() ? null : status.trim().toUpperCase(java.util.Locale.ROOT);
        if (s != null && !"OVERDUE".equals(s) && !"ACTIVE".equals(s)) {
            s = DisputeWorkflow.parse(Status.class, s, "El estado").name();
        }
        return store.search(actor.companyId(), s, sellerId, q, LocalDate.now(clock), pageable);
    }

    @Transactional(readOnly = true)
    public DisputeStatsDto stats() {
        return store.stats(view().companyId(), LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public Optional<OrderDisputeDto> ofOrder(long orderId) {
        return store.latestForOrder(view().companyId(), orderId);
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────

    private RequestActor view() {
        return actors.current().requireAny(MarketplacePermissions.DISPUTES_VIEW, MarketplacePermissions.DISPUTES_MANAGE,
                MarketplacePermissions.DISPUTES_RESOLVE);
    }

    private DisputeRow load(RequestActor actor, long id, boolean forUpdate) {
        return store.find(actor.companyId(), id, forUpdate)
                .orElseThrow(() -> new NotFoundException("No se encontró el reclamo " + id + "."));
    }

    private DisputeDetailDto detail(RequestActor actor, long id) {
        DisputeRow r = load(actor, id, false);
        DisputeSummaryDto s = r.summary();
        Status status = Status.valueOf(s.status());
        LocalDate today = LocalDate.now(clock);
        DisputeSummaryDto summary = new DisputeSummaryDto(s.id(), s.number(), s.orderId(), s.orderNumber(), s.purchaseId(), s.sellerId(),
                s.sellerName(), s.customerName(), s.reason(), s.status(), s.claimedAmount(), s.respondBy(),
                DisputeWorkflow.overdue(status, s.respondBy(), today), s.resolution(), s.resolutionAmount(), s.createdAt());
        List<String> actions = new ArrayList<>();
        if (!status.closed() && actor.has(MarketplacePermissions.DISPUTES_MANAGE)) {
            if (status == Status.OPEN) {
                actions.add("RESPOND");
            }
            actions.add("CANCEL");
        }
        if (!status.closed() && actor.has(MarketplacePermissions.DISPUTES_RESOLVE)
                && (status == Status.SELLER_RESPONDED || today.isAfter(s.respondBy()))) {
            actions.add("RESOLVE");
        }
        return new DisputeDetailDto(summary, r.description(), r.sellerResponse(), r.respondedAt(), r.sellerCharge(), r.resolutionNote(),
                r.devolutionId(), r.resolvedAt(), r.resolvedBy(), r.orderTotal(), r.orderState(), r.lockVersion(), r.createdByName(),
                actions, store.events(id));
    }

    private static int lock(DisputeRow row, Integer requested) {
        MarketplaceRuleViolation.check(requested != null, "Falta lock_version.");
        if (requested != row.lockVersion()) {
            throw stale();
        }
        return requested;
    }

    private static ConflictException stale() {
        return new ConflictException("Otra persona modificó el reclamo. Recarga para ver los cambios.");
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        return text.length() > max ? text.substring(0, max) : text;
    }
}
