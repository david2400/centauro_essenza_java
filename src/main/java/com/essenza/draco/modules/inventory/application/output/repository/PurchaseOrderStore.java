package com.essenza.draco.modules.inventory.application.output.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.EventDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.LineDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.OptionDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.OrderSummaryDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.ReceiptDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.StatsDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.SupplierOptionDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.SupplierProductDto;
import com.essenza.draco.modules.inventory.domain.model.purchase.ReorderPolicy;

/** Órdenes de compra, sus líneas, recepciones e historial (JDBC, misma transacción que JPA). */
public interface PurchaseOrderStore {

    Optional<SupplierRef> supplier(long supplierId);

    Optional<String> activeWarehouse(long warehouseId);

    /** SKU existentes de los ids dados. */
    Map<Long, SkuInfo> skus(Collection<Long> skuIds);

    /** Productos activos que surte el proveedor, por SKU. */
    Map<Long, SupplierProductDto> supplierProducts(long supplierId);

    long insertOrder(long companyId, SupplierRef supplier, long warehouseId, LocalDate expected, String notes, BigDecimal total,
                     String actorId, String actorName, Instant now);

    void setOrderNumber(long id, String number);

    boolean updateOrder(long id, int lockVersion, long supplierId, long warehouseId, LocalDate expected, String notes, BigDecimal total,
                        Instant now);

    void replaceLines(long orderId, List<NewLine> lines);

    Optional<OrderRow> find(long companyId, long id, boolean forUpdate);

    List<LineDto> lines(long orderId);

    /** @return false si {@code lockVersion} no coincide */
    boolean setStatus(long id, int lockVersion, String status, String reason, boolean sent, boolean closed, Instant now);

    boolean hasReceipts(long orderId);

    long insertReceipt(long companyId, long orderId, long warehouseId, String reference, String notes, int units, String actorId,
                       String actorName, Instant now);

    void setReceiptNumber(long id, String number);

    void insertReceiptLine(long receiptId, long lineId, int quantity);

    void addReceived(long lineId, int quantity);

    List<ReceiptDto> receipts(long orderId);

    void addEvent(long orderId, String action, String from, String to, String note, String actorId, String actorName, Instant now);

    List<EventDto> events(long orderId);

    Page<OrderSummaryDto> search(long companyId, String status, Long supplierId, String q, LocalDate today, Pageable pageable);

    StatsDto stats(long companyId, LocalDate today);

    List<SupplierOptionDto> supplierOptions();

    List<OptionDto> warehouseOptions();

    /**
     * SKU con stock bajo (mínimo &gt; 0 y disponible ≤ mínimo) en bodegas activas, con lo que ya viene
     * en órdenes abiertas de la empresa hacia esa bodega y el costo promedio. Máximo {@code limit} filas.
     */
    List<LowStockRow> lowStock(long companyId, Long warehouseId, int limit);

    /** Proveedores activos que surten cada SKU (productos de proveedor activos). */
    Map<Long, List<SupplierCandidate>> supplierCandidates(Collection<Long> skuIds);

    record SupplierRef(long id, String name, String status, String currency, Integer leadTimeDays, BigDecimal minOrderAmount) {
    }

    record SkuInfo(long skuId, long productId, String code, String name) {
    }

    record NewLine(long skuId, long productId, String skuCode, String productName, String supplierCode, int quantity, BigDecimal unitCost) {
    }

    record LowStockRow(long skuId, long productId, String skuCode, String productName, long warehouseId, String warehouseName,
                       int onHand, int reserved, int minThreshold, int onOrder, BigDecimal averageCost) {
    }

    record SupplierCandidate(long supplierId, String supplierName, Integer leadTimeDays, BigDecimal minOrderAmount, String supplierCode,
                             BigDecimal unitCost, Integer minOrderQty, boolean preferred) implements ReorderPolicy.Candidate {
    }

    record OrderRow(OrderSummaryDto summary, String currency, String notes, String statusReason, Instant sentAt, Instant closedAt,
                    int lockVersion, String createdByName) {
    }
}
