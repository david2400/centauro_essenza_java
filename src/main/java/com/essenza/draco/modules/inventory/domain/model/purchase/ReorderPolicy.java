package com.essenza.draco.modules.inventory.domain.model.purchase;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Sugerencia de compra para un SKU con stock bajo en una bodega.
 *
 * <ul>
 *   <li>Aplica cuando hay mínimo (&gt; 0) y el disponible está en el mínimo o por debajo.</li>
 *   <li>Se repone hasta el doble del mínimo, descontando lo que ya viene en órdenes abiertas
 *       (borrador, enviada o recibida en parte) hacia esa bodega.</li>
 *   <li>Nunca menos que la cantidad mínima del proveedor.</li>
 *   <li>Proveedor sugerido: el preferido; si no hay, el de menor costo pactado; a igual costo, el de menor id.</li>
 * </ul>
 */
public final class ReorderPolicy {

    private ReorderPolicy() {
    }

    public static boolean isLow(int available, int minThreshold) {
        return minThreshold > 0 && available <= minThreshold;
    }

    /** @return unidades a pedir (0 si no hace falta) */
    public static int suggestedQuantity(int available, int minThreshold, int onOrder, Integer supplierMinQty) {
        if (!isLow(available, minThreshold)) {
            return 0;
        }
        long need = 2L * minThreshold - Math.max(available, 0) - Math.max(onOrder, 0);
        if (need <= 0) {
            return 0;
        }
        int min = supplierMinQty == null || supplierMinQty < 1 ? 1 : supplierMinQty;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(need, min));
    }

    public static <T extends Candidate> Optional<T> pickSupplier(List<T> candidates) {
        return candidates.stream().min(Comparator.comparing((T c) -> !c.preferred())
                .thenComparing(c -> c.unitCost() == null ? BigDecimal.valueOf(Long.MAX_VALUE) : c.unitCost())
                .thenComparingLong(Candidate::supplierId));
    }

    public interface Candidate {
        long supplierId();

        boolean preferred();

        BigDecimal unitCost();
    }
}
