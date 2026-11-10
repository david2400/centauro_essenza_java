package com.essenza.draco.modules.shipping_logistics.tariff.infrastructure.outbound.persistence.schema;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

/**
 * Tabla {@code shipping_rate_cards}: Tarifario de envío de una transportadora para la empresa. {@code carrier_id} apunta a {@code carriers} (sin FK declarada: {@code carriers} tiene borrado lógico y Hibernate no admite la asociación LAZY).
 * Solo define el esquema; la lee y escribe {@code TariffJdbcStore}.
 */
@Getter
@Entity
@Table(name = "shipping_rate_cards",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_shipping_rate_cards_carrier", columnNames = {"company_id", "carrier_id"})},
        check = {
        @CheckConstraint(name = "ck_shipping_rate_cards_divisor", constraint = "volumetric_divisor between 1000 and 10000"),
        @CheckConstraint(name = "ck_shipping_rate_cards_amounts", constraint = "default_weight_kg > 0 and min_charge >= 0 and insurance_pct between 0 and 20 and insurance_min >= 0")})
public class ShippingRateCardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "carrier_id", columnDefinition = "bigint not null")
    private Long carrierId;

    @Column(name = "name", columnDefinition = "varchar(120) not null")
    private String name;

    @Column(name = "volumetric_divisor", columnDefinition = "int default 5000 not null")
    private Integer volumetricDivisor;

    @Column(name = "default_weight_kg", columnDefinition = "decimal(10,3) default 1.000 not null")
    private java.math.BigDecimal defaultWeightKg;

    @Column(name = "min_charge", columnDefinition = "decimal(15,2) default 0.00 not null")
    private java.math.BigDecimal minCharge;

    @Column(name = "insurance_pct", columnDefinition = "decimal(5,2) default 0.00 not null")
    private java.math.BigDecimal insurancePct;

    @Column(name = "insurance_min", columnDefinition = "decimal(15,2) default 0.00 not null")
    private java.math.BigDecimal insuranceMin;

    @Column(name = "active", columnDefinition = "tinyint(1) default 1 not null")
    private Boolean active;

    @Column(name = "version", columnDefinition = "bigint default 0 not null")
    private Long version;

    @Column(name = "updated_by", columnDefinition = "varchar(64)")
    private String updatedBy;

    @Column(name = "updated_by_name", columnDefinition = "varchar(120)")
    private String updatedByName;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime updatedAt;
}
