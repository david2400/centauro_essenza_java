package com.essenza.draco.modules.sales.application.output.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.essenza.draco.modules.sales.domain.model.purchase.PurchaseStatus;

/** Compras multivendedor: cabecera por empresa y vínculo de cada subpedido (orden) con su vendedor. */
public interface PurchaseLedger {

    /** Escribe en la base los cambios pendientes de JPA (totales de las órdenes) antes de leer con SQL. */
    void flush();

    long insert(long companyId, NewPurchase purchase, String actorId, String actorName, Instant now);

    /** Liga la orden a la compra y a su vendedor ({@code sellerId} nulo = propio sin ficha de vendedor). */
    void linkOrder(long purchaseId, long orderId, Long sellerId);

    void setLineOffer(long lineId, long offerId);

    Optional<PurchaseRow> find(long companyId, long purchaseId);

    Page<PurchaseRow> search(long companyId, PurchaseFilter filter, Pageable pageable);

    List<SubOrderRow> subOrders(long purchaseId);

    /** Compra y vendedor de una orden (vacío si la orden no pertenece a una compra de la empresa). */
    Optional<OrderLink> linkOf(long companyId, long orderId);

    record NewPurchase(String channel, String currency, String customerName, String customerDocType,
                       String customerDocNumber, String customerEmail, String customerPhone) {
    }

    record PurchaseFilter(String q, String status) {
    }

    /** Cabecera con los conteos de sus subpedidos (de ahí sale el estado) y el total vigente. */
    record PurchaseRow(long id, String number, String channel, String currency, String customerName,
                       String customerDocType, String customerDocNumber, String customerEmail, String customerPhone,
                       PurchaseStatus.Counts counts, BigDecimal total, int sellers, String createdBy, String createdByName,
                       Instant createdAt) {

        public PurchaseStatus status() {
            return PurchaseStatus.derive(counts);
        }
    }

    record SubOrderRow(long orderId, String orderNumber, String state, BigDecimal total, Long sellerId,
                       String sellerName, String sellerKind, int items, int units, Instant paidAt) {
    }

    record OrderLink(long purchaseId, String purchaseNumber, Long sellerId, String sellerName, String sellerKind,
                     int subOrders) {
    }
}
