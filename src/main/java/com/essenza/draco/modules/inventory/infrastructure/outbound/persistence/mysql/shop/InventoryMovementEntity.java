package com.essenza.draco.modules.inventory.infrastructure.outbound.persistence.mysql.shop;

import com.essenza.draco.shared.common.domain.entity.AuditInfo;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
// Índice por fecha: el listado de movimientos se ordena y filtra por fecha de registro.
@Table(name = "inventory_movements", indexes = @Index(name = "ix_inventory_movements_created_at", columnList = "created_at"))
public class InventoryMovementEntity extends AuditInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movement")
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    // Sin asociación JPA hacia producto del catálogo: se referencia solo por id (Fase 1, fronteras entre módulos).

    @Column(name = "from_warehouse_id")
    private Long fromWarehouseId;

    // LAZY: el listado solo usa el id; con EAGER cada movimiento cargaba sus bodegas sin usarlas.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_warehouse_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private WarehouseEntity fromWarehouse;

    @Column(name = "to_warehouse_id")
    private Long toWarehouseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_warehouse_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private WarehouseEntity toWarehouse;

    @Column(nullable = false, length = 20)
    private String type; // ENTRY, EXIT, TRANSFER

    @Column(nullable = false)
    private Integer quantity;

    @Column(length = 255)
    private String reason;

    /** SKU movido (Fase 3). Nullable solo en movimientos anteriores a la migración. */
    @Column(name = "sku_id")
    private Long skuId;

    /** Origen: MANUAL, PRODUCT_EDIT, VARIANT_EDIT, MIGRATION… */
    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;
}
