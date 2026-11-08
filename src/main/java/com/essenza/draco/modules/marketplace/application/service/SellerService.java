package com.essenza.draco.modules.marketplace.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.marketplace.application.MarketplacePermissions;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.ContactDto;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.ReadinessDto;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.SaveSellerRequest;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.SellerDetailDto;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.SellerEventDto;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.SellerStatsDto;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.SellerSummaryDto;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.SellerTransitionRequest;
import com.essenza.draco.modules.marketplace.application.port.AssignmentStore;
import com.essenza.draco.modules.marketplace.application.port.AssignmentStore.AssignmentRow;
import com.essenza.draco.modules.marketplace.application.port.SellerStore;
import com.essenza.draco.modules.marketplace.application.port.SellerStore.DocumentRow;
import com.essenza.draco.modules.marketplace.application.port.SellerStore.SellerFilter;
import com.essenza.draco.modules.marketplace.application.port.SellerStore.SellerRow;
import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.domain.assignment.PlanSchedule;
import com.essenza.draco.modules.marketplace.domain.assignment.PlanSchedule.State;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.Action;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.DocType;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.Kind;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.Status;
import com.essenza.draco.modules.marketplace.domain.seller.SellerProfile;
import com.essenza.draco.modules.marketplace.domain.seller.SellerProfile.SellerContact;
import com.essenza.draco.modules.marketplace.domain.seller.SellerWorkflow;
import com.essenza.draco.modules.marketplace.domain.seller.SellerWorkflow.DocumentState;
import com.essenza.draco.modules.marketplace.domain.seller.SellerWorkflow.Readiness;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Vendedores: ficha, contactos, flujo de estados con motivo y auditoría. No hay borrado:
 * un vendedor se cierra y conserva su historial.
 */
@Service
public class SellerService {

    private final SellerStore sellers;
    private final AssignmentStore assignments;
    private final ActorProvider actors;
    private final Clock clock;

    public SellerService(SellerStore sellers, AssignmentStore assignments, ActorProvider actors, Clock marketplaceClock) {
        this.sellers = sellers;
        this.assignments = assignments;
        this.actors = actors;
        this.clock = marketplaceClock;
    }

