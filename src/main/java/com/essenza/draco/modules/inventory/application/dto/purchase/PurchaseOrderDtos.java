package com.essenza.draco.modules.inventory.application.dto.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Órdenes de compra a proveedores (JSON en snake_case). */
public final class PurchaseOrderDtos {

    private PurchaseOrderDtos() {
    }

    /** {@code unitCost} vacío = el costo pactado con el proveedor para ese SKU. */
    public record SaveOrderRequest(Long supplierId, Long warehouseId, LocalDate expectedDate, String notes, List<LineRequest> lines,
                                   Integer lockVersion) {
    }

    public record LineRequest(Long skuId, Integer quantity, BigDecimal unitCost) {
    }

    public record ReceiveRequest(String reference, String notes, List<ReceiveLine> lines, Integer lockVersion) {
    }

    public record ReceiveLine(Long lineId, Integer quantity) {
    }

    public record StatusRequest(String reason, Integer lockVersion) {
    }

    public record OrderSummaryDto(Long id, String number, Long supplierId, String supplierName, Long warehouseId, String warehouseName,
                                  String status, LocalDate expectedDate, boolean overdue, BigDecimal total, long orderedUnits,
                                  long receivedUnits, Instant createdAt) {
    }

    public record LineDto(Long id, Long skuId, Long productId, String skuCode, String productName, String supplierCode, int quantity,
                          int received, int pending, BigDecimal unitCost, BigDecimal lineTotal) {
    }

    public record ReceiptLineDto(Long lineId, String skuCode, String productName, int quantity) {
    }

    public record ReceiptDto(Long id, String number, String warehouseName, String reference, String notes, int units,
                             String createdByName, Instant createdAt, List<ReceiptLineDto> lines) {
    }

    public record EventDto(String action, String fromStatus, String toStatus, String note, String actorName, Instant createdAt) {
    }

    /** {@code actions}: EDIT, SEND, RECEIVE, CANCEL, CLOSE según estado y permisos. */
    public record OrderDetailDto(OrderSummaryDto summary, String currency, String notes, String statusReason, Instant sentAt,
                                 Instant closedAt, int lockVersion, String createdByName, List<LineDto> lines, List<ReceiptDto> receipts,
                                 List<EventDto> events, List<String> actions) {
    }

    public record StatsDto(long drafts, long open, long overdue, long partiallyReceived, long receivedUnits30, BigDecimal purchased30) {
    }

    public record SupplierOptionDto(Long id, String name, String status, Integer leadTimeDays, BigDecimal minOrderAmount) {
    }

    public record SupplierProductDto(Long skuId, Long productId, String skuCode, String productName, String supplierCode,
                                     BigDecimal unitCost, Integer minOrderQty, boolean preferred) {
    }

    public record OptionDto(Long id, String name) {
    }

    /** Línea sugerida: {@code suggested} = reponer hasta el doble del mínimo menos lo que viene, ≥ mínimo del proveedor. */
    public record SuggestionLineDto(Long skuId, Long productId, String skuCode, String productName, String supplierCode, int onHand,
                                    int available, int minThreshold, int onOrder, int suggested, BigDecimal unitCost,
                                    BigDecimal averageCost, BigDecimal lineTotal) {
    }

    /** Un grupo por bodega y proveedor sugerido; sin proveedor ({@code supplierId} nulo) = ningún proveedor activo lo surte. */
    public record SuggestionGroupDto(Long warehouseId, String warehouseName, Long supplierId, String supplierName, Integer leadTimeDays,
                                     BigDecimal minOrderAmount, BigDecimal total, boolean belowMinimumAmount,
                                     List<SuggestionLineDto> lines) {
    }

    /** {@code coveredByOpenOrders}: SKU con stock bajo que ya vienen en órdenes abiertas suficientes. */
    public record SuggestionsDto(int lowStockSkus, int coveredByOpenOrders, boolean truncated, List<SuggestionGroupDto> groups,
                                 List<SuggestionGroupDto> withoutSupplier) {
    }
}
