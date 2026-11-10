package com.essenza.draco.modules.inventory.infrastructure.outbound.persistence.mysql.shop;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Index;

/** V13: SKU que surte un proveedor. */
@Data
@NoArgsConstructor
@Entity
@Table(name = "supplier_products",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_supplier_products_sku", columnNames = {"supplier_id", "sku_id"})},
        indexes = {
                @Index(name = "ix_supplier_products_sku", columnList = "sku_id")})
public class SupplierProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_supplier_product")
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "supplier_code", length = 64)
    private String supplierCode;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "min_order_qty", nullable = false)
    private Integer minOrderQty;

    @Column(name = "preferred", nullable = false)
    private Boolean preferred;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

}
