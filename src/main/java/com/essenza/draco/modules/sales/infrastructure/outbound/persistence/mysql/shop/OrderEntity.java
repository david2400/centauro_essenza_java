package com.essenza.draco.modules.sales.infrastructure.outbound.persistence.mysql.shop;

import com.essenza.draco.shared.common.domain.entity.AuditInfo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Index;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ForeignKey;
import com.essenza.draco.modules.sales.infrastructure.outbound.persistence.schema.PurchaseEntity;
import jakarta.persistence.Column;

@SuperBuilder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_orders_number", columnNames = {"order_number"})},
        indexes = {
                @Index(name = "idx_orders_cash_session", columnList = "cash_session_id"),
                @Index(name = "ix_orders_customer_doc", columnList = "customer_doc_number"),
                @Index(name = "ix_orders_state_created", columnList = "state, created_at"),
                @Index(name = "ix_orders_store", columnList = "store_id"),
                @Index(name = "ix_orders_promised", columnList = "promised_date"),
                @Index(name = "ix_orders_purchase", columnList = "purchase_id"),
                @Index(name = "ix_orders_seller", columnList = "seller_id"),
                @Index(name = "ix_orders_state_ship_by", columnList = "state, ship_by")},
        check = {
                @CheckConstraint(name = "ck_orders_delivery_method", constraint = "delivery_method IN ('SHIPPING', 'PICKUP')"),
                @CheckConstraint(name = "ck_orders_priority", constraint = "priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')"),
                @CheckConstraint(name = "ck_orders_discount_type", constraint = "discount_type IN ('NONE', 'PERCENT', 'AMOUNT', 'COUPON')")})
