package com.essenza.draco.modules.inventory.application.input.stock;

import com.essenza.draco.modules.inventory.application.dto.InventoryMovementDto;

public interface AlignStockUseCase {

    /** Ajusta el total del SKU a {@code desiredOnHand}; devuelve el movimiento o null si no hizo falta. */
    InventoryMovementDto align(Long productId, Long skuId, int desiredOnHand, String source);
}
