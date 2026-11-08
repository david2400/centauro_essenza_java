package com.essenza.draco.modules.inventory.application.output.repository;

import java.math.BigDecimal;
import java.time.Instant;

/** Costo promedio por SKU del inventario propio (JDBC, misma transacción que la recepción). */
public interface SkuCostStore {

    /** Crea la fila del SKU si no existe y la bloquea: serializa las recepciones del mismo SKU. */
    CostRow lock(long skuId, long productId, Instant now);

    /** Unidades físicas del SKU en todas las bodegas propias. */
    long unitsOnHand(long skuId);

    void save(long skuId, BigDecimal averageCost, BigDecimal lastCost, long receiptId, Instant now);

    void addHistory(long skuId, long receiptId, long unitsBefore, BigDecimal averageBefore, int quantity, BigDecimal unitCost,
                    BigDecimal averageAfter, Instant now);

    /** {@code catalogCost}: costo del SKU en el catálogo (se usa si aún no hay promedio). */
    record CostRow(long skuId, BigDecimal averageCost, BigDecimal lastCost, BigDecimal catalogCost) {
    }
}
