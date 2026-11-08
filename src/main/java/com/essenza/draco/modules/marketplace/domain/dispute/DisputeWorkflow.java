package com.essenza.draco.modules.marketplace.domain.dispute;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;

/**
 * Reclamo del comprador contra el subpedido de un vendedor externo.
 *
 * <pre>
 * OPEN ──RESPOND──▶ SELLER_RESPONDED ──RESOLVE──▶ RESOLVED_BUYER | RESOLVED_SELLER
 *   │                     │
 *   ├──RESOLVE (solo con el plazo del vendedor vencido)
 *   └──CANCEL────────────┴──▶ CANCELLED
 * </pre>
 * El vendedor tiene un plazo para responder; la tienda decide. A favor del comprador hay
 * reembolso o cambio, y opcionalmente un cargo al vendedor en su estado de cuenta.
 */
public final class DisputeWorkflow {

    public static final int MAX_RESPONSE_DAYS = 30;
    private static final Set<String> DISPUTABLE_ORDER_STATES = Set.of("PAID", "PROCESSING", "READY", "SHIPPED", "DELIVERED");

    private DisputeWorkflow() {
    }

    public enum Status {
        OPEN, SELLER_RESPONDED, RESOLVED_BUYER, RESOLVED_SELLER, CANCELLED;

        public boolean closed() {
            return this == RESOLVED_BUYER || this == RESOLVED_SELLER || this == CANCELLED;
        }
    }

    public enum Reason {
        NOT_RECEIVED, DEFECTIVE, NOT_AS_DESCRIBED, WRONG_ITEM, OTHER
    }

    /** Decisión de la tienda. REJECTED = a favor del vendedor. */
    public enum Resolution {
        REFUND, REPLACEMENT, REJECTED
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        MarketplaceRuleViolation.check(value != null && !value.isBlank(), field + " es obligatorio.");
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new MarketplaceRuleViolation(field + " no es válido: " + value + ".");
        }
    }

    /** Se reclama un subpedido de vendedor externo ya pagado (no pendiente ni cancelado). */
    public static void checkCanOpen(boolean thirdParty, String orderState, boolean alreadyOpen, String description) {
        MarketplaceRuleViolation.check(thirdParty, "Los reclamos son contra subpedidos de vendedores externos; "
                + "las ventas propias se atienden con devoluciones.");
        MarketplaceRuleViolation.check(DISPUTABLE_ORDER_STATES.contains(orderState),
                "La orden está " + orderState + ": solo se reclaman órdenes pagadas.");
        MarketplaceRuleViolation.check(!alreadyOpen, "Esta orden ya tiene un reclamo abierto.");
        MarketplaceRuleViolation.check(description != null && description.trim().length() >= 10,
                "Describe el reclamo (al menos 10 caracteres).");
    }

    public static LocalDate respondBy(LocalDate openedOn, int responseDays) {
        int days = Math.max(1, Math.min(responseDays, MAX_RESPONSE_DAYS));
        return openedOn.plusDays(days);
    }

    public static void checkRespond(Status status, String response) {
        MarketplaceRuleViolation.check(status == Status.OPEN, status == Status.SELLER_RESPONDED
                ? "El vendedor ya respondió: falta la decisión de la tienda." : "El reclamo está cerrado.");
        MarketplaceRuleViolation.check(response != null && response.trim().length() >= 5,
                "Escribe la respuesta del vendedor (al menos 5 caracteres).");
    }

    /**
     * Valida la decisión.
     *
     * @return estado final
     */
    public static Status checkResolve(Status status, LocalDate respondBy, LocalDate today, Resolution resolution,
                                      BigDecimal amount, BigDecimal sellerCharge, BigDecimal orderTotal, String note) {
        MarketplaceRuleViolation.check(!status.closed(), "El reclamo ya está cerrado.");
        MarketplaceRuleViolation.check(status == Status.SELLER_RESPONDED || today.isAfter(respondBy),
                "El vendedor tiene hasta el " + respondBy + " para responder: decide después de su respuesta o al vencer el plazo.");
        MarketplaceRuleViolation.check(note != null && note.trim().length() >= 5, "Explica la decisión (al menos 5 caracteres).");
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        BigDecimal charge = sellerCharge == null ? BigDecimal.ZERO : sellerCharge;
        MarketplaceRuleViolation.check(value.signum() >= 0 && charge.signum() >= 0, "Los valores no pueden ser negativos.");
        if (resolution == Resolution.REJECTED) {
            MarketplaceRuleViolation.check(value.signum() == 0 && charge.signum() == 0,
                    "Si se rechaza el reclamo no hay reembolso ni cargo al vendedor.");
            return Status.RESOLVED_SELLER;
        }
        MarketplaceRuleViolation.check(value.compareTo(orderTotal) <= 0, "El reembolso no puede superar el total de la orden ("
                + orderTotal.stripTrailingZeros().toPlainString() + ").");
        MarketplaceRuleViolation.check(resolution != Resolution.REFUND || value.signum() > 0, "Indica el valor del reembolso.");
        MarketplaceRuleViolation.check(charge.compareTo(orderTotal) <= 0,
                "El cargo al vendedor no puede superar el total de la orden.");
        return Status.RESOLVED_BUYER;
    }

    public static void checkCancel(Status status, String reason) {
        MarketplaceRuleViolation.check(!status.closed(), "El reclamo ya está cerrado.");
        MarketplaceRuleViolation.check(reason != null && reason.trim().length() >= 5, "Indica por qué se retira (al menos 5 caracteres).");
    }

    public static boolean overdue(Status status, LocalDate respondBy, LocalDate today) {
        return status == Status.OPEN && today.isAfter(respondBy);
    }

    public static String number(long id) {
        return String.format("DSP-%06d", id);
    }
}
