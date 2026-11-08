package com.essenza.draco.modules.marketplace.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Vigencias de planes por vendedor. */
public interface AssignmentStore {

    /** Todas las vigencias del vendedor (incluidas canceladas), de la más reciente a la más antigua. */
    List<AssignmentRow> bySeller(long companyId, long sellerId);

    /** Vigente o programada de cada vendedor (para listados), sin canceladas ni terminadas. */
    Map<Long, List<AssignmentRow>> openFor(long companyId, Collection<Long> sellerIds, LocalDate today);

    /** Vigencias abiertas (vigentes o programadas) por plan. */
    Map<Long, Long> openCountByPlan(long companyId, Collection<Long> planIds, LocalDate today);

    long insert(long companyId, NewAssignment assignment, String actorId, Instant now);

    void setEndsOn(long companyId, long assignmentId, LocalDate endsOn);

    void cancel(long companyId, long assignmentId, String reason, String actorId, Instant now);

    record NewAssignment(
            long sellerId,
            long planId,
            LocalDate startsOn,
            BigDecimal overrideCommissionPercent,
            BigDecimal overrideMonthlyFee,
            String overrideReason,
            String overrideApprovedBy,
            String agreedTermsJson,
            String notes) {
    }

    record AssignmentRow(
            long id,
            long sellerId,
            long planId,
            String planCode,
            String planName,
            int planVersion,
            LocalDate startsOn,
            LocalDate endsOn,
            BigDecimal overrideCommissionPercent,
            BigDecimal overrideMonthlyFee,
            String overrideReason,
            String overrideApprovedBy,
            String agreedTermsJson,
            String notes,
            Instant cancelledAt,
            String cancelledBy,
            String cancelReason,
            Instant createdAt,
            String createdBy) {
    }
}
