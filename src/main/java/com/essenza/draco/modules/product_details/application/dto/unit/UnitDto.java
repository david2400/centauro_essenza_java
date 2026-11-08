package com.essenza.draco.modules.product_details.application.dto.unit;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Unidad de medida.
 *
 * @param factor     unidades base de su magnitud que equivale una unidad (base = 1)
 * @param baseSymbol símbolo de la unidad base de la magnitud (para mostrar "1 g = 0,001 kg")
 * @param usageCount atributos, productos y variantes que la usan
 */
public record UnitDto(
        Long id,
        String code,
        String symbol,
        String name,
        @Schema(allowableValues = {"LENGTH", "MASS", "VOLUME", "AREA", "COUNT", "OTHER"}) String dimension,
        BigDecimal factor,
        boolean base,
        String baseSymbol,
        int decimals,
        boolean active,
        long usageCount) {
}
