package com.essenza.draco.modules.marketplace.domain.billing;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.FeeBilling;

/**
 * Ciclo mensual de la mensualidad (AAAA-MM). Sin prorrateo: cobra el ciclo la vigencia que
 * está en curso el día de cobro. Por adelantado se cobra el día 1; vencido, el último día.
 */
public final class FeeCycle {

    private FeeCycle() {
    }

    public static YearMonth parse(String value) {
        try {
            return YearMonth.parse(value == null ? "" : value.trim());
        } catch (DateTimeParseException ex) {
            throw new MarketplaceRuleViolation("El ciclo debe tener el formato AAAA-MM.");
        }
    }

    public static LocalDate chargeDate(YearMonth cycle, FeeBilling billing) {
        return billing == FeeBilling.IN_ARREARS ? cycle.atEndOfMonth() : cycle.atDay(1);
    }

    /** Un ciclo se genera cuando llega su día de cobro (nunca por anticipado al calendario). */
    public static boolean chargeable(YearMonth cycle, FeeBilling billing, LocalDate today) {
        return !chargeDate(cycle, billing).isAfter(today);
    }

    /** Clave idempotente: un solo cobro por vendedor y ciclo. */
    public static String key(long sellerId, YearMonth cycle) {
        return "FEE:" + sellerId + ":" + cycle;
    }
}