public class OrderEntity extends AuditInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_order")
    private Long id;

    @Column(name = "complementary_order")
    private String complementaryOrder;

    @Column(nullable = false, precision = 15, scale = 2)
    private java.math.BigDecimal total;

    /** Fase 5: la orden reserva y descuenta stock (las anteriores no). */
    @Column(name = "stock_managed", nullable = false)
    @lombok.Builder.Default
    private Boolean stockManaged = false;

    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    @Column(nullable = false)
    private String state;

    /** V9: impuesto incluido en el total y suma de las bases (total − impuesto). */
    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2)
    @lombok.Builder.Default
    private java.math.BigDecimal taxAmount = java.math.BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 15, scale = 2)
    @lombok.Builder.Default
    private java.math.BigDecimal netAmount = java.math.BigDecimal.ZERO;

    /** Canal de venta: BACKOFFICE (panel), POS (caja) u ONLINE (tienda). */
    @Column(name = "channel", nullable = false, length = 20)
    @lombok.Builder.Default
    private String channel = "BACKOFFICE";

    /** Turno de caja en el que se vendió (ventas de mostrador). */
    @Column(name = "cash_session_id")
    private Long cashSessionId;

    /** V10: valor acreditado al cliente por devoluciones (lo que se cobra es total − devuelto). */
    @Column(name = "returned_amount", nullable = false, precision = 15, scale = 2)
    @lombok.Builder.Default
    private java.math.BigDecimal returnedAmount = java.math.BigDecimal.ZERO;

    // ── V19: compras multivendedor. Las escribe ventas por SQL al crear la compra; aquí solo se leen. ──
    @Column(name = "purchase_id", insertable = false, updatable = false)
    private Long purchaseId;

    @Column(name = "seller_id", insertable = false, updatable = false)
    private Long sellerId;

    /** Subpedido de un vendedor externo: venta de terceros (la cobra y la factura el vendedor). */
    @org.hibernate.annotations.Formula("(SELECT CASE WHEN s.kind = 'EXTERNAL' THEN 1 ELSE 0 END FROM mp_sellers s WHERE s.id = seller_id)")
    private Boolean thirdPartySale;

    // ── V16: orden empresarial (cliente, entrega, origen, descuento, notas y fechas) ──
    @Column(name = "order_number", length = 30)
    private String orderNumber;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "customer_kind", length = 10)
    private String customerKind;

    @Column(name = "customer_doc_type", length = 10)
    private String customerDocType;

    @Column(name = "customer_doc_number", length = 30)
    private String customerDocNumber;

    @Column(name = "customer_name", length = 200)
    private String customerName;

    @Column(name = "customer_email", length = 160)
    private String customerEmail;

    @Column(name = "customer_phone", length = 40)
    private String customerPhone;

    @Column(name = "billing_address", length = 200)
    private String billingAddress;

    @Column(name = "billing_city", length = 120)
    private String billingCity;

    @Column(name = "delivery_method", length = 12)
    private String deliveryMethod;

    @Column(name = "pickup_store_id")
    private Long pickupStoreId;

    @Column(name = "recipient_name", length = 160)
    private String recipientName;

    @Column(name = "recipient_phone", length = 40)
    private String recipientPhone;

    @Column(name = "ship_address", length = 200)
    private String shipAddress;

    @Column(name = "ship_address_detail", length = 200)
    private String shipAddressDetail;

    @Column(name = "ship_country_id")
    private Long shipCountryId;

    @Column(name = "ship_state_id")
    private Long shipStateId;

    @Column(name = "ship_city_id")
    private Long shipCityId;

    @Column(name = "ship_country", length = 80)
    private String shipCountry;

    @Column(name = "ship_state", length = 80)
    private String shipState;

    @Column(name = "ship_city", length = 120)
    private String shipCity;

    @Column(name = "ship_postal_code", length = 20)
    private String shipPostalCode;

    @Column(name = "delivery_notes", length = 500)
    private String deliveryNotes;

    @Column(name = "carrier_id")
    private Long carrierId;

    @Column(name = "tracking_number", length = 80)
    private String trackingNumber;

    @Column(name = "shipping_amount", precision = 15, scale = 2)
    private java.math.BigDecimal shippingAmount;

    @Column(name = "shipping_discount", precision = 15, scale = 2)
    private java.math.BigDecimal shippingDiscount;

    @Column(name = "promised_date")
    private java.time.LocalDate promisedDate;

    @Column(name = "store_id")
    private Long storeId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "assigned_to", length = 120)
    private String assignedTo;

    @Column(name = "priority", length = 10)
    private String priority;

    @Column(name = "discount_type", length = 10)
    private String discountType;

    @Column(name = "discount_value", precision = 15, scale = 2)
    private java.math.BigDecimal discountValue;

    @Column(name = "coupon_id")
    private Long couponId;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "discount_amount", precision = 15, scale = 2)
    private java.math.BigDecimal discountAmount;

    @Column(name = "internal_notes", length = 2000)
    private String internalNotes;

    @Column(name = "customer_notes", length = 2000)
    private String customerNotes;

    @Column(name = "paid_at")
    private java.time.Instant paidAt;

    @Column(name = "shipped_at")
    private java.time.Instant shippedAt;

    @Column(name = "delivered_at")
    private java.time.Instant deliveredAt;

    @Column(name = "cancelled_at")
    private java.time.Instant cancelledAt;

    @PrePersist
    void defaultsV9() {
        if (taxAmount == null) {
            taxAmount = java.math.BigDecimal.ZERO;
        }
        if (netAmount == null) {
            netAmount = java.math.BigDecimal.ZERO;
        }
        if (channel == null || channel.isBlank()) {
            channel = "BACKOFFICE";
        }
        if (stockManaged == null) {
            stockManaged = false;
        }
        if (returnedAmount == null) {
            returnedAmount = java.math.BigDecimal.ZERO;
        }
        if (deliveryMethod == null || deliveryMethod.isBlank()) {
            deliveryMethod = "SHIPPING";
        }
        if (priority == null || priority.isBlank()) {
            priority = "NORMAL";
        }
        if (discountType == null || discountType.isBlank()) {
            discountType = "NONE";
        }
        if (shippingAmount == null) {
            shippingAmount = java.math.BigDecimal.ZERO;
        }
        if (shippingDiscount == null) {
            shippingDiscount = java.math.BigDecimal.ZERO;
        }
        if (discountAmount == null) {
            discountAmount = java.math.BigDecimal.ZERO;
        }
    }

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<ProductOrderEntity> productOrder;

    // Sin asociación JPA hacia devoluciones (módulo devolution): se referencia solo por id (Fase 1, fronteras entre módulos).

    // Sin asociación JPA hacia despachos (módulo shipping_logistics): se referencia solo por id (Fase 1, fronteras entre módulos).

    /** La escribe JDBC (solo lectura para JPA). */
    @Column(name = "ship_by", columnDefinition = "date", insertable = false, updatable = false)
    private java.time.LocalDate shipBy;

    /** Solo para la llave foránea fk_orders_purchase (Hibernate la crea); no se usa en consultas. */
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", insertable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_orders_purchase"))
    private PurchaseEntity fkPurchaseId;
}
