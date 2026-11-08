package com.essenza.draco.modules.marketplace.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.marketplace.application.MarketplacePermissions;
import com.essenza.draco.modules.marketplace.application.dto.BillingDtos.AccrualRunDto;
import com.essenza.draco.modules.marketplace.application.dto.BillingDtos.BillingSummaryDto;
import com.essenza.draco.modules.marketplace.application.dto.BillingDtos.CommissionDto;
import com.essenza.draco.modules.marketplace.application.dto.BillingDtos.StatusTotalDto;
import com.essenza.draco.modules.marketplace.application.port.AssignmentStore;
import com.essenza.draco.modules.marketplace.application.port.CommissionStore;
import com.essenza.draco.modules.marketplace.application.port.CommissionStore.CommissionFilter;
import com.essenza.draco.modules.marketplace.application.port.CommissionStore.CommissionRow;
import com.essenza.draco.modules.marketplace.application.port.CommissionStore.SellerRef;
import com.essenza.draco.modules.marketplace.application.port.CommissionStore.Totals;
import com.essenza.draco.modules.marketplace.application.port.SellerLedgerStore;
import com.essenza.draco.modules.marketplace.application.port.SellerLedgerStore.NewEntry;
import com.essenza.draco.modules.marketplace.application.service.AgreedTerms.InForce;
import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.domain.billing.CommissionRules;
import com.essenza.draco.modules.marketplace.domain.billing.CommissionRules.Status;
import com.essenza.draco.modules.marketplace.domain.plan.Money;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.AccrualEvent;
import com.essenza.draco.modules.marketplace.domain.plan.PlanTerms;
import com.essenza.draco.modules.marketplace.domain.plan.SaleAmounts;
import com.essenza.draco.modules.marketplace.domain.plan.SaleCharge;
import com.essenza.draco.shared.common.marketplace.SellerCommissions;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Comisión de cada venta de un vendedor externo con las condiciones acordadas en su vigencia
 * (plan + excepciones) el día del pago. Ventas avisa los cambios de estado dentro de su
 * transacción; un fallo al calcular no tumba la venta: la comisión queda en REVIEW.
 */
@Service
@Transactional
public class CommissionService implements SellerCommissions {

    static final String SYSTEM = "system";

    private final CommissionStore commissions;
    private final SellerLedgerStore ledger;
    private final AssignmentStore assignments;
    private final ActorProvider actors;
    private final Clock clock;

    public CommissionService(CommissionStore commissions, SellerLedgerStore ledger, AssignmentStore assignments,
                             ActorProvider actors, Clock marketplaceClock) {
        this.commissions = commissions;
        this.ledger = ledger;
        this.assignments = assignments;
        this.actors = actors;
        this.clock = marketplaceClock;
    }

    // ── Desde ventas ────────────────────────────────────────────────────────────

    @Override
    public void orderChanged(SaleChange change) {
        if (change == null || change.orderId() == null || change.sellerId() == null || change.status() == null) {
            return;
        }
        Optional<SellerRef> seller = commissions.seller(change.sellerId());
        if (seller.isEmpty() || !"EXTERNAL".equals(seller.get().kind())) {
            return;
        }
        Instant at = change.at() == null ? clock.instant() : change.at();
        switch (change.status()) {
            case "PAID" -> {
                if (commissions.byOrder(change.orderId(), true).isEmpty()) {
                    create(seller.get(), change, at);
                }
            }
            case "DELIVERED" -> commissions.byOrder(change.orderId(), true).ifPresent(c -> delivered(c, at));
            case "CANCELLED" -> commissions.byOrder(change.orderId(), true).ifPresent(c -> cancelled(c, at));
            default -> {
            }
        }
    }

    /**
     * Reembolso de una devolución: reversa según la política del plan. {@code refunded} incluye
     * impuestos (es lo que se le devolvió al comprador). Idempotente por devolución.
     */
    public void refunded(Long devolutionId, Long orderId, BigDecimal refunded) {
        if (devolutionId == null || orderId == null || refunded == null || refunded.signum() <= 0) {
            return;
        }
        Optional<CommissionRow> found = commissions.byOrder(orderId, true);
        if (found.isEmpty() || found.get().status() == Status.VOIDED || found.get().status() == Status.REVIEW) {
            return;
        }
        CommissionRow c = found.get();
        Optional<InForce> terms = AgreedTerms.byId(assignments, c.companyId(), c.sellerId(), c.assignmentId());
        if (terms.isEmpty()) {
            return;
        }
        BigDecimal buyerTotal = c.itemsSubtotal().subtract(c.discount()).add(c.shipping()).add(c.taxes());
        BigDecimal refundedBase = CommissionRules.refundedBase(refunded, buyerTotal, c.base(), c.currency());
        reverse(c, terms.get().terms(), refundedBase, "REFUND", "REFUND:" + devolutionId,
                "Devolución #" + devolutionId, clock.instant());
    }

