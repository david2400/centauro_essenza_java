package com.essenza.draco.modules.marketplace.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.marketplace.application.MarketplacePermissions;
import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.PlanDto;
import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.PlanEventDto;
import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.SavePlanRequest;
import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.SimulationDto;
import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.SimulationRequest;
import com.essenza.draco.modules.marketplace.application.port.AssignmentStore;
import com.essenza.draco.modules.marketplace.application.port.PlanStore;
import com.essenza.draco.modules.marketplace.application.port.PlanStore.PlanFilter;
import com.essenza.draco.modules.marketplace.application.port.PlanStore.PlanRow;
import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.domain.plan.CommercialPlan;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.Status;
import com.essenza.draco.modules.marketplace.domain.plan.PlanSimulation;
import com.essenza.draco.modules.marketplace.domain.plan.PlanTerms;
import com.essenza.draco.modules.marketplace.domain.plan.SaleAmounts;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Planes comerciales: borradores, publicación (inmutable), nuevas versiones, retiro y
 * simulador. Cada operación valida permiso y empresa del actor.
 */
@Service
public class PlanService {

    static final String SIMULATION_NOTICE = "Simulación informativa: no genera cargos, facturas ni cobros.";

    private final PlanStore plans;
    private final AssignmentStore assignments;
    private final ActorProvider actors;
    private final Clock clock;

    public PlanService(PlanStore plans, AssignmentStore assignments, ActorProvider actors, Clock marketplaceClock) {
        this.plans = plans;
        this.assignments = assignments;
        this.actors = actors;
        this.clock = marketplaceClock;
    }

    @Transactional(readOnly = true)
    public Page<PlanDto> search(PlanFilter filter, Pageable pageable) {
        RequestActor actor = actors.current().requireAny(MarketplacePermissions.PLANS_VIEW, MarketplacePermissions.PLANS_ASSIGN);
        Page<PlanRow> page = plans.search(actor.companyId(), filter, pageable);
        Map<Long, Long> open = assignments.openCountByPlan(actor.companyId(),
                page.getContent().stream().map(r -> r.plan().id()).toList(), today());
        return new PageImpl<>(page.getContent().stream().map(r -> toDto(r, open.getOrDefault(r.plan().id(), 0L))).toList(),
                pageable, page.getTotalElements());
    }

    /** Planes publicados que se pueden asignar hoy (buscador del formulario de asignación). */
    @Transactional(readOnly = true)
    public List<PlanDto> assignable(String q) {
        RequestActor actor = actors.current().require(MarketplacePermissions.PLANS_ASSIGN);
        return plans.assignable(actor.companyId(), q, today(), 20).stream().map(r -> toDto(r, 0)).toList();
    }

    @Transactional(readOnly = true)
    public PlanDto get(long id) {
        RequestActor actor = actors.current().requireAny(MarketplacePermissions.PLANS_VIEW, MarketplacePermissions.PLANS_ASSIGN);
        return detail(actor, id);
    }

    private PlanDto detail(RequestActor actor, long id) {
        PlanRow row = load(actor, id, false);
        long open = assignments.openCountByPlan(actor.companyId(), List.of(id), today()).getOrDefault(id, 0L);
        return toDto(row, open);
    }

    @Transactional(readOnly = true)
    public List<PlanEventDto> events(long id) {
        RequestActor actor = actors.current().require(MarketplacePermissions.PLANS_VIEW);
        load(actor, id, false);
        return plans.events(actor.companyId(), id, 200);
    }

