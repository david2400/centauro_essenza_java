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

import java.time.Instant;
import jakarta.persistence.UniqueConstraint;

/** V14: ubicación interna de una bodega. */
@Data
@NoArgsConstructor
@Entity
@Table(name = "warehouse_locations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_warehouse_locations_code", columnNames = {"warehouse_id", "code"})})
public class WarehouseLocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_location")
    private Long id;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "zone", length = 30)
    private String zone;

    @Column(name = "aisle", length = 10)
    private String aisle;

    @Column(name = "rack", length = 10)
    private String rack;

    @Column(name = "level", length = 10)
    private String level;

    @Column(name = "location_type", nullable = false, length = 12)
    private String locationType;

    @Column(name = "capacity_units")
    private Integer capacityUnits;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

}
