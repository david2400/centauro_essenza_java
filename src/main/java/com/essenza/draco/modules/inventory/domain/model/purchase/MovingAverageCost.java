package com.essenza.draco.modules.inventory.domain.model.purchase;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Costo promedio ponderado móvil de un SKU (inventario propio, todas las bodegas).
 *
 * <p>Al recibir {@code q} unidades a costo {@code c} con {@code u} unidades ya en existencia a
 * promedio {@code a}: nuevo promedio = (u·a + q·c) / (u + q). Las salidas no cambian el promedio.
 * <ul>
 *   <li>Sin existencias previas (u ≤ 0): el promedio pasa a ser el costo de la compra.</li>
 *   <li>Sin promedio previo: las unidades existentes se valoran al costo del catálogo; si no lo hay,
 *       al costo de la compra.</li>
 * </ul>
 * Cuatro decimales, redondeo HALF_UP.
 */
public final class MovingAverageCost {

    public static final int SCALE = 4;

    private MovingAverageCost() {
    }

    public static BigDecimal next(BigDecimal currentAverage, BigDecimal catalogCost, long unitsBefore, int quantity, BigDecimal unitCost) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad recibida debe ser mayor que cero");
        }
        if (unitCost == null || unitCost.signum() < 0) {
            throw new IllegalArgumentException("El costo de la compra no puede ser vacío ni negativo");
        }
        if (unitsBefore <= 0) {
            return unitCost.setScale(SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal base = currentAverage != null ? currentAverage : catalogCost != null && catalogCost.signum() >= 0 ? catalogCost : unitCost;
        BigDecimal value = base.multiply(BigDecimal.valueOf(unitsBefore)).add(unitCost.multiply(BigDecimal.valueOf(quantity)));
        return value.divide(BigDecimal.valueOf(unitsBefore + quantity), SCALE, RoundingMode.HALF_UP);
    }
}
