package com.essenza.draco.modules.inventory.application.output.repository;

import com.essenza.draco.modules.inventory.application.dto.StockPerWarehouseDto;

import java.util.Optional;

public interface StockPerWarehouseRepository {
    Optional<StockPerWarehouseDto> findByProductIdAndWarehouseId(Long productId, Long warehouseId);
    StockPerWarehouseDto save(StockPerWarehouseDto dto);

    long totalQuantityInWarehouse(Long warehouseId);
}
