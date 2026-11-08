package com.essenza.draco.modules.marketplace.application.port;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.PlanEventDto;
import com.essenza.draco.modules.marketplace.domain.plan.CommercialPlan;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.Status;

/** Persistencia de versiones de planes y su auditoría, siempre por empresa. */
public interface PlanStore {

    long insert(long companyId, CommercialPlan plan, String actorId, Instant now);

    Optional<PlanRow> find(long companyId, long planId, boolean forUpdate);

    /** Versión ACTIVE de un código, si hay. */
    Optional<PlanRow> activeVersion(long companyId, String code);

    int maxVersion(long companyId, String code);

    /** @return false si {@code lockVersion} no coincide */
    boolean updateDraft(long companyId, long planId, int lockVersion, CommercialPlan plan, String actorId, Instant now);

    /** @return false si {@code lockVersion} no coincide */
    boolean updateStatus(long companyId, long planId, int lockVersion, Status status, boolean publishing, String actorId,
                         Instant now);

    Page<PlanRow> search(long companyId, PlanFilter filter, Pageable pageable);

    /** Planes ACTIVE asignables en una fecha (para el buscador del formulario de asignación). */
    List<PlanRow> assignable(long companyId, String q, LocalDate on, int limit);

    void addEvent(long companyId, long planId, String type, String detail, String actorId, String actorName, Instant now);

    List<PlanEventDto> events(long companyId, long planId, int limit);

    record PlanFilter(String q, String status, String modality) {
    }

    record PlanRow(
            CommercialPlan plan,
            int lockVersion,
            Instant publishedAt,
            String publishedBy,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {
    }
}