    @Transactional
    public PlanDto create(SavePlanRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.PLANS_MANAGE);
        PlanTerms terms = TermsMapper.fromRequest(request);
        LocalDate from = request.validFrom() == null ? today() : request.validFrom();
        CommercialPlan draft = CommercialPlan.draft(request.code(), request.name(), request.description(), terms, from,
                request.validTo());
        if (plans.maxVersion(actor.companyId(), draft.code()) > 0) {
            throw new ConflictException("Ya existe un plan con el código " + draft.code()
                    + ". Para cambiar sus condiciones crea una nueva versión.");
        }
        Instant now = clock.instant();
        long id = plans.insert(actor.companyId(), draft, actor.userId(), now);
        plans.addEvent(actor.companyId(), id, "CREATED", draft.code() + " v1", actor.userId(), actor.displayName(), now);
        return detail(actor, id);
    }

    @Transactional
    public PlanDto update(long id, SavePlanRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.PLANS_MANAGE);
        PlanRow row = load(actor, id, true);
        checkLock(row, request.lockVersion());
        if (request.code() != null && !request.code().isBlank()
                && !CommercialPlan.normalizeCode(request.code()).equals(row.plan().code())) {
            throw new MarketplaceRuleViolation("El código de un plan no se cambia; crea otro plan.");
        }
        CommercialPlan edited = row.plan().edit(request.name(), request.description(), TermsMapper.fromRequest(request),
                request.validFrom() == null ? row.plan().validFrom() : request.validFrom(), request.validTo());
        Instant now = clock.instant();
        if (!plans.updateDraft(actor.companyId(), id, row.lockVersion(), edited, actor.userId(), now)) {
            throw stale();
        }
        plans.addEvent(actor.companyId(), id, "EDITED", null, actor.userId(), actor.displayName(), now);
        return detail(actor, id);
    }

    /**
     * Publica el borrador. Si había otra versión ACTIVE del mismo código queda SUPERSEDED: quien
     * la tiene asignada la conserva; no se migra a nadie.
     */
    @Transactional
    public PlanDto publish(long id, Integer lockVersion) {
        RequestActor actor = actors.current().require(MarketplacePermissions.PLANS_MANAGE);
        PlanRow row = load(actor, id, true);
        checkLock(row, lockVersion);
        CommercialPlan published = row.plan().publish(today());
        Instant now = clock.instant();
        plans.activeVersion(actor.companyId(), published.code()).ifPresent(previous -> {
            previous.plan().supersede();
            if (!plans.updateStatus(actor.companyId(), previous.plan().id(), previous.lockVersion(), Status.SUPERSEDED,
                    false, actor.userId(), now)) {
                throw stale();
            }
            plans.addEvent(actor.companyId(), previous.plan().id(), "SUPERSEDED",
                    "Reemplazado por v" + published.version() + "; las asignaciones existentes se conservan",
                    actor.userId(), actor.displayName(), now);
        });
        if (!plans.updateStatus(actor.companyId(), id, row.lockVersion(), Status.ACTIVE, true, actor.userId(), now)) {
            throw stale();
        }
        plans.addEvent(actor.companyId(), id, "PUBLISHED", published.code() + " v" + published.version(),
                actor.userId(), actor.displayName(), now);
        return detail(actor, id);
    }

    /** Borrador de la siguiente versión, copiando las condiciones de esta. */
    @Transactional
    public PlanDto newVersion(long id, Integer lockVersion) {
        RequestActor actor = actors.current().require(MarketplacePermissions.PLANS_MANAGE);
        PlanRow row = load(actor, id, true);
        checkLock(row, lockVersion);
        int next = plans.maxVersion(actor.companyId(), row.plan().code()) + 1;
        CommercialPlan draft = row.plan().nextVersionDraft(next, today());
        Instant now = clock.instant();
        long newId = plans.insert(actor.companyId(), draft, actor.userId(), now);
        plans.addEvent(actor.companyId(), newId, "VERSION_CREATED", "Desde v" + row.plan().version(), actor.userId(),
                actor.displayName(), now);
        return detail(actor, newId);
    }

    @Transactional
    public PlanDto retire(long id, Integer lockVersion) {
        RequestActor actor = actors.current().require(MarketplacePermissions.PLANS_MANAGE);
        PlanRow row = load(actor, id, true);
        checkLock(row, lockVersion);
        row.plan().retire();
        Instant now = clock.instant();
        if (!plans.updateStatus(actor.companyId(), id, row.lockVersion(), Status.RETIRED, false, actor.userId(), now)) {
            throw stale();
        }
        plans.addEvent(actor.companyId(), id, "RETIRED", "Las asignaciones existentes se conservan", actor.userId(),
                actor.displayName(), now);
        return detail(actor, id);
    }

    /** Simulación con las mismas reglas del cálculo real. No escribe nada. */
    @Transactional(readOnly = true)
    public SimulationDto simulate(long id, SimulationRequest request) {
        RequestActor actor = actors.current().requireAny(MarketplacePermissions.PLANS_VIEW, MarketplacePermissions.PLANS_ASSIGN);
        PlanTerms terms = load(actor, id, false).plan().terms()
                .withOverrides(request.overrideCommissionPercent(), request.overrideMonthlyFee());
        SaleAmounts sale = SaleAmounts.of(terms.currency(), request.itemsSubtotal(), request.discount(), request.shipping(),
                request.taxes());
        PlanSimulation sim = PlanSimulation.of(terms, sale, request.salesPerMonth() == null ? 1 : request.salesPerMonth());
        return new SimulationDto(true, SIMULATION_NOTICE, sim.currency(), sim.salesCount(), sim.perSale().base(),
                sim.perSale().percentPart(), sim.perSale().fixedPart(), sim.perSale().total(), sim.grossSales(),
                sim.commissionTotal(), sim.monthlyFee(), sim.platformTotal(), sim.sellerNet(), sim.effectiveRatePercent());
    }

    PlanRow load(RequestActor actor, long id, boolean forUpdate) {
        return plans.find(actor.companyId(), id, forUpdate)
                .orElseThrow(() -> new NotFoundException("Plan " + id + " no encontrado."));
    }

    private static void checkLock(PlanRow row, Integer lockVersion) {
        MarketplaceRuleViolation.check(lockVersion != null, "Falta lock_version (concurrencia).");
        if (row.lockVersion() != lockVersion) {
            throw stale();
        }
    }

    private static ConflictException stale() {
        return new ConflictException("El plan cambió mientras lo editabas. Recarga e intenta de nuevo.");
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    static PlanDto toDto(PlanRow r, long openAssignments) {
        CommercialPlan p = r.plan();
        return new PlanDto(p.id(), p.code(), p.version(), p.name(), p.description(), p.status().name(),
                TermsMapper.toDto(p.terms()), p.validFrom(), p.validTo(), openAssignments, r.publishedAt(),
                r.publishedBy(), r.lockVersion(), r.createdAt(), r.createdBy(), r.updatedAt(), r.updatedBy());
    }
}
