package com.essenza.draco.modules.inventory.application.input.movement;

import com.essenza.draco.modules.inventory.application.dto.InventoryMovementDto;

public interface RegisterEntryUseCase {
    InventoryMovementDto registerEntry(InventoryMovementDto dto);
}