    @Transactional(readOnly = true)
    public Page<SellerSummaryDto> search(SellerFilter filter, Pageable pageable) {
        RequestActor actor = actors.current().require(MarketplacePermissions.SELLERS_VIEW);
        Page<SellerRow> page = sellers.search(actor.companyId(), filter, pageable);
        LocalDate today = today();
        Map<Long, List<AssignmentRow>> open = assignments.openFor(actor.companyId(),
                page.getContent().stream().map(SellerRow::id).toList(), today);
        List<SellerSummaryDto> content = page.getContent().stream()
                .map(row -> summary(row, open.getOrDefault(row.id(), List.of()), today)).toList();
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    /** Buscador de vendedores (formularios de ofertas). Excluye cerrados. */
    @Transactional(readOnly = true)
    public List<com.essenza.draco.modules.marketplace.application.dto.OfferDtos.SellerOptionDto> lookup(String q, String kind) {
        RequestActor actor = actors.current().requireAny(MarketplacePermissions.SELLERS_VIEW, MarketplacePermissions.OFFERS_MANAGE);
        return sellers.lookup(actor.companyId(), q, kind, 20).stream()
                .map(r -> new com.essenza.draco.modules.marketplace.application.dto.OfferDtos.SellerOptionDto(r.id(),
                        r.profile().code(), r.profile().tradeName() != null ? r.profile().tradeName() : r.profile().legalName(),
                        r.profile().kind().name(), r.status().name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SellerStatsDto stats() {
        RequestActor actor = actors.current().require(MarketplacePermissions.SELLERS_VIEW);
        return sellers.stats(actor.companyId());
    }

    @Transactional(readOnly = true)
    public SellerDetailDto get(long id) {
        RequestActor actor = actors.current().require(MarketplacePermissions.SELLERS_VIEW);
        return detail(actor, load(actor, id, false));
    }

    @Transactional(readOnly = true)
    public List<SellerEventDto> events(long id) {
        RequestActor actor = actors.current().require(MarketplacePermissions.SELLERS_VIEW);
        load(actor, id, false);
        return sellers.events(actor.companyId(), id, 300);
    }

    @Transactional
    public SellerDetailDto create(SaveSellerRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.SELLERS_MANAGE);
        SellerProfile profile = profile(request, null);
        if (profile.accountManagerName() == null) {
            // Sin responsable explícito queda quien lo crea.
            profile = withManager(profile, actor.userId(), actor.displayName());
        }
        Instant now = clock.instant();
        long id = sellers.insert(actor.companyId(), profile, Status.DRAFT, actor.userId(), now);
        if (profile.code() == null) {
            sellers.assignCode(actor.companyId(), id, String.format("VEN-%05d", id));
        }
        sellers.addEvent(actor.companyId(), id, "CREATED", null, Status.DRAFT, null, profile.kind().name(),
                actor.userId(), actor.displayName(), now);
        return detail(actor, load(actor, id, false));
    }

    @Transactional
    public SellerDetailDto update(long id, SaveSellerRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.SELLERS_MANAGE);
        SellerRow row = load(actor, id, true);
        checkVersion(row, request.version());
        MarketplaceRuleViolation.check(row.status().profileEditable(), row.status() == Status.IN_REVIEW
                ? "El vendedor está en revisión: espera la decisión o pide correcciones antes de editar."
                : "Un vendedor cerrado no se edita.");
        SellerProfile profile = profile(request, row.profile());
        MarketplaceRuleViolation.check(profile.kind() == row.profile().kind() || row.status() == Status.DRAFT,
                "El tipo de vendedor (propio o externo) solo se cambia en borrador.");
        Instant now = clock.instant();
        if (!sellers.updateProfile(actor.companyId(), id, row.version(), profile, actor.userId(), now)) {
            throw stale();
        }
        sellers.addEvent(actor.companyId(), id, "EDITED", null, null, null, changes(row.profile(), profile),
                actor.userId(), actor.displayName(), now);
        return detail(actor, load(actor, id, false));
    }

    @Transactional
    public SellerDetailDto transition(long id, SellerTransitionRequest request) {
        RequestActor actor = actors.current();
        Action action = PlanEnums.parse(Action.class, request.action(), "La acción");
        actor.require(permissionFor(action));
        SellerRow row = load(actor, id, true);
        checkVersion(row, request.version());
        Readiness readiness = readiness(actor, row);
        Status next = SellerWorkflow.transition(row.status(), action, request.reason(), readiness);
        Instant now = clock.instant();
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        if (!sellers.updateStatus(actor.companyId(), id, row.version(), next, reason, action == Action.APPROVE,
                actor.userId(), now)) {
            throw stale();
        }
        sellers.addEvent(actor.companyId(), id, action.name(), row.status(), next, reason, null, actor.userId(),
                actor.displayName(), now);
        return detail(actor, load(actor, id, false));
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    static String permissionFor(Action action) {
        return action == Action.SUBMIT ? MarketplacePermissions.SELLERS_MANAGE : MarketplacePermissions.SELLERS_APPROVE;
    }

    SellerRow load(RequestActor actor, long id, boolean forUpdate) {
        return sellers.find(actor.companyId(), id, forUpdate)
                .orElseThrow(() -> new NotFoundException("Vendedor " + id + " no encontrado."));
    }

    Readiness readiness(RequestActor actor, SellerRow row) {
        List<DocumentState> docs = sellers.documents(actor.companyId(), row.id(), false).stream()
                .map(d -> new DocumentState(d.kind(), d.review())).toList();
        LocalDate today = today();
        boolean hasPlan = assignments.bySeller(actor.companyId(), row.id()).stream()
                .map(a -> period(a).stateOn(today))
                .anyMatch(state -> state == State.CURRENT || state == State.SCHEDULED);
        return SellerWorkflow.readiness(row.profile(), docs, hasPlan);
    }

    SellerDetailDto detail(RequestActor actor, SellerRow row) {
        SellerProfile p = row.profile();
        Readiness readiness = readiness(actor, row);
        List<String> allowed = SellerWorkflow.actionsFrom(row.status()).stream()
                .filter(a -> actor.has(permissionFor(a))).map(Enum::name).toList();
        List<DocumentRow> docs = sellers.documents(actor.companyId(), row.id(), true);
        LocalDate today = today();
        return new SellerDetailDto(row.id(), p.code(), p.kind().name(), p.legalName(), p.tradeName(), p.docType().name(),
                p.docNumber(), p.email(), p.phone(), p.website(), p.address(), p.city(), p.description(),
                p.accountManagerId(), p.accountManagerName(),
                p.contacts().stream().map(c -> new ContactDto(c.name(), c.role(), c.email(), c.phone(), c.primary())).toList(),
                row.status().name(), row.statusReason(), row.statusChangedAt(), row.approvedAt(), row.approvedBy(),
                row.version(), new ReadinessDto(readiness.forSubmit(), readiness.forApproval()), allowed,
                SellerWorkflow.requiredDocuments(p.kind(), p.docType()).stream().map(Enum::name).sorted().toList(),
                docs.stream().map(SellerDocumentService::toDto).toList(),
                assignments.bySeller(actor.companyId(), row.id()).stream().map(a -> AssignmentService.toDto(a, today)).toList(),
                row.createdAt(), row.createdBy(), row.updatedAt(), row.updatedBy());
    }

    static PlanSchedule.Period period(AssignmentRow a) {
        return new PlanSchedule.Period(a.id(), a.startsOn(), a.endsOn(), a.cancelledAt() != null);
    }

    private static SellerSummaryDto summary(SellerRow row, List<AssignmentRow> open, LocalDate today) {
        AssignmentRow current = open.stream().filter(a -> period(a).stateOn(today) == State.CURRENT).findFirst().orElse(null);
        AssignmentRow scheduled = open.stream().filter(a -> period(a).stateOn(today) == State.SCHEDULED).findFirst().orElse(null);
        SellerProfile p = row.profile();
        return new SellerSummaryDto(row.id(), p.code(), p.kind().name(), p.legalName(), p.tradeName(), p.docType().name(),
                p.docNumber(), p.email(), p.phone(), p.city(), row.status().name(), p.accountManagerName(),
                current == null ? null : current.planCode(), current == null ? null : current.planName(),
                current == null ? null : current.planVersion(), scheduled == null ? null : scheduled.startsOn(),
                row.createdAt(), row.updatedAt());
    }

    private static SellerProfile profile(SaveSellerRequest r, SellerProfile previous) {
        List<SellerContact> contacts = new ArrayList<>();
        if (r.contacts() != null) {
            for (ContactDto c : r.contacts()) {
                if (c == null) {
                    continue;
                }
                contacts.add(new SellerContact(c.name(), c.role(), c.email(), c.phone(), c.primary()));
            }
        }
        String code = r.code();
        if (previous != null) {
            String requested = SellerProfile.normalizeCode(code);
            MarketplaceRuleViolation.check(requested == null || requested.equals(previous.code()),
                    "El código del vendedor no se cambia.");
            code = previous.code();
        }
        // El id del responsable nunca se toma del navegador: solo el servidor lo fija (el actor al
        // crear) y se conserva mientras no cambie el nombre; un nombre nuevo queda como texto.
        String managerName = r.accountManagerName() == null || r.accountManagerName().isBlank() ? null : r.accountManagerName().trim();
        String managerId = previous != null && managerName != null && managerName.equals(previous.accountManagerName())
                ? previous.accountManagerId() : null;
        return new SellerProfile(code, PlanEnums.parse(Kind.class, r.kind(), "El tipo de vendedor"), r.legalName(),
                r.tradeName(), PlanEnums.parse(DocType.class, r.docType(), "El tipo de documento"), r.docNumber(),
                r.email(), r.phone(), r.website(), r.address(), r.city(), r.description(), managerId, managerName, contacts);
    }

    private static SellerProfile withManager(SellerProfile p, String id, String name) {
        return new SellerProfile(p.code(), p.kind(), p.legalName(), p.tradeName(), p.docType(), p.docNumber(), p.email(),
                p.phone(), p.website(), p.address(), p.city(), p.description(), id, name, p.contacts());
    }

    /** Resumen de campos cambiados para la auditoría (sin valores sensibles). */
    private static String changes(SellerProfile before, SellerProfile after) {
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(before.legalName(), after.legalName())) changed.add("legal_name");
        if (!Objects.equals(before.tradeName(), after.tradeName())) changed.add("trade_name");
        if (before.docType() != after.docType() || !Objects.equals(before.docNumber(), after.docNumber())) changed.add("document");
        if (!Objects.equals(before.email(), after.email())) changed.add("email");
        if (!Objects.equals(before.phone(), after.phone())) changed.add("phone");
        if (!Objects.equals(before.address(), after.address()) || !Objects.equals(before.city(), after.city())) changed.add("address");
        if (!Objects.equals(before.website(), after.website())) changed.add("website");
        if (!Objects.equals(before.description(), after.description())) changed.add("description");
        if (!Objects.equals(before.accountManagerId(), after.accountManagerId())
                || !Objects.equals(before.accountManagerName(), after.accountManagerName())) changed.add("account_manager");
        if (!Objects.equals(before.contacts(), after.contacts())) changed.add("contacts");
        if (before.kind() != after.kind()) changed.add("kind");
        return changed.isEmpty() ? "sin cambios" : String.join(",", changed);
    }

    private static void checkVersion(SellerRow row, Integer version) {
        MarketplaceRuleViolation.check(version != null, "Falta version (concurrencia).");
        if (row.version() != version) {
            throw stale();
        }
    }

    private static ConflictException stale() {
        return new ConflictException("El vendedor cambió mientras lo editabas. Recarga e intenta de nuevo.");
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
