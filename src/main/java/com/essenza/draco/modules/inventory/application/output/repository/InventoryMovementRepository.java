package com.essenza.draco.modules.inventory.application.output.repository;

import com.essenza.draco.modules.inventory.application.dto.InventoryMovementDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InventoryMovementRepository {
    InventoryMovementDto save(InventoryMovementDto dto);
    Page<InventoryMovementDto> findAll(Pageable pageable);
}
