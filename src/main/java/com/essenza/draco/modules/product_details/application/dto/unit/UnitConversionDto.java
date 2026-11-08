package com.essenza.draco.modules.product_details.application.dto.unit;

import java.math.BigDecimal;

/** Resultado de convertir {@code value} de {@code from} a {@code to}. */
public record UnitConversionDto(BigDecimal value, String from, String fromSymbol, String to, String toSymbol, BigDecimal result) {
}
