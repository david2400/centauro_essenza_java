package com.essenza.draco.modules.inventory.application.services;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.inventory.application.dto.StockReservationDto;
import com.essenza.draco.modules.inventory.application.input.stock.FindStockReservationsUseCase;
import com.essenza.draco.modules.inventory.application.output.repository.StockReservationRepository;
import com.essenza.draco.modules.inventory.domain.model.StockReservation;

@Service
@Transactional(readOnly = true)
public class StockReservationQueryService implements FindStockReservationsUseCase {

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    private final StockReservationRepository reservations;

    public StockReservationQueryService(StockReservationRepository reservations) {
        this.reservations = reservations;
    }

    @Override
    public List<StockReservationDto> find(Long orderId, Long skuId, Long productId, String status, Integer limit) {
        StockReservation.Status parsed = null;
        if (status != null && !status.isBlank()) {
            try {
                parsed = StockReservation.Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Estado de reserva desconocido: " + status);
            }
        }
        int size = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        return reservations.search(orderId, skuId, productId, parsed, size).stream()
                .map(r -> new StockReservationDto(r.getId(), r.getOrderId(), r.getLineId(), r.getSkuId(), r.getProductId(),
                        r.getWarehouseId(), r.getQuantity(), r.getStatus().name(), r.getExpiresAt()))
                .toList();
    }
}
