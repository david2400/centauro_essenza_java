package com.essenza.draco.modules.inventory.domain.model.purchase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orden de compra a un proveedor y su recepción en bodega.
 *
 * <pre>
 * DRAFT ──SEND──▶ SENT ──RECEIVE──▶ PARTIALLY_RECEIVED ──RECEIVE──▶ RECEIVED
 *   │               │                       │
 *   └──CANCEL───────┴──▶ CANCELLED          └──CLOSE──▶ CLOSED (lo pendiente ya no llega)
 * </pre>
 * Solo el borrador se edita. Cada recepción suma al inventario propio de la bodega destino.
 */
public final class PurchaseOrderRules {

    public static final int MAX_LINES = 200;
    public static final int MAX_QUANTITY = 100_000;
    public static final BigDecimal MAX_COST = new BigDecimal("1000000000");

    private PurchaseOrderRules() {
    }

    public enum Status {
        DRAFT, SENT, PARTIALLY_RECEIVED, RECEIVED, CANCELLED, CLOSED;

        public boolean receivable() {
            return this == SENT || this == PARTIALLY_RECEIVED;
        }

        public boolean closed() {
            return this == RECEIVED || this == CANCELLED || this == CLOSED;
        }
    }

    /** Línea pedida: costo unitario sin impuestos. */
    public record Line(long skuId, int quantity, BigDecimal unitCost) {
    }

    public static List<Line> checkLines(List<Line> lines) {
        violation(lines != null && !lines.isEmpty(), "Agrega al menos un producto a la orden.");
        violation(lines.size() <= MAX_LINES, "Una orden de compra admite hasta " + MAX_LINES + " productos.");
        Set<Long> seen = new HashSet<>();
        List<Line> clean = new ArrayList<>();
        for (Line line : lines) {
            violation(line.quantity() >= 1 && line.quantity() <= MAX_QUANTITY,
                    "La cantidad de cada producto debe estar entre 1 y " + MAX_QUANTITY + ".");
            violation(seen.add(line.skuId()), "El SKU " + line.skuId() + " está repetido: une las cantidades.");
            violation(line.unitCost() != null && line.unitCost().signum() >= 0 && line.unitCost().compareTo(MAX_COST) < 0,
                    "El costo unitario del SKU " + line.skuId() + " no es válido.");
            violation(line.unitCost().scale() <= 2 || line.unitCost().stripTrailingZeros().scale() <= 2,
                    "El costo unitario admite hasta 2 decimales.");
            clean.add(new Line(line.skuId(), line.quantity(), line.unitCost().setScale(2, RoundingMode.HALF_UP)));
        }
        return List.copyOf(clean);
    }

    public static BigDecimal total(List<Line> lines) {
        return lines.stream().map(l -> l.unitCost().multiply(BigDecimal.valueOf(l.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    public static void checkEditable(Status status) {
        violation(status == Status.DRAFT, "Solo se edita una orden en borrador (esta está " + status + ").");
    }

    /** Enviar al proveedor: activo y con el pedido mínimo cubierto. */
    public static void checkSend(Status status, boolean supplierActive, BigDecimal total, BigDecimal minOrderAmount) {
        violation(status == Status.DRAFT, "Solo se envía una orden en borrador.");
        violation(supplierActive, "El proveedor no está activo: no se le pueden enviar órdenes.");
        violation(minOrderAmount == null || minOrderAmount.signum() <= 0 || total.compareTo(minOrderAmount) >= 0,
                "El proveedor pide un mínimo de " + (minOrderAmount == null ? "0" : minOrderAmount.stripTrailingZeros().toPlainString())
                        + " por orden.");
    }

    /** Pendiente por recibir de una línea. */
    public record Pending(long lineId, String label, int ordered, int received) {
        public int pending() {
            return ordered - received;
        }
    }

    /**
     * Valida una recepción.
     *
     * @param receiving cantidades por línea
     * @return el estado que queda
     */
    public static Status checkReceive(Status status, List<Pending> lines, Map<Long, Integer> receiving) {
        violation(status.receivable(), status == Status.DRAFT ? "Envía la orden al proveedor antes de recibir."
                : "La orden está " + status + ": ya no recibe mercancía.");
        violation(receiving != null && receiving.values().stream().anyMatch(q -> q != null && q > 0),
                "Indica al menos una cantidad recibida.");
        Set<Long> known = new HashSet<>();
        boolean complete = true;
        for (Pending line : lines) {
            known.add(line.lineId());
            int qty = receiving.getOrDefault(line.lineId(), 0) == null ? 0 : receiving.getOrDefault(line.lineId(), 0);
            violation(qty >= 0, "Las cantidades recibidas no pueden ser negativas.");
            violation(qty <= line.pending(), line.label() + ": quedan " + line.pending() + " por recibir, no " + qty + ".");
            if (line.received() + qty < line.ordered()) {
                complete = false;
            }
        }
        for (Long lineId : receiving.keySet()) {
            violation(known.contains(lineId), "La línea " + lineId + " no es de esta orden.");
        }
        return complete ? Status.RECEIVED : Status.PARTIALLY_RECEIVED;
    }

    public static void checkCancel(Status status, boolean hasReceipts, String reason) {
        violation(status == Status.DRAFT || status == Status.SENT, "Solo se cancela una orden en borrador o enviada.");
        violation(!hasReceipts, "La orden ya tiene recepciones: ciérrala en lugar de cancelarla.");
        violation(reason != null && reason.trim().length() >= 5, "Indica el motivo (al menos 5 caracteres).");
    }

    public static void checkClose(Status status, String reason) {
        violation(status == Status.PARTIALLY_RECEIVED, "Solo se cierra una orden recibida en parte.");
        violation(reason != null && reason.trim().length() >= 5, "Indica por qué no llegará lo pendiente (al menos 5 caracteres).");
    }

    public static String orderNumber(long id) {
        return String.format("OC-%06d", id);
    }

    public static String receiptNumber(long id) {
        return String.format("REC-%06d", id);
    }

    private static void violation(boolean ok, String message) {
        if (!ok) {
            throw new PurchaseRuleViolation(message);
        }
    }

    /** Regla de negocio incumplida (400 con el mensaje, como las demás reglas). */
    public static class PurchaseRuleViolation extends IllegalArgumentException {
        public PurchaseRuleViolation(String message) {
            super(message);
        }
    }
}
