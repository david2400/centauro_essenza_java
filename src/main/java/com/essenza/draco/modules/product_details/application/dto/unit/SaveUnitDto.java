package com.essenza.draco.modules.product_details.application.dto.unit;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta / edición de unidad. Solo {@code name} es obligatorio (compatibilidad con el
 * formulario antiguo): el código se deriva del símbolo o del nombre, la magnitud por
 * defecto es OTHER y el factor 1.
 *
 * @param factor cuántas unidades base de la magnitud equivale (1 g = 0.001 kg)
 * @param base   convertirla en la unidad base de su magnitud (reescala las demás)
 */
public record SaveUnitDto(
        @Size(max = 20) String code,
        @Size(max = 20) String symbol,
        @NotBlank @Size(max = 255) String name,
        @Schema(allowableValues = {"LENGTH", "MASS", "VOLUME", "AREA", "COUNT", "OTHER"}) String dimension,
        @DecimalMin(value = "0", inclusive = false) BigDecimal factor,
        Boolean base,
        @Min(0) @Max(6) Integer decimals,
        Boolean active) {
}
