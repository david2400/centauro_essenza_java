package com.essenza.draco.modules.inventory.application.output.repository;

import com.essenza.draco.modules.inventory.application.dto.StockItemDto;
import com.essenza.draco.modules.inventory.domain.model.StockItem;
import java.util.List;

public interface StockItemRepository {

    /** Carga las existencias del SKU en la bodega con bloqueo de escritura (o un registro vacío). */
    StockItem lockOrEmpty(Long skuId, Long productId, Long warehouseId);

    StockItem save(StockItem item);

    /** Existencias del SKU en todas las bodegas, bloqueadas en orden de bodega. */
    List<StockItem> lockAllForSku(Long skuId);

    /** Unidades físicas del SKU en todas las bodegas. */
    int totalOnHand(Long skuId);

    /** Unidades físicas en la bodega (solo SKUs vigentes). */
    long totalOnHandInWarehouse(Long warehouseId);

    List<StockItemDto> find(Long skuId, Long productId, Long warehouseId);
}
