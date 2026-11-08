package com.essenza.draco.modules.marketplace.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Contratos del API de planes, asignaciones y simulador. */
public final class PlanDtos {

    private PlanDtos() {
    }

    /** Condiciones económicas (las de un plan o las acordadas en una asignación). */
    public record TermsDto(
            String modality,
            String currency,
            BigDecimal monthlyFee,
            BigDecimal commissionPercent,
            BigDecimal fixedFeePerSale,
            String commissionBase,
            boolean includeShipping,
            boolean includeTaxes,
            String accrualEvent,
            int returnWindowDays,
            String refundPolicy,
            String feeBilling,
            int graceDays,
            BigDecimal lateInterestMonthlyPercent,
            BigDecimal storageFeePerUnitDay) {
    }

    /** Alta o edición de un borrador. {@code lockVersion} obligatoria al editar. */
    public record SavePlanRequest(
            String code,
            String name,
            String description,
            String modality,
            String currency,
            BigDecimal monthlyFee,
            BigDecimal commissionPercent,
            BigDecimal fixedFeePerSale,
            String commissionBase,
            Boolean includeShipping,
            Boolean includeTaxes,
            String accrualEvent,
            Integer returnWindowDays,
            String refundPolicy,
            String feeBilling,
            Integer graceDays,
            BigDecimal lateInterestMonthlyPercent,
            BigDecimal storageFeePerUnitDay,
            LocalDate validFrom,
            LocalDate validTo,
            Integer lockVersion) {
    }

    /** Publicar, retirar o crear nueva versión: solo lleva la versión de bloqueo. */
    public record PlanCommandRequest(Integer lockVersion) {
    }

    public record PlanDto(
            long id,
            String code,
            int version,
            String name,
            String description,
            String status,
            TermsDto terms,
            LocalDate validFrom,
            LocalDate validTo,
            long openAssignments,
            Instant publishedAt,
            String publishedBy,
            int lockVersion,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {
    }

    public record PlanEventDto(long id, String type, String detail, String actorId, String actorName, Instant createdAt) {
    }

    /** Venta típica del mes y excepciones opcionales a simular. */
    public record SimulationRequest(
            BigDecimal itemsSubtotal,
            BigDecimal discount,
            BigDecimal shipping,
            BigDecimal taxes,
            Integer salesPerMonth,
            BigDecimal overrideCommissionPercent,
            BigDecimal overrideMonthlyFee) {
    }

    /** Resultado SOLO informativo: {@code simulation} siempre es true y nada se cobra. */
    public record SimulationDto(
            boolean simulation,
            String notice,
            String currency,
            int salesCount,
            BigDecimal commissionBasePerSale,
            BigDecimal percentPartPerSale,
            BigDecimal fixedPartPerSale,
            BigDecimal commissionPerSale,
            BigDecimal grossSales,
            BigDecimal commissionTotal,
            BigDecimal monthlyFee,
            BigDecimal platformTotal,
            BigDecimal sellerNet,
            BigDecimal effectiveRatePercent) {
    }

    /** Asignar o programar un plan. startsOn vacío: hoy (sin plan vigente) o el próximo ciclo. */
    public record AssignPlanRequest(
            Long planId,
            LocalDate startsOn,
            BigDecimal overrideCommissionPercent,
            BigDecimal overrideMonthlyFee,
            String overrideReason,
            String notes) {
    }

    public record CancelAssignmentRequest(String reason) {
    }

    public record AssignmentDto(
            long id,
            long planId,
            String planCode,
            String planName,
            int planVersion,
            LocalDate startsOn,
            LocalDate endsOn,
            String state,
            BigDecimal overrideCommissionPercent,
            BigDecimal overrideMonthlyFee,
            String overrideReason,
            String overrideApprovedBy,
            TermsDto agreedTerms,
            String notes,
            Instant cancelledAt,
            String cancelledBy,
            String cancelReason,
            Instant createdAt,
            String createdBy) {
    }
}
