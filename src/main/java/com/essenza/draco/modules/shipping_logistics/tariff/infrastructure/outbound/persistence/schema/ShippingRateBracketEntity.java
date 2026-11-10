package com.essenza.draco.modules.shipping_logistics.tariff.infrastructure.outbound.persistence.schema;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

/**
 * Tabla {@code shipping_rate_brackets}: Franja de peso cobrable {@code (min_kg, max_kg]} de una tarifa; {@code max_kg} vacío = sin límite.
 * Solo define el esquema; la lee y escribe {@code TariffJdbcStore}.
 */
@Getter
@Entity
@Table(name = "shipping_rate_brackets",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_shipping_rate_brackets_min", columnNames = {"rate_id", "min_kg"})},
        check = {
        @CheckConstraint(name = "ck_shipping_rate_brackets_range", constraint = "min_kg >= 0 and (max_kg is null or max_kg > min_kg) and price >= 0 and (per_kg_over is null or per_kg_over >= 0)")})
public class ShippingRateBracketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "rate_id", columnDefinition = "bigint not null")
    private Long rateId;

    @Column(name = "min_kg", columnDefinition = "decimal(10,3) not null")
    private java.math.BigDecimal minKg;

    @Column(name = "max_kg", columnDefinition = "decimal(10,3)")
    private java.math.BigDecimal maxKg;

    @Column(name = "price", columnDefinition = "decimal(15,2) not null")
    private java.math.BigDecimal price;

    @Column(name = "per_kg_over", columnDefinition = "decimal(15,2)")
    private java.math.BigDecimal perKgOver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rate_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_shipping_rate_brackets_rate"))
    private ShippingRateEntity rate;
}
