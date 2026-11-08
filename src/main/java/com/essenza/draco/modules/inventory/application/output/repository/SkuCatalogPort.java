package com.essenza.draco.modules.inventory.application.output.repository;

import com.essenza.draco.modules.inventory.application.dto.SkuRef;

/** Resuelve el SKU de un movimiento a partir del producto y/o del SKU indicado. */
public interface SkuCatalogPort {

    SkuRef resolve(Long productId, Long skuId);
}
