package com.essenza.draco.modules.inventory.application.input.stock;

import com.essenza.draco.modules.inventory.application.dto.StockItemDto;
import java.util.List;

public interface FindStockUseCase {

    List<StockItemDto> find(Long skuId, Long productId, Long warehouseId);
}
