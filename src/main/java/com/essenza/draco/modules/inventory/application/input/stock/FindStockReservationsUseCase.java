package com.essenza.draco.modules.inventory.application.input.stock;

import java.util.List;

import com.essenza.draco.modules.inventory.application.dto.StockReservationDto;

public interface FindStockReservationsUseCase {

    /** Reservas más recientes primero; todos los filtros son opcionales. {@code limit} 1–200. */
    List<StockReservationDto> find(Long orderId, Long skuId, Long productId, String status, Integer limit);
}
