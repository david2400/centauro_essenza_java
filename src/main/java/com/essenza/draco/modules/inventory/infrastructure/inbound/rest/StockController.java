package com.essenza.draco.modules.inventory.infrastructure.inbound.rest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.essenza.draco.modules.inventory.application.dto.StockItemDto;
import com.essenza.draco.modules.inventory.application.dto.StockReservationDto;
import com.essenza.draco.modules.inventory.application.input.stock.FindStockReservationsUseCase;
import com.essenza.draco.modules.inventory.application.input.stock.FindStockUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Existencias (solo lectura). Los movimientos viven en {@code /inventory/stock/movements}.
 * {@code GET /inventory/stock} (F3) se reenvía a {@code /levels} (ver LegacyApiRoutesFilter).
 */
@RestController
@RequestMapping("/inventory/stock")
@Tag(name = "Stock")
public class StockController {

    private final FindStockUseCase findStock;
    private final FindStockReservationsUseCase findReservations;

    public StockController(FindStockUseCase findStock, FindStockReservationsUseCase findReservations) {
        this.findStock = findStock;
        this.findReservations = findReservations;
    }

    @Operation(summary = "Stock por SKU y bodega", description = "Filtros opcionales: sku_id, product_id, warehouse_id")
    @GetMapping("/levels")
    public List<StockItemDto> levels(@RequestParam(value = "sku_id", required = false) Long skuId,
                                     @RequestParam(value = "product_id", required = false) Long productId,
                                     @RequestParam(value = "warehouse_id", required = false) Long warehouseId) {
        return findStock.find(skuId, productId, warehouseId);
    }

    @Operation(summary = "Reservas de stock",
            description = "Filtros opcionales: order_id, sku_id, product_id, status (ACTIVE, COMMITTED, RELEASED, EXPIRED, RETURNED). limit 1–200 (50).")
    @GetMapping("/reservations")
    public List<StockReservationDto> reservations(@RequestParam(value = "order_id", required = false) Long orderId,
                                                  @RequestParam(value = "sku_id", required = false) Long skuId,
                                                  @RequestParam(value = "product_id", required = false) Long productId,
                                                  @RequestParam(value = "status", required = false) String status,
                                                  @RequestParam(value = "limit", required = false) Integer limit) {
        return findReservations.find(orderId, skuId, productId, status, limit);
    }
}
