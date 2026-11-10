package com.essenza.draco.modules.inventory.infrastructure.outbound.persistence.mysql.shop;

import com.essenza.draco.shared.common.domain.entity.AuditInfo;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import jakarta.persistence.Index;

@SuperBuilder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "stock_per_warehouse",
        uniqueConstraints = @UniqueConstraint(name = "uk_spw_product_warehouse", columnNames = {"product_id", "warehouse_id"}),
        indexes = {
                @Index(name = "idx_spw_warehouse", columnList = "warehouse_id")})
/**
 * Stock de un producto en una bodega. La unicidad es la PAREJA (producto, bodega):
 * un producto puede estar en varias bodegas y una bodega guarda muchos productos.
 */
public class StockPerWarehouseEntity extends AuditInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    // Sin asociación JPA hacia producto del catálogo: se referencia solo por id (Fase 1, fronteras entre módulos).

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "warehouse_id", insertable = false, updatable = false)
    private WarehouseEntity warehouse;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "min_threshold", nullable = false)
    private Integer minThreshold;
}
