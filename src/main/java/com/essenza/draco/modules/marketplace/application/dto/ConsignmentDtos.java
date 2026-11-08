package com.essenza.draco.modules.marketplace.application.dto;

import java.time.Instant;
import java.util.List;

/** Consignación: stock del vendedor en bodegas propias, documentos y kardex (JSON en snake_case). */
public final class ConsignmentDtos {

    private ConsignmentDtos() {
    }

    /** {@code kind}: RECEIPT (recepción) o RETURN_TO_SELLER (devolución al vendedor). */
    public record SaveDocumentRequest(String kind, Long sellerId, Long warehouseId, String reference, String notes,
                                      List<LineRequest> lines) {
    }

    public record LineRequest(Long skuId, Integer quantity) {
    }

    /** Conteo físico de un SKU del vendedor en una bodega. */
    public record AdjustmentRequest(Long sellerId, Long warehouseId, Long skuId, Integer countedQuantity, String reason) {
    }

    public record StockFilter(Long sellerId, Long warehouseId, String q, boolean onlyWithStock) {
    }

    public record MovementFilter(Long sellerId, Long skuId, Long warehouseId, Long orderId, String kind) {
    }

    /**
     * Saldo por vendedor, SKU y bodega. {@code reserved}/{@code available}: del vendedor y SKU en
     * todas las bodegas (ventas pendientes de pago en su oferta con despacho de la plataforma).
     */
    public record StockRowDto(Long id, Long sellerId, String sellerName, Long skuId, String skuCode, String productName,
                              Long warehouseId, String warehouseName, int onHand, int reserved, int available,
                              Instant firstReceivedAt, Instant lastMovementAt, Long daysInCustody, Long offerId,
                              String offerStatus, String offerFulfillment) {
    }

    public record StockStatsDto(long units, long skus, long sellers, long warehouses, long reservedUnits,
                                long withoutPlatformOffer) {
    }

    public record MovementDto(Long id, String kind, int quantity, int balanceAfter, Long sellerId, String sellerName,
                              Long skuId, String skuCode, String productName, Long warehouseId, String warehouseName,
                              Long documentId, String documentNumber, Long orderId, String orderNumber, Long returnCaseId,
                              String note, String createdBy, Instant createdAt) {
    }

    /** {@code lines}: solo en el detalle. */
    public record DocumentDto(Long id, String number, String kind, Long sellerId, String sellerName, Long warehouseId,
                              String warehouseName, String reference, String notes, int units, String createdBy,
                              String createdByName, Instant createdAt, List<MovementDto> lines) {
    }

    public record OptionDto(Long id, String name) {
    }
}
