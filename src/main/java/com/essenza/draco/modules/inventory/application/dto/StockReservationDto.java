package com.essenza.draco.modules.inventory.application.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** Reserva de stock de una línea de orden en una bodega. */
public record StockReservationDto(
        Long id,
        Long orderId,
        Long lineId,
        Long skuId,
        Long productId,
        Long warehouseId,
        int quantity,
        @Schema(allowableValues = {"ACTIVE", "COMMITTED", "RELEASED", "EXPIRED", "RETURNED"}) String status,
        Instant expiresAt) {
}
