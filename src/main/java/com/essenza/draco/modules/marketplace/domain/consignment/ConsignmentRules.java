package com.essenza.draco.modules.marketplace.domain.consignment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;

/**
 * Consignación: mercancía de un vendedor externo custodiada en una bodega propia. El vendedor es
 * el propietario comercial; la bodega es el custodio y la ubicación. Este stock nunca se mezcla
 * con el inventario propio: se lleva en su propio kardex por vendedor, SKU y bodega.
 */
public final class ConsignmentRules {

    public static final int MAX_LINES = 200;
    public static final int MAX_QUANTITY = 100_000;

    private ConsignmentRules() {
    }

    /** Documentos que registra una persona. Las ventas y devoluciones de clientes las genera el sistema. */
    public enum DocumentKind {
        RECEIPT, RETURN_TO_SELLER, ADJUSTMENT;

        public static DocumentKind parse(String value) {
            if (value == null || value.isBlank()) {
                throw new MarketplaceRuleViolation("Indica el tipo de documento (RECEIPT, RETURN_TO_SELLER o ADJUSTMENT).");
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new MarketplaceRuleViolation("Tipo de documento no válido: " + value
                        + " (RECEIPT, RETURN_TO_SELLER o ADJUSTMENT).");
            }
        }
    }

    /** Movimientos del kardex de consignación (cantidad con signo). */
    public enum MovementKind {
        RECEIPT, RETURN_TO_SELLER, ADJUSTMENT, SALE, SALE_CANCEL, CUSTOMER_RETURN
    }

    public record Line(long skuId, int quantity) {
    }

    /**
     * Valida las líneas de una recepción o devolución al vendedor: al menos una, sin SKU repetido,
     * cantidades entre 1 y {@link #MAX_QUANTITY}.
     */
    public static List<Line> checkLines(List<Line> lines) {
        MarketplaceRuleViolation.check(lines != null && !lines.isEmpty(), "Agrega al menos un SKU con su cantidad.");
        MarketplaceRuleViolation.check(lines.size() <= MAX_LINES, "Un documento admite hasta " + MAX_LINES + " SKU.");
        Set<Long> seen = new HashSet<>();
        for (Line line : lines) {
            MarketplaceRuleViolation.check(line.quantity() >= 1 && line.quantity() <= MAX_QUANTITY,
                    "La cantidad de cada SKU debe estar entre 1 y " + MAX_QUANTITY + ".");
            MarketplaceRuleViolation.check(seen.add(line.skuId()), "El SKU " + line.skuId() + " está repetido: une las cantidades.");
        }
        return List.copyOf(lines);
    }

    /**
     * Diferencia de un conteo físico.
     *
     * @return unidades a sumar (positivo) o restar (negativo); nunca 0
     */
    public static int adjustment(int onHand, int counted, int reservedForSales, String reason) {
        MarketplaceRuleViolation.check(counted >= 0 && counted <= MAX_QUANTITY,
                "El conteo debe estar entre 0 y " + MAX_QUANTITY + ".");
        MarketplaceRuleViolation.check(reason != null && reason.trim().length() >= 5,
                "Explica el ajuste (al menos 5 caracteres).");
        int delta = counted - onHand;
        MarketplaceRuleViolation.check(delta != 0, "El conteo coincide con el saldo: no hay nada que ajustar.");
        MarketplaceRuleViolation.check(delta > 0 || -delta <= Math.max(0, onHand - reservedForSales),
                "No se puede bajar a " + counted + ": hay " + reservedForSales + " unidades reservadas en ventas pendientes.");
        return delta;
    }

    /** Saldo de un vendedor y SKU en una bodega. */
    public record Bin(long stockId, long warehouseId, int onHand) {
    }

    public record Pick(long stockId, long warehouseId, int quantity) {
    }

    /**
     * De qué bodegas sale una venta: primero la que más tiene (menos viajes), con empate por id.
     *
     * @throws MarketplaceRuleViolation si el saldo físico no alcanza
     */
    public static List<Pick> allocate(List<Bin> bins, int quantity) {
        MarketplaceRuleViolation.check(quantity >= 1, "La cantidad debe ser mayor que cero.");
        int total = bins.stream().mapToInt(Bin::onHand).sum();
        MarketplaceRuleViolation.check(total >= quantity, "La mercancía en consignación no alcanza: hay " + total
                + " unidades y la venta pide " + quantity + ". Revisa el conteo de la bodega.");
        List<Bin> ordered = new ArrayList<>(bins);
        ordered.sort(Comparator.comparingInt(Bin::onHand).reversed().thenComparingLong(Bin::stockId));
        List<Pick> picks = new ArrayList<>();
        int pending = quantity;
        for (Bin bin : ordered) {
            if (pending == 0) {
                break;
            }
            int take = Math.min(pending, bin.onHand());
            if (take > 0) {
                picks.add(new Pick(bin.stockId(), bin.warehouseId(), take));
                pending -= take;
            }
        }
        return picks;
    }

    /** Número visible del documento. */
    public static String number(long id) {
        return String.format("CSG-%06d", id);
    }
}
