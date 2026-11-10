package com.essenza.draco.modules.inventory.infrastructure.outbound.persistence.mysql.shop;

import com.essenza.draco.shared.common.domain.entity.AuditInfo;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Index;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;

@SuperBuilder
@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "warehouses",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_warehouses_code_active", columnNames = {"code_active"})},
        indexes = {
                @Index(name = "ix_warehouses_type", columnList = "warehouse_type")},
        check = {
                @CheckConstraint(name = "ck_warehouses_type", constraint = "warehouse_type IN ('ALMACEN', 'TIENDA', 'TRANSITO', 'DEVOLUCIONES', 'CONSIGNACION')")})
public class WarehouseEntity extends AuditInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_warehouse")
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(length = 255)
    private String address;

    @Column(nullable = false)
    private Boolean active = true;

    /** Ids del catálogo de georreferenciación (servicio parametros). Nullable por las bodegas existentes. */
    @Column(name = "country_id")
    private Long countryId;

    @Column(name = "state_id")
    private Long stateId;

    @Column(name = "city_id")
    private Long cityId;

    /** Bodega principal: recibe los ajustes de stock hechos desde la ficha de producto (Fase 3). */
    @Column(name = "is_main", nullable = false)
    @lombok.Builder.Default
    private Boolean isMain = false;

    // ── V14: bodega empresarial ────────────────────────────────────────────────

    /** ALMACEN, TIENDA, TRANSITO, DEVOLUCIONES o CONSIGNACION. */
    @Column(name = "warehouse_type", nullable = false, length = 15)
    @lombok.Builder.Default
    private String warehouseType = "ALMACEN";

    @Column(name = "allow_sales", nullable = false)
    @lombok.Builder.Default
    private Boolean allowSales = true;

    @Column(name = "allow_online", nullable = false)
    @lombok.Builder.Default
    private Boolean allowOnline = true;

    @Column(name = "allow_receiving", nullable = false)
    @lombok.Builder.Default
    private Boolean allowReceiving = true;

    @Column(name = "fulfillment_priority", nullable = false)
    @lombok.Builder.Default
    private Integer fulfillmentPriority = 10;

    @Column(name = "manager_name", length = 120)
    private String managerName;

    @Column(name = "manager_phone", length = 40)
    private String managerPhone;

    @Column(name = "manager_email", length = 120)
    private String managerEmail;

    @Column(name = "opening_hours")
    private String openingHours;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "area_m2", precision = 10, scale = 2)
    private java.math.BigDecimal areaM2;

    @Column(name = "capacity_units")
    private Integer capacityUnits;

    @Column(name = "capacity_positions")
    private Integer capacityPositions;

    /** Nombre de la ciudad del catálogo de parametros (para listados). */
    @Column(name = "city", length = 80)
    private String city;

    /** Columna generada (solo lectura para JPA). */
    @Column(name = "code_active", columnDefinition = "varchar(50) generated always as (if(deleted = 0, code, null)) stored", insertable = false, updatable = false)
    private String codeActive;
}
