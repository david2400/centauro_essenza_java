package com.essenza.draco.modules.sales.application.dto.cash;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OpenCashSessionDto(
        @NotNull Long cashRegisterId,
        @Size(max = 64) @Schema(description = "Usuario de access_control que atiende (GET /sales/cash_registers/cashiers). "
                + "Vacío: el usuario de la sesión. El nombre del turno lo pone el servidor.") String cashierUserId,
        @DecimalMin("0") @Schema(description = "Base en efectivo") BigDecimal openingAmount,
        @Size(max = 500) String notes) {
}
