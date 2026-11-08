package com.essenza.draco.modules.inventory.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Existencias de un SKU en una bodega. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockItemDto {
    private Long id;
    private Long skuId;
    private Long productId;
    private Long warehouseId;
    private Integer onHand;
    private Integer reserved;
    private Integer available;
    private Integer minThreshold;
    private Boolean belowThreshold;
}
