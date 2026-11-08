package com.essenza.draco.modules.inventory.application.input.movement;

import com.essenza.draco.modules.inventory.application.dto.InventoryMovementDto;

public interface RegisterTransferUseCase {
    InventoryMovementDto registerTransfer(InventoryMovementDto dto);
}