    /** Igual que {@link #refunded}, en una transacción propia (tras el commit de la devolución). */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void refundedAfterCommit(Long devolutionId, Long orderId, BigDecimal refunded) {
        refunded(devolutionId, orderId, refunded);
    }

    // ── Devengo ─────────────────────────────────────────────────────────────────

    /** Comisiones pendientes cuya fecha de devengo ya llegó. */
    @Transactional(readOnly = true)
    public List<Long> dueIds() {
        return commissions.dueIds(LocalDate.now(clock), 500);
    }

    /** Devenga una comisión (cada una en su transacción cuando la llama el trabajo diario). */
    public boolean accrueOne(long commissionId) {
        Optional<CommissionRow> row = commissions.lockById(commissionId);
        if (row.isEmpty() || row.get().status() != Status.PENDING
                || !CommissionRules.isDue(row.get().accrueOn(), LocalDate.now(clock))) {
            return false;
        }
        accrue(row.get(), clock.instant());
        return true;
    }

    /** Devenga ya lo exigible de la empresa del usuario (exige permiso). */
    public AccrualRunDto accrueDueNow() {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_MANAGE);
        int accrued = 0;
        for (Long id : commissions.dueIds(LocalDate.now(clock), 500)) {
            Optional<CommissionRow> row = commissions.find(actor.companyId(), id, false);
            if (row.isPresent() && accrueOne(id)) {
                accrued++;
            }
        }
        return new AccrualRunDto(accrued);
    }

    /**
     * Recalcula una comisión en revisión (p. ej. después de asignar el plan que faltaba). Usa los
     * importes guardados de la venta y la vigencia en curso el día del pago. Si sigue sin poder
     * calcularse, queda en revisión con el motivo actualizado.
     */
    public CommissionDto recalculate(long commissionId) {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_MANAGE);
        CommissionRow c = commissions.find(actor.companyId(), commissionId, true)
                .orElseThrow(() -> new com.essenza.draco.shared.exceptions.NotFoundException(
                        "No se encontró la comisión " + commissionId + "."));
        MarketplaceRuleViolation.check(c.status() == Status.REVIEW, "Solo se recalculan comisiones en revisión.");
        Instant now = clock.instant();
        Optional<InForce> inForce = AgreedTerms.on(assignments, c.companyId(), c.sellerId(), c.paidOn());
        String reason = null;
        if (inForce.isEmpty()) {
            reason = "El vendedor no tenía un plan vigente el " + c.paidOn() + ".";
        } else if (!inForce.get().terms().currency().equalsIgnoreCase(c.currency())) {
            reason = "El plan cobra en " + inForce.get().terms().currency() + " y la venta fue en " + c.currency() + ".";
        }
        SaleCharge charge = null;
        if (reason == null) {
            try {
                charge = inForce.get().terms().chargeFor(SaleAmounts.of(c.currency(), c.itemsSubtotal(), c.discount(),
                        c.shipping(), c.taxes()));
            } catch (MarketplaceRuleViolation ex) {
                reason = ex.getMessage();
            }
        }
        if (reason != null) {
            commissions.recalculated(withCalculation(c, c.assignmentId(), c.planCode(), c.base(), c.percent(), c.percentPart(),
                    c.fixedPart(), c.amount(), c.accrualEvent(), c.returnWindowDays(), null, Status.REVIEW, clip(reason, 300)), now);
            return toDto(commissions.find(actor.companyId(), commissionId, false).orElseThrow());
        }
        PlanTerms terms = inForce.get().terms();
        LocalDate accrueOn = CommissionRules.accrueOn(terms.accrualEvent(), c.paidOn(), c.deliveredOn(), terms.returnWindowDays());
        CommissionRow next = withCalculation(c, inForce.get().assignment().id(), inForce.get().assignment().planCode(),
                charge.base(), terms.commissionPercent(), charge.percentPart(), charge.fixedPart(), charge.total(),
                terms.accrualEvent().name(), terms.returnWindowDays(), accrueOn, Status.PENDING, null);
        commissions.recalculated(next, now);
        if (CommissionRules.isDue(accrueOn, LocalDate.now(clock))) {
            accrue(commissions.find(actor.companyId(), commissionId, true).orElseThrow(), now);
        }
        return toDto(commissions.find(actor.companyId(), commissionId, false).orElseThrow());
    }

    private static CommissionRow withCalculation(CommissionRow c, Long assignmentId, String planCode, BigDecimal base,
                                                 BigDecimal percent, BigDecimal percentPart, BigDecimal fixedPart,
                                                 BigDecimal amount, String accrualEvent, int window, LocalDate accrueOn,
                                                 Status status, String reviewReason) {
        return new CommissionRow(c.id(), c.companyId(), c.sellerId(), c.sellerName(), c.orderId(), c.purchaseId(),
                c.orderNumber(), assignmentId, planCode, c.currency(), c.itemsSubtotal(), c.discount(), c.shipping(), c.taxes(),
                base, percent, percentPart, fixedPart, amount, c.reversedAmount(), accrualEvent, window, c.paidOn(),
                c.deliveredOn(), accrueOn, status, reviewReason, c.accruedAt(), c.voidedAt(), c.createdAt());
    }

    // ── Consultas ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CommissionDto> search(CommissionFilter filter, Pageable pageable) {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_VIEW);
        return commissions.search(actor.companyId(), filter, pageable).map(CommissionService::toDto);
    }

    @Transactional(readOnly = true)
    public BillingSummaryDto summary() {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_VIEW);
        Map<String, Totals> totals = commissions.totalsByStatus(actor.companyId(), null);
        List<StatusTotalDto> byStatus = new ArrayList<>();
        for (Status status : Status.values()) {
            Totals t = totals.getOrDefault(status.name(), new Totals(0, BigDecimal.ZERO));
            byStatus.add(new StatusTotalDto(status.name(), t.count(), t.amount()));
        }
        Map<Long, BigDecimal> balances = ledger.balances(actor.companyId());
        BigDecimal receivable = balances.values().stream().filter(b -> b.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
        long withBalance = balances.values().stream().filter(b -> b.signum() > 0).count();
        return new BillingSummaryDto(byStatus, receivable, withBalance);
    }

    // ── Reglas ──────────────────────────────────────────────────────────────────

    private void create(SellerRef seller, SaleChange change, Instant at) {
        LocalDate paidOn = LocalDate.ofInstant(at, clock.getZone());
        Optional<InForce> inForce = AgreedTerms.on(assignments, seller.companyId(), seller.id(), paidOn);
        String currency = change.currency() == null ? "COP" : change.currency();
        if (inForce.isEmpty()) {
            insertReview(seller, change, currency, paidOn, null, null, "El vendedor no tenía un plan vigente el " + paidOn + ".", at);
            return;
        }
        PlanTerms terms = inForce.get().terms();
        if (!terms.currency().equalsIgnoreCase(currency)) {
            insertReview(seller, change, currency, paidOn, inForce.get(), null,
                    "El plan cobra en " + terms.currency() + " y la venta fue en " + currency + ".", at);
            return;
        }
        SaleAmounts sale;
        try {
            sale = SaleAmounts.of(currency, change.itemsSubtotal(), change.discount(), change.shipping(), change.taxes());
        } catch (MarketplaceRuleViolation ex) {
            insertReview(seller, change, currency, paidOn, inForce.get(), null, ex.getMessage(), at);
            return;
        }
        SaleCharge charge = terms.chargeFor(sale);
        LocalDate accrueOn = CommissionRules.accrueOn(terms.accrualEvent(), paidOn, null, terms.returnWindowDays());
        CommissionRow row = new CommissionRow(null, seller.companyId(), seller.id(), seller.name(), change.orderId(),
                change.purchaseId(), change.orderNumber(), inForce.get().assignment().id(), inForce.get().assignment().planCode(),
                currency, sale.itemsSubtotal(), sale.discount(), sale.shipping(), sale.taxes(), charge.base(),
                terms.commissionPercent(), charge.percentPart(), charge.fixedPart(), charge.total(), Money.zero(currency),
                terms.accrualEvent().name(), terms.returnWindowDays(), paidOn, null, accrueOn, Status.PENDING, null, null, null,
                at);
        long id = commissions.insert(row, at);
        row = commissions.find(seller.companyId(), id, true).orElseThrow();
        if (CommissionRules.isDue(accrueOn, LocalDate.now(clock))) {
            accrue(row, at);
        }
    }

    private void insertReview(SellerRef seller, SaleChange change, String currency, LocalDate paidOn, InForce inForce,
                              String accrual, String reason, Instant at) {
        BigDecimal zero = Money.zero(currency.length() == 3 ? currency : "COP");
        commissions.insert(new CommissionRow(null, seller.companyId(), seller.id(), seller.name(), change.orderId(),
                change.purchaseId(), change.orderNumber(), inForce == null ? null : inForce.assignment().id(),
                inForce == null ? null : inForce.assignment().planCode(), currency, nz(change.itemsSubtotal()),
                nz(change.discount()), nz(change.shipping()), nz(change.taxes()), zero, BigDecimal.ZERO, zero, zero, zero, zero,
                accrual, 0, paidOn, null, null, Status.REVIEW, clip(reason, 300), null, null, at), at);
    }

    private void delivered(CommissionRow c, Instant at) {
        if (c.deliveredOn() != null || (c.status() != Status.PENDING && c.status() != Status.REVIEW)) {
            return;
        }
        LocalDate deliveredOn = LocalDate.ofInstant(at, clock.getZone());
        if (c.status() == Status.REVIEW) {
            // Se guarda la entrega para que el recálculo use la fecha correcta.
            commissions.update(c.with(deliveredOn, null, Status.REVIEW, c.reversedAmount(), null, null), at);
            return;
        }
        AccrualEvent event = c.accrualEvent() == null ? null : AccrualEvent.valueOf(c.accrualEvent());
        LocalDate accrueOn = c.accrueOn() != null ? c.accrueOn()
                : CommissionRules.accrueOn(event, c.paidOn(), deliveredOn, c.returnWindowDays());
        CommissionRow next = c.with(deliveredOn, accrueOn, c.status(), c.reversedAmount(), null, null);
        commissions.update(next, at);
        if (CommissionRules.isDue(accrueOn, LocalDate.now(clock))) {
            accrue(next, at);
        }
    }

    /** Cancelada antes de ser exigible: se anula. Después: reverso como devolución total. */
    private void cancelled(CommissionRow c, Instant at) {
        if (c.status() == Status.PENDING || c.status() == Status.REVIEW) {
            commissions.insertAdjustment(c.companyId(), c.id(), "CANCEL", "CANCEL:" + c.orderId(), c.base(), c.net(), false,
                    "Venta cancelada antes de ser exigible", at);
            commissions.update(c.with(c.deliveredOn(), c.accrueOn(), Status.VOIDED, c.reversedAmount(), null, at), at);
            return;
        }
        if (c.status() == Status.ACCRUED) {
            AgreedTerms.byId(assignments, c.companyId(), c.sellerId(), c.assignmentId()).ifPresent(terms ->
                    reverse(c, terms.terms(), c.base(), "CANCEL", "CANCEL:" + c.orderId(), "Venta cancelada", at));
        }
    }

    private void reverse(CommissionRow c, PlanTerms terms, BigDecimal refundedBase, String kind, String sourceKey,
                         String note, Instant at) {
        SaleCharge original = new SaleCharge(c.currency(), c.base(), c.percentPart(), c.fixedPart(), c.amount());
        BigDecimal amount = terms.reversalFor(original, refundedBase, c.reversedAmount());
        boolean accrued = c.status() == Status.ACCRUED;
        if (!commissions.insertAdjustment(c.companyId(), c.id(), kind, sourceKey, refundedBase, amount,
                accrued && amount.signum() > 0, note, at)) {
            return;
        }
        if (amount.signum() == 0) {
            return;
        }
        commissions.update(c.with(c.deliveredOn(), c.accrueOn(), c.status(), c.reversedAmount().add(amount), c.accruedAt(),
                c.voidedAt()), at);
        if (accrued) {
            LocalDate today = LocalDate.now(clock);
            ledger.post(c.companyId(), new NewEntry(c.sellerId(), "COMMISSION_REVERSAL", amount.negate(), c.currency(), today,
                    null, BigDecimal.ZERO, c.orderNumber(), c.id(), null, "REV:" + sourceKey, note), SYSTEM, null, at);
        }
    }

    private void accrue(CommissionRow c, Instant at) {
        BigDecimal net = c.net();
        if (net.signum() > 0) {
            Optional<InForce> terms = AgreedTerms.byId(assignments, c.companyId(), c.sellerId(), c.assignmentId());
            int grace = terms.map(t -> t.terms().graceDays()).orElse(0);
            BigDecimal interest = terms.map(t -> t.terms().lateInterestMonthlyPercent()).orElse(BigDecimal.ZERO);
            ledger.post(c.companyId(), new NewEntry(c.sellerId(), "COMMISSION", net, c.currency(), c.accrueOn(),
                    c.accrueOn().plusDays(grace), interest, c.orderNumber(), c.id(), null, "COMM:" + c.id(),
                    "Comisión " + (c.planCode() == null ? "" : c.planCode())), SYSTEM, null, at);
        }
        commissions.update(c.with(c.deliveredOn(), c.accrueOn(), Status.ACCRUED, c.reversedAmount(), at, null), at);
    }

    static CommissionDto toDto(CommissionRow c) {
        return new CommissionDto(c.id(), c.sellerId(), c.sellerName(), c.orderId(), c.purchaseId(), c.orderNumber(),
                c.planCode(), c.currency(), c.base(), c.percent(), c.percentPart(), c.fixedPart(), c.amount(),
                c.reversedAmount(), c.net(), c.accrualEvent(), c.paidOn(), c.deliveredOn(), c.accrueOn(), c.status().name(),
                c.reviewReason(), c.createdAt());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String clip(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
