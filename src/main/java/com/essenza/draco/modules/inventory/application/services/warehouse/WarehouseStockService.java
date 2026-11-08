package com.essenza.draco.modules.inventory.application.services.warehouse;

import com.essenza.draco.modules.inventory.application.dto.warehouse.UpdateMinThresholdDto;
import com.essenza.draco.modules.inventory.application.dto.warehouse.WarehouseLinksDto;
import com.essenza.draco.modules.inventory.application.dto.warehouse.WarehouseMovementDto;
import com.essenza.draco.modules.inventory.application.dto.warehouse.WarehouseStockDto;
import com.essenza.draco.modules.inventory.application.input.warehouse.WarehouseStockUseCase;
import com.essenza.draco.modules.inventory.application.output.repository.SkuCatalogPort;
import com.essenza.draco.modules.inventory.application.output.repository.StockItemRepository;
import com.essenza.draco.modules.inventory.application.output.repository.WarehouseInsights;
import com.essenza.draco.modules.inventory.application.dto.SkuRef;
import com.essenza.draco.modules.inventory.domain.model.StockItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Existencias valorizadas, mínimos por SKU, movimientos recientes y tiendas o cajas vinculadas de una bodega. */
@Service
@Transactional(readOnly = true)
public class WarehouseStockService implements WarehouseStockUseCase {

    private final WarehouseInsights insights;
    private final StockItemRepository stockItems;
    private final SkuCatalogPort skus;
    private final WarehouseSoporte soporte;

    public WarehouseStockService(WarehouseInsights insights, StockItemRepository stockItems, SkuCatalogPort skus,
                                 WarehouseSoporte soporte) {
        this.insights = insights;
        this.stockItems = stockItems;
        this.skus = skus;
        this.soporte = soporte;
    }

    @Override
    public Page<WarehouseStockDto> stock(Long warehouseId, String q, boolean belowMinOnly, Pageable pageable) {
        soporte.warehouse(warehouseId);
        return insights.stock(warehouseId, q, belowMinOnly, pageable).map(WarehouseSoporte::stockDto);
    }

    @Override
    @Transactional
    public WarehouseStockDto updateMin(Long warehouseId, Long skuId, UpdateMinThresholdDto input) {
        soporte.warehouse(warehouseId);
        SkuRef sku = skus.resolve(null, skuId);
        StockItem item = stockItems.lockOrEmpty(sku.skuId(), sku.productId(), warehouseId);
        item.changeMinThreshold(input.minThreshold());
        stockItems.save(item);
        return insights.line(warehouseId, sku.skuId()).map(WarehouseSoporte::stockDto).orElseThrow();
    }

    @Override
    public List<WarehouseMovementDto> movements(Long warehouseId, int limit) {
        soporte.warehouse(warehouseId);
        return insights.movements(warehouseId, Math.max(1, Math.min(limit, 100))).stream().map(m -> {
            boolean in = warehouseId.equals(m.toWarehouseId());
            return new WarehouseMovementDto(m.id(), m.type(), in ? "IN" : "OUT", m.skuId(), m.skuCode(), m.productName(),
                    m.quantity(), in ? m.fromName() : m.toName(), m.reason(), m.referenceType(), m.referenceId(),
                    m.createdAt());
        }).toList();
    }

    @Override
    public WarehouseLinksDto links(Long warehouseId) {
        soporte.warehouse(warehouseId);
        return new WarehouseLinksDto(
                insights.stores(warehouseId).stream()
                        .map(s -> new WarehouseLinksDto.LinkedStore(s.id(), s.code(), s.name(), s.active())).toList(),
                insights.registers(warehouseId).stream()
                        .map(r -> new WarehouseLinksDto.LinkedRegister(r.id(), r.name(), r.storeName(), r.active(), r.own()))
                        .toList());
    }
}
