package com.essenza.draco.modules.inventory.application.input.movement;

import com.essenza.draco.modules.inventory.application.dto.InventoryMovementDto;

public interface RegisterExitUseCase {
    InventoryMovementDto registerExit(InventoryMovementDto dto);
}
