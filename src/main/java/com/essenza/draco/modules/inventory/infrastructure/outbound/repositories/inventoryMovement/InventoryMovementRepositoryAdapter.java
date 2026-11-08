package com.essenza.draco.modules.inventory.infrastructure.outbound.repositories.inventoryMovement;

import com.essenza.draco.modules.inventory.application.output.repository.InventoryMovementRepository;
import com.essenza.draco.modules.inventory.application.dto.InventoryMovementDto;
import com.essenza.draco.modules.inventory.infrastructure.outbound.mappers.InventoryMovementMapper;
import com.essenza.draco.modules.inventory.infrastructure.outbound.persistence.mysql.shop.InventoryMovementEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class InventoryMovementRepositoryAdapter implements InventoryMovementRepository {

    private final JpaInventoryMovementRepository jpa;
    private final InventoryMovementMapper mapper;

    @Override
    @Transactional
    public InventoryMovementDto save(InventoryMovementDto dto) {
        InventoryMovementEntity entity = mapper.toEntity(dto);
        InventoryMovementEntity saved = jpa.save(entity);
        return mapper.toDto(saved);
    }

    @Override
    public Page<InventoryMovementDto> findAll(Pageable pageable) {
        return jpa.findAll(pageable).map(mapper::toDto);
    }
}
