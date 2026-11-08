package com.essenza.draco.modules.marketplace.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.marketplace.application.MarketplacePermissions;
import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.AssignPlanRequest;
import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.AssignmentDto;
import com.essenza.draco.modules.marketplace.application.port.AssignmentStore;
import com.essenza.draco.modules.marketplace.application.port.AssignmentStore.AssignmentRow;
import com.essenza.draco.modules.marketplace.application.port.AssignmentStore.NewAssignment;
import com.essenza.draco.modules.marketplace.application.port.PlanStore;
import com.essenza.draco.modules.marketplace.application.port.PlanStore.PlanRow;
import com.essenza.draco.modules.marketplace.application.port.SellerStore;
import com.essenza.draco.modules.marketplace.application.port.SellerStore.SellerRow;
import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.domain.assignment.PlanSchedule;
import com.essenza.draco.modules.marketplace.domain.assignment.PlanSchedule.Decision;
import com.essenza.draco.modules.marketplace.domain.assignment.PlanSchedule.Period;
import com.essenza.draco.modules.marketplace.domain.plan.Money;
import com.essenza.draco.modules.marketplace.domain.plan.PlanTerms;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.Kind;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.Status;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Asignación de planes a vendedores externos. Cada vigencia guarda una copia de las condiciones
 * acordadas (plan + excepciones): cambiar o versionar el plan no altera lo pactado ni lo
 * histórico. Los cambios aplican al siguiente ciclo, sin prorrateo.
 */
@Service
public class AssignmentService {

    private final SellerStore sellers;
    private final PlanStore plans;
    private final AssignmentStore assignments;
    private final ActorProvider actors;
    private final Clock clock;

    public AssignmentService(SellerStore sellers, PlanStore plans, AssignmentStore assignments, ActorProvider actors,
                             Clock marketplaceClock) {
        this.sellers = sellers;
        this.plans = plans;
        this.assignments = assignments;
        this.actors = actors;
        this.clock = marketplaceClock;
    }

    @Transactional(readOnly = true)
    public List<AssignmentDto> list(long sellerId) {
        RequestActor actor = actors.current().require(MarketplacePermissions.SELLERS_VIEW);
        seller(actor, sellerId, false);
        LocalDate today = today();
        return assignments.bySeller(actor.companyId(), sellerId).stream().map(a -> toDto(a, today)).toList();
    }

    @Transactional
    public AssignmentDto assign(long sellerId, AssignPlanRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.PLANS_ASSIGN);
        MarketplaceRuleViolation.check(request.planId() != null, "Elige el plan.");
        // Bloquea el vendedor: dos asignaciones simultáneas no pueden solaparse.
        SellerRow seller = seller(actor, sellerId, true);
        MarketplaceRuleViolation.check(seller.profile().kind() == Kind.EXTERNAL,
                "El vendedor propio no tiene plan comercial: no se cobra comisión a sí mismo.");
        MarketplaceRuleViolation.check(seller.status() != Status.CLOSED && seller.status() != Status.SUSPENDED,
                "No se asignan planes a vendedores " + (seller.status() == Status.CLOSED ? "cerrados." : "suspendidos."));

        boolean exception = request.overrideCommissionPercent() != null || request.overrideMonthlyFee() != null;
        String overrideReason = null;
        if (exception) {
            actor.require(MarketplacePermissions.PLANS_EXCEPTIONS);
            overrideReason = request.overrideReason() == null ? "" : request.overrideReason().trim();
            MarketplaceRuleViolation.check(overrideReason.length() >= 5 && overrideReason.length() <= 1000,
                    "Explica la excepción comercial (motivo de 5 a 1000 caracteres).");
        }
        String notes = request.notes() == null || request.notes().isBlank() ? null : request.notes().trim();
        MarketplaceRuleViolation.check(notes == null || notes.length() <= 1000, "Las notas admiten máximo 1000 caracteres.");

