package com.essenza.draco.modules.inventory.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InventoryMovementDto {
    /** Id del movimiento (solo lectura). */
    private Long id;
    /** Producto. Opcional si se envía {@code skuId}. */
    private Long productId;
    /** SKU (variante). Obligatorio si el producto tiene varias variantes. */
    private Long skuId;
    private Long fromWarehouseId;
    private Long toWarehouseId;
    @NotNull
    private String type; // ENTRY, EXIT, TRANSFER
    @NotNull
    @Min(1)
    private Integer quantity;
    private String reason;
    /** Origen del movimiento: MANUAL, PRODUCT_EDIT, MIGRATION… (solo lectura). */
    private String referenceType;
    private Long referenceId;
    /** Fecha de registro (solo lectura). */
    private java.time.Instant createdAt;
}
