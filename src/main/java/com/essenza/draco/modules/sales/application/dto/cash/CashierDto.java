package com.essenza.draco.modules.sales.application.dto.cash;

import io.swagger.v3.oas.annotations.media.Schema;

/** Persona que puede atender una caja: usuario de access_control con acceso a la aplicación en esta empresa. */
public record CashierDto(
        @Schema(description = "Id del usuario en access_control") String id,
        @Schema(description = "Usuario de inicio de sesión") String username,
        @Schema(description = "Nombre para mostrar y para el turno") String name,
        @Schema(description = "Es quien hace la petición") boolean current) {
}
