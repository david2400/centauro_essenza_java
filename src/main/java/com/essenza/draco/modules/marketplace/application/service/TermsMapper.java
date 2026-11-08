package com.essenza.draco.modules.marketplace.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.SavePlanRequest;
import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.TermsDto;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.AccrualEvent;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.CommissionBase;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.FeeBilling;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.Modality;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.RefundPolicy;
import com.essenza.draco.modules.marketplace.domain.plan.PlanTerms;

/** Conversión de condiciones entre dominio, API y la copia JSON guardada en cada asignación. */
public final class TermsMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    private TermsMapper() {
    }

    public static PlanTerms fromRequest(SavePlanRequest r) {
        return PlanTerms.of(
                PlanEnums.parse(Modality.class, r.modality(), "La modalidad"),
                r.currency(),
                r.monthlyFee(),
                r.commissionPercent(),
                r.fixedFeePerSale(),
                optional(CommissionBase.class, r.commissionBase(), "La base de comisión"),
                Boolean.TRUE.equals(r.includeShipping()),
                Boolean.TRUE.equals(r.includeTaxes()),
                optional(AccrualEvent.class, r.accrualEvent(), "El momento de exigibilidad"),
                r.returnWindowDays(),
                optional(RefundPolicy.class, r.refundPolicy(), "La política de ajustes"),
                optional(FeeBilling.class, r.feeBilling(), "La facturación de la mensualidad"),
                r.graceDays(),
                r.lateInterestMonthlyPercent(),
                r.storageFeePerUnitDay());
    }

    public static TermsDto toDto(PlanTerms t) {
        return new TermsDto(t.modality().name(), t.currency(), t.monthlyFee(), t.commissionPercent(), t.fixedFeePerSale(),
                t.commissionBase().name(), t.includeShipping(), t.includeTaxes(), t.accrualEvent().name(),
                t.returnWindowDays(), t.refundPolicy().name(), t.feeBilling().name(), t.graceDays(),
                t.lateInterestMonthlyPercent(), t.storageFeePerUnitDay());
    }

    public static PlanTerms fromDto(TermsDto d) {
        return PlanTerms.of(Modality.valueOf(d.modality()), d.currency(), d.monthlyFee(), d.commissionPercent(),
                d.fixedFeePerSale(), CommissionBase.valueOf(d.commissionBase()), d.includeShipping(), d.includeTaxes(),
                AccrualEvent.valueOf(d.accrualEvent()), d.returnWindowDays(), RefundPolicy.valueOf(d.refundPolicy()),
                FeeBilling.valueOf(d.feeBilling()), d.graceDays(), d.lateInterestMonthlyPercent(),
                d.storageFeePerUnitDay()); // null en condiciones guardadas antes de V24 = sin cobro
    }

    public static String toJson(PlanTerms terms) {
        try {
            return JSON.writeValueAsString(toDto(terms));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudieron serializar las condiciones del plan", ex);
        }
    }

    public static TermsDto fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, TermsDto.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Condiciones guardadas ilegibles", ex);
        }
    }

    private static <E extends Enum<E>> E optional(Class<E> type, String value, String label) {
        return value == null || value.isBlank() ? null : PlanEnums.parse(type, value, label);
    }
}
