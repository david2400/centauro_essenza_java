package com.essenza.draco.modules.inventory.application.output.repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Entrada al inventario propio de lo recibido de una orden de compra (kardex "PURCHASE_RECEIPT").
 * Con {@code unitCost} recalcula además el costo promedio del SKU.
 */
public interface PurchaseStockEntry {

    void receive(long receiptId, String receiptNumber, long warehouseId, List<Entry> entries);

    record Entry(long productId, long skuId, int quantity, BigDecimal unitCost) {

        public Entry(long productId, long skuId, int quantity) {
            this(productId, skuId, quantity, null);
        }
    }
}
