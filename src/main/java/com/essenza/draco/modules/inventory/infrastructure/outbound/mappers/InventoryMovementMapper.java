package com.essenza.draco.modules.inventory.infrastructure.outbound.mappers;

import com.essenza.draco.modules.inventory.application.dto.InventoryMovementDto;
import com.essenza.draco.modules.inventory.infrastructure.outbound.persistence.mysql.shop.InventoryMovementEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface InventoryMovementMapper {
    InventoryMovementDto toDto(InventoryMovementEntity entity);
    InventoryMovementEntity toEntity(InventoryMovementDto dto);
}
