package com.essenza.draco.modules.marketplace.domain.plan;

import java.util.Locale;

import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;

/** Enumeraciones de los planes comerciales. */
public final class PlanEnums {

    private PlanEnums() {
    }

    /** Modalidad de cobro al vendedor. */
    public enum Modality {
        /** Solo comisión por venta (porcentaje y/o cargo fijo). */
        COMMISSION,
        /** Solo mensualidad. */
        SUBSCRIPTION,
        /** Mensualidad más comisión. */
        MIXED;

        public boolean chargesCommission() {
            return this != SUBSCRIPTION;
        }

        public boolean chargesMonthlyFee() {
            return this != COMMISSION;
        }
    }

    /** Ciclo de vida de una versión de plan. Solo DRAFT se edita; ACTIVE es inmutable. */
    public enum Status {
        DRAFT,
        /** Se puede asignar. */
        ACTIVE,
        /** Reemplazada por una versión más nueva: quien la tiene la conserva, no se asigna de nuevo. */
        SUPERSEDED,
        /** Retirada: no se asigna; las asignaciones existentes siguen. */
        RETIRED
    }

    /** Sobre qué valor se calcula el porcentaje de comisión. */
    public enum CommissionBase {
        /** Subtotal de productos antes de descuentos. */
        ITEMS_GROSS,
        /** Subtotal de productos menos descuentos (lo que efectivamente se cobró por productos). */
        ITEMS_NET_OF_DISCOUNTS
    }

    /** Momento en que la comisión de una venta pasa a ser exigible. */
    public enum AccrualEvent {
        PAYMENT_CONFIRMED,
        ORDER_DELIVERED,
        /** Al cerrar la ventana de devolución (entrega + N días). */
        RETURN_WINDOW_CLOSED
    }

    /** Ajuste de la comisión cuando la venta se cancela o se devuelve (total o parcial). */
    public enum RefundPolicy {
        /** Se reversa la comisión completa en proporción a lo devuelto (incluye el cargo fijo). */
        REVERSE_PROPORTIONAL,
        /** Se reversa el porcentaje en proporción; el cargo fijo por venta no se devuelve. */
        KEEP_FIXED_FEE,
        /** No se reversa nada. */
        NO_REVERSAL
    }

    /** Cuándo se factura la mensualidad del ciclo. */
    public enum FeeBilling {
        IN_ADVANCE,
        IN_ARREARS
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String value, String label) {
        if (value == null || value.isBlank()) {
            throw new MarketplaceRuleViolation(label + " es obligatorio.");
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new MarketplaceRuleViolation(label + " no admite el valor '" + value + "'.");
        }
    }
}
