package com.essenza.draco.modules.inventory.infrastructure.outbound.repositories.stock;

import com.essenza.draco.modules.inventory.application.output.repository.StockPerWarehouseRepository;
import com.essenza.draco.modules.inventory.application.dto.StockPerWarehouseDto;
import com.essenza.draco.modules.inventory.infrastructure.outbound.mappers.StockPerWarehouseMapper;
import com.essenza.draco.modules.inventory.infrastructure.outbound.persistence.mysql.shop.StockPerWarehouseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StockPerWarehouseRepositoryAdapter implements StockPerWarehouseRepository {

    private final JpaStockPerWarehouseRepository jpa;
    private final StockPerWarehouseMapper mapper;

    @Override
    public Optional<StockPerWarehouseDto> findByProductIdAndWarehouseId(Long productId, Long warehouseId) {
        return jpa.findByProductIdAndWarehouseId(productId, warehouseId).map(mapper::toDto);
    }

    @Override
    @Transactional
    public StockPerWarehouseDto save(StockPerWarehouseDto dto) {
        StockPerWarehouseEntity entity = mapper.toEntity(dto);
        StockPerWarehouseEntity saved = jpa.save(entity);
        return mapper.toDto(saved);
    }

    /** Unidades totales almacenadas en una bodega. */
    public long totalQuantityInWarehouse(Long warehouseId) {
        return jpa.sumQuantityByWarehouseId(warehouseId);
    }
}