        // Bloquea también el plan (orden: vendedor y luego plan) para no asignar uno que se está retirando.
        PlanRow plan = plans.find(actor.companyId(), request.planId(), true)
                .orElseThrow(() -> new NotFoundException("Plan " + request.planId() + " no encontrado."));
        LocalDate today = today();
        List<AssignmentRow> existing = assignments.bySeller(actor.companyId(), sellerId);
        Decision decision = PlanSchedule.schedule(existing.stream().map(SellerService::period).toList(),
                request.startsOn(), today);
        plan.plan().checkAssignableOn(decision.startsOn());
        PlanTerms agreed = plan.plan().terms().withOverrides(
                request.overrideCommissionPercent() == null ? null : Money.percent(request.overrideCommissionPercent(), "La comisión"),
                request.overrideMonthlyFee());

        Instant now = clock.instant();
        if (decision.closesPeriodId() != null) {
            assignments.setEndsOn(actor.companyId(), decision.closesPeriodId(), decision.closesOn());
        }
        long id = assignments.insert(actor.companyId(), new NewAssignment(sellerId, plan.plan().id(), decision.startsOn(),
                request.overrideCommissionPercent() == null ? null : agreed.commissionPercent(),
                request.overrideMonthlyFee() == null ? null : agreed.monthlyFee(), overrideReason,
                exception ? actor.userId() : null, TermsMapper.toJson(agreed), notes), actor.userId(), now);
        String detail = plan.plan().code() + " v" + plan.plan().version() + " desde " + decision.startsOn()
                + (exception ? " con excepción" : "");
        sellers.addEvent(actor.companyId(), sellerId, decision.startsOn().isAfter(today) ? "PLAN_SCHEDULED" : "PLAN_ASSIGNED",
                null, null, overrideReason, detail, actor.userId(), actor.displayName(), now);
        return assignments.bySeller(actor.companyId(), sellerId).stream().filter(a -> a.id() == id).findFirst()
                .map(a -> toDto(a, today)).orElseThrow();
    }

    /** Cancela un cambio programado; el plan vigente vuelve a quedar sin fecha de fin. */
    @Transactional
    public void cancel(long sellerId, long assignmentId, String reason) {
        RequestActor actor = actors.current().require(MarketplacePermissions.PLANS_ASSIGN);
        seller(actor, sellerId, true);
        String text = reason == null ? "" : reason.trim();
        MarketplaceRuleViolation.check(text.length() >= 5 && text.length() <= 1000, "Escribe el motivo (5 a 1000 caracteres).");
        LocalDate today = today();
        List<AssignmentRow> existing = assignments.bySeller(actor.companyId(), sellerId);
        AssignmentRow target = existing.stream().filter(a -> a.id() == assignmentId).findFirst()
                .orElseThrow(() -> new NotFoundException("Asignación " + assignmentId + " no encontrada."));
        List<Period> periods = existing.stream().map(SellerService::period).toList();
        Optional<Period> reopen = PlanSchedule.cancel(SellerService.period(target), periods, today);
        Instant now = clock.instant();
        assignments.cancel(actor.companyId(), assignmentId, text, actor.userId(), now);
        reopen.ifPresent(p -> assignments.setEndsOn(actor.companyId(), p.id(), null));
        sellers.addEvent(actor.companyId(), sellerId, "PLAN_CANCELLED", null, null, text,
                target.planCode() + " v" + target.planVersion() + " desde " + target.startsOn(), actor.userId(),
                actor.displayName(), now);
    }

    private SellerRow seller(RequestActor actor, long sellerId, boolean forUpdate) {
        return sellers.find(actor.companyId(), sellerId, forUpdate)
                .orElseThrow(() -> new NotFoundException("Vendedor " + sellerId + " no encontrado."));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    static AssignmentDto toDto(AssignmentRow a, LocalDate today) {
        return new AssignmentDto(a.id(), a.planId(), a.planCode(), a.planName(), a.planVersion(), a.startsOn(), a.endsOn(),
                SellerService.period(a).stateOn(today).name(), a.overrideCommissionPercent(), a.overrideMonthlyFee(),
                a.overrideReason(), a.overrideApprovedBy(), TermsMapper.fromJson(a.agreedTermsJson()), a.notes(),
                a.cancelledAt(), a.cancelledBy(), a.cancelReason(), a.createdAt(), a.createdBy());
    }
}
