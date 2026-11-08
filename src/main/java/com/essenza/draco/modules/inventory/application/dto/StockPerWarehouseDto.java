package com.essenza.draco.modules.inventory.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StockPerWarehouseDto {
    private Long id;
    @NotNull
    private Long productId;
    @NotNull
    private Long warehouseId;
    @NotNull 
    @Min(0)
    private Integer quantity;
    @NotNull 
    @Min(0)
    private Integer minThreshold;
}
