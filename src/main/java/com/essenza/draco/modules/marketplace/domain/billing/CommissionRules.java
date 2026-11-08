package com.essenza.draco.modules.marketplace.domain.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import com.essenza.draco.modules.marketplace.domain.plan.Money;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.AccrualEvent;

/**
 * Ciclo de vida de la comisión de una venta (subpedido de un vendedor externo).
 * <pre>
 * PENDING (venta pagada, aún no exigible) → ACCRUED (exigible: entra al estado de cuenta)
 * PENDING → VOIDED (la venta se cancela antes de ser exigible: no se cobra nada)
 * REVIEW: no se pudo calcular (sin plan vigente o moneda distinta); lo resuelve una persona.
 * </pre>
 * Las devoluciones posteriores reversan según la política del plan: antes de ser exigible
 * reducen la comisión; después, generan un abono en el estado de cuenta.
 */
public final class CommissionRules {

    public enum Status {
        PENDING, ACCRUED, VOIDED, REVIEW
    }

    private CommissionRules() {
    }

    /**
     * Fecha en que la comisión pasa a ser exigible; {@code null} si aún falta el hecho que la
     * dispara (la entrega).
     */
    public static LocalDate accrueOn(AccrualEvent event, LocalDate paidOn, LocalDate deliveredOn, int returnWindowDays) {
        if (event == null || paidOn == null) {
            return null;
        }
        return switch (event) {
            case PAYMENT_CONFIRMED -> paidOn;
            case ORDER_DELIVERED -> deliveredOn;
            case RETURN_WINDOW_CLOSED -> deliveredOn == null ? null : deliveredOn.plusDays(Math.max(returnWindowDays, 0));
        };
    }

    /** Ya es exigible en {@code today}. */
    public static boolean isDue(LocalDate accrueOn, LocalDate today) {
        return accrueOn != null && !accrueOn.isAfter(today);
    }

    /**
     * Parte de la base de comisión que corresponde a un reembolso: lo reembolsado (con impuestos)
     * en proporción a lo que pagó el comprador. Nunca supera la base.
     */
    public static BigDecimal refundedBase(BigDecimal refunded, BigDecimal buyerTotal, BigDecimal commissionBase,
                                          String currency) {
        if (refunded == null || refunded.signum() <= 0 || buyerTotal == null || buyerTotal.signum() <= 0
                || commissionBase == null || commissionBase.signum() <= 0) {
            return Money.zero(currency);
        }
        if (refunded.compareTo(buyerTotal) >= 0) {
            return Money.round(commissionBase, currency);
        }
        BigDecimal ratio = refunded.divide(buyerTotal, 10, RoundingMode.HALF_UP);
        return Money.round(commissionBase.multiply(ratio), currency).min(commissionBase);
    }
}
