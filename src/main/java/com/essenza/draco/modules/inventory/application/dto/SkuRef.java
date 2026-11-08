package com.essenza.draco.modules.inventory.application.dto;

/** Referencia mínima a un SKU del catálogo (id del SKU y de su producto). */
public record SkuRef(Long skuId, Long productId) {
}
