package com.essenza.draco.modules.inventory.application.services.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.inventory.application.output.repository.InventoryMovementRepository;
import com.essenza.draco.modules.inventory.application.output.repository.LegacyStockProjectionPort;
import com.essenza.draco.modules.inventory.application.output.repository.PurchaseStockEntry;
import com.essenza.draco.modules.inventory.application.output.repository.SkuCostStore;
import com.essenza.draco.modules.inventory.application.output.repository.StockItemRepository;
import com.essenza.draco.modules.inventory.domain.dto.InventoryMovementDto;
import com.essenza.draco.modules.inventory.domain.model.StockItem;
import com.essenza.draco.modules.inventory.domain.model.purchase.MovingAverageCost;

/**
 * Suma al stock de la bodega lo recibido de un proveedor y deja la entrada en el kardex (igual
 * que el reingreso de devoluciones). Con costo, recalcula el costo promedio ponderado del SKU
 * (todas las bodegas) antes de sumar las unidades. Se une a la transacción de la recepción.
 */
@Service
@Transactional(propagation = Propagation.REQUIRED)
public class PurchaseStockEntryService implements PurchaseStockEntry {

    static final String REFERENCE = "PURCHASE_RECEIPT";

    private final StockItemRepository stockItems;
    private final InventoryMovementRepository movements;
    private final LegacyStockProjectionPort legacyProjection;
    private final SkuCostStore costs;

    public PurchaseStockEntryService(StockItemRepository stockItems, InventoryMovementRepository movements,
                                     LegacyStockProjectionPort legacyProjection, SkuCostStore costs) {
        this.stockItems = stockItems;
        this.movements = movements;
        this.legacyProjection = legacyProjection;
        this.costs = costs;
    }

    @Override
    public void receive(long receiptId, String receiptNumber, long warehouseId, List<Entry> entries) {
        List<Entry> ordered = new ArrayList<>(entries);
        // Orden fijo por SKU al bloquear existencias (evita interbloqueos).
        ordered.sort(Comparator.comparingLong(Entry::skuId));
        Set<Long> products = new LinkedHashSet<>();
        for (Entry entry : ordered) {
            if (entry.quantity() <= 0) {
                continue;
            }
            if (entry.unitCost() != null) {
                updateAverage(receiptId, entry);
            }
            StockItem item = stockItems.lockOrEmpty(entry.skuId(), entry.productId(), warehouseId);
            item.receive(entry.quantity());
            stockItems.save(item);
            InventoryMovementDto movement = new InventoryMovementDto();
            movement.setProductId(entry.productId());
            movement.setSkuId(entry.skuId());
            movement.setToWarehouseId(warehouseId);
            movement.setType("ENTRY");
            movement.setQuantity(entry.quantity());
            movement.setReason("Recepción de compra " + receiptNumber);
            movement.setReferenceType(REFERENCE);
            movement.setReferenceId(receiptId);
            movements.save(movement);
            products.add(entry.productId());
        }
        products.forEach(legacyProjection::refresh);
    }

    /** Bloquea el costo del SKU, lee las unidades previas (antes de sumar esta entrada) y guarda el nuevo promedio. */
    private void updateAverage(long receiptId, Entry entry) {
        Instant now = Instant.now();
        SkuCostStore.CostRow row = costs.lock(entry.skuId(), entry.productId(), now);
        long unitsBefore = costs.unitsOnHand(entry.skuId());
        BigDecimal next = MovingAverageCost.next(row.averageCost(), row.catalogCost(), unitsBefore, entry.quantity(), entry.unitCost());
        costs.save(entry.skuId(), next, entry.unitCost(), receiptId, now);
        costs.addHistory(entry.skuId(), receiptId, unitsBefore, row.averageCost(), entry.quantity(), entry.unitCost(), next, now);
    }
}
