package com.essenza.draco.modules.inventory.infrastructure.outbound.mappers;

import com.essenza.draco.modules.inventory.application.dto.StockPerWarehouseDto;
import com.essenza.draco.modules.inventory.infrastructure.outbound.persistence.mysql.shop.StockPerWarehouseEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface StockPerWarehouseMapper {
    StockPerWarehouseDto toDto(StockPerWarehouseEntity entity);
    StockPerWarehouseEntity toEntity(StockPerWarehouseDto dto);
}
