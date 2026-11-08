package com.essenza.draco.modules.inventory.application.services;

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
import com.essenza.draco.modules.inventory.application.output.repository.MainWarehousePort;
import com.essenza.draco.modules.inventory.application.output.repository.SkuCatalogPort;
import com.essenza.draco.modules.inventory.application.output.repository.StockItemRepository;
import com.essenza.draco.modules.inventory.application.output.repository.WarehouseRepository;
import com.essenza.draco.modules.inventory.application.dto.InventoryMovementDto;
import com.essenza.draco.modules.inventory.application.dto.SkuRef;
import com.essenza.draco.modules.inventory.domain.model.StockItem;
import com.essenza.draco.shared.common.inventory.StockReturns;
import com.essenza.draco.shared.exceptions.NotFoundException;

/** Reingreso al stock de lo devuelto: entrada "DEVOLUTION" en la bodega elegida o en la principal. */
@Service
@Transactional(propagation = Propagation.REQUIRED)
public class StockReturnsService implements StockReturns {

    static final String DEVOLUTION = "DEVOLUTION";

    private final StockItemRepository stockItems;
    private final SkuCatalogPort skuCatalog;
    private final MainWarehousePort mainWarehouse;
    private final WarehouseRepository warehouses;
    private final InventoryMovementRepository movements;
    private final LegacyStockProjectionPort legacyProjection;

    public StockReturnsService(StockItemRepository stockItems, SkuCatalogPort skuCatalog, MainWarehousePort mainWarehouse,
                               WarehouseRepository warehouses, InventoryMovementRepository movements,
                               LegacyStockProjectionPort legacyProjection) {
        this.stockItems = stockItems;
        this.skuCatalog = skuCatalog;
        this.mainWarehouse = mainWarehouse;
        this.warehouses = warehouses;
        this.movements = movements;
        this.legacyProjection = legacyProjection;
    }

    @Override
    public void restock(Long devolutionId, Long warehouseId, List<ReturnedUnit> units) {
        Long target = warehouseId == null ? mainWarehouse.mainWarehouseId() : warehouseId;
        if (warehouseId != null && warehouses.findById(warehouseId).isEmpty()) {
            throw new NotFoundException("No se encontró la bodega " + warehouseId + ".");
        }
        List<Resolved> resolved = new ArrayList<>();
        for (ReturnedUnit unit : units) {
            if (unit.quantity() <= 0) {
                continue;
            }
            resolved.add(new Resolved(skuCatalog.resolve(unit.productId(), unit.skuId()), unit.quantity()));
        }
        // Orden fijo por SKU al bloquear existencias (evita interbloqueos).
        resolved.sort(Comparator.comparing(r -> r.sku().skuId()));
        Set<Long> products = new LinkedHashSet<>();
        for (Resolved r : resolved) {
            StockItem item = stockItems.lockOrEmpty(r.sku().skuId(), r.sku().productId(), target);
            item.receive(r.quantity());
            stockItems.save(item);
            InventoryMovementDto movement = new InventoryMovementDto();
            movement.setProductId(r.sku().productId());
            movement.setSkuId(r.sku().skuId());
            movement.setToWarehouseId(target);
            movement.setType("ENTRY");
            movement.setQuantity(r.quantity());
            movement.setReason("Devolución #" + devolutionId);
            movement.setReferenceType(DEVOLUTION);
            movement.setReferenceId(devolutionId);
            movements.save(movement);
            products.add(r.sku().productId());
        }
        products.forEach(legacyProjection::refresh);
    }

    private record Resolved(SkuRef sku, int quantity) {
    }
}
