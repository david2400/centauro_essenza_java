package com.essenza.draco.modules.product_details.infrastructure.outbound.persistence.mysql.shop;

import com.essenza.draco.shared.common.domain.entity.AuditInfo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Set;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Index;
import jakarta.persistence.Column;

@SuperBuilder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "units_measurement",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_units_code_active", columnNames = {"code_active"}),
                @UniqueConstraint(name = "uk_units_name_active", columnNames = {"name_active"})},
        indexes = {
                @Index(name = "idx_units_dimension", columnList = "dimension")})
public class UnitMeasurementEntity extends AuditInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_unit_measurement")
    private Long id;

    /** Único entre no borradas (columna generada {@code name_active}, V7). */
    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 20)
    private String dimension;

    @Column(nullable = false, precision = 24, scale = 12)
    private java.math.BigDecimal factor;

    @Column(name = "is_base", nullable = false)
    private Boolean base;

    @Column(name = "display_decimals", nullable = false)
    private Integer decimals;

    @Column(nullable = false)
    private Boolean active;

    @ManyToMany(mappedBy = "unitMeasurements")
    private Set<FeatureEntity> features;

    /** Columna generada (solo lectura para JPA). */
    @Column(name = "code_active", columnDefinition = "varchar(20) generated always as (if(deleted = 0, code, null)) stored", insertable = false, updatable = false)
    private String codeActive;

    /** Columna generada (solo lectura para JPA). */
    @Column(name = "name_active", columnDefinition = "varchar(255) generated always as (if(deleted = 0, name, null)) stored", insertable = false, updatable = false)
    private String nameActive;
}
