package com.essenza.draco.modules.sales.infrastructure.outbound.persistence.mysql.shop;

import com.essenza.draco.shared.common.domain.entity.AuditInfo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import jakarta.persistence.Index;
import jakarta.persistence.Column;

@SuperBuilder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "product_orders",
        indexes = {
                @Index(name = "idx_product_orders_sku", columnList = "sku_id"),
                @Index(name = "ix_product_orders_offer", columnList = "offer_id")})
public class ProductOrderEntity extends AuditInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_product_order")
    private Long id;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 15, scale = 2)
    private java.math.BigDecimal discount;

    @Column(nullable = false, precision = 15, scale = 2)
    private java.math.BigDecimal subtotal;

    @Column(nullable = false, precision = 15, scale = 2)
    private java.math.BigDecimal total;

    /** SKU vendido y foto congelada al crear la línea (Fase 5). */
    @Column(name = "sku_id")
    private Long skuId;

    @Column(name = "sku_code", length = 64)
    private String skuCode;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "unit_price", precision = 15, scale = 2)
    private java.math.BigDecimal unitPrice;

    /** Impuesto congelado (V9): tarifa, tipo, porcentaje, base y valor incluido en el total. */
    @Column(name = "tax_rate_id")
    private Long taxRateId;

    @Column(name = "tax_code", length = 30)
    private String taxCode;

    @Column(name = "tax_kind", length = 20)
    private String taxKind;

    @Column(name = "tax_rate", precision = 6, scale = 3)
    private java.math.BigDecimal taxRate;

    @Column(name = "tax_base", precision = 15, scale = 2)
    private java.math.BigDecimal taxBase;

    @Column(name = "tax_amount", precision = 15, scale = 2)
    private java.math.BigDecimal taxAmount;

    /** V16: parte del descuento global de la orden (el total ya la descuenta). */
    @Column(name = "order_discount", precision = 15, scale = 2)
    private java.math.BigDecimal orderDiscount;
    /** V16: unidades preparadas para el despacho. */
    @Column(name = "picked_quantity")
    private Integer pickedQuantity;

    @jakarta.persistence.PrePersist
    @jakarta.persistence.PreUpdate
    void defaultsV16() {
        if (orderDiscount == null) {
            orderDiscount = java.math.BigDecimal.ZERO;
        }
        if (pickedQuantity == null) {
            pickedQuantity = 0;
        }
    }
    @Column(name = "product_id", nullable = false)
    private Long productId;

    // Sin asociación JPA hacia producto del catálogo: se referencia solo por id (Fase 1, fronteras entre módulos).

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", insertable = false, updatable = false)
    private OrderEntity order;

    // Sin asociación JPA hacia devoluciones (módulo devolution): se referencia solo por id (Fase 1, fronteras entre módulos).

    // Sin asociación JPA hacia detalle de despacho (módulo shipping_logistics): se referencia solo por id (Fase 1, fronteras entre módulos).

    /** La escribe JDBC (solo lectura para JPA). */
    @Column(name = "offer_id", columnDefinition = "bigint", insertable = false, updatable = false)
    private Long offerId;
}
