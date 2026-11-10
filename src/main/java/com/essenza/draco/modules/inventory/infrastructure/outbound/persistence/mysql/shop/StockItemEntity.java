package com.essenza.draco.modules.inventory.infrastructure.outbound.persistence.mysql.shop;

import com.essenza.draco.shared.common.domain.entity.AuditInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import jakarta.persistence.CheckConstraint;

/** Existencias por SKU y bodega (Fase 3). Fuente de verdad del stock. */
@SuperBuilder
@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "stock_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_items_sku_warehouse", columnNames = {"sku_id", "warehouse_id"}),
        indexes = {
                @Index(name = "idx_stock_items_product", columnList = "product_id"),
                @Index(name = "idx_stock_items_warehouse", columnList = "warehouse_id")
        },
        check = {
                @CheckConstraint(name = "ck_stock_items_quantities", constraint = "on_hand >= 0 AND reserved >= 0 AND reserved <= on_hand")})
public class StockItemEntity extends AuditInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_stock_item")
    private Long id;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    /** Producto del SKU (desnormalizado para consultas y proyección). */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "on_hand", nullable = false)
    private Integer onHand;

    @Column(nullable = false)
    private Integer reserved;

    @Column(name = "min_threshold", nullable = false)
    private Integer minThreshold;

    @Version
    @Column(nullable = false)
    private Long version;

}
