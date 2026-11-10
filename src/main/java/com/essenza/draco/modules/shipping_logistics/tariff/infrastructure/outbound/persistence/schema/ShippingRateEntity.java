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
 * Tabla {@code shipping_rates}: Tarifa de un tarifario para una zona ({@code LOCAL}, {@code REGIONAL}, {@code NATIONAL} o {@code Z<id>} de una zona propia).
 * Solo define el esquema; la lee y escribe {@code TariffJdbcStore}.
 */
@Getter
@Entity
@Table(name = "shipping_rates",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_shipping_rates_zone", columnNames = {"rate_card_id", "zone_key"})},
        check = {
        @CheckConstraint(name = "ck_shipping_rates_days", constraint = "min_days >= 0 and max_days >= min_days and max_days <= 60"),
        @CheckConstraint(name = "ck_shipping_rates_free", constraint = "free_from is null or free_from >= 0")})
public class ShippingRateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "rate_card_id", columnDefinition = "bigint not null")
    private Long rateCardId;

    @Column(name = "zone_key", columnDefinition = "varchar(24) not null")
    private String zoneKey;

    @Column(name = "min_days", columnDefinition = "int default 1 not null")
    private Integer minDays;

    @Column(name = "max_days", columnDefinition = "int default 1 not null")
    private Integer maxDays;

    @Column(name = "free_from", columnDefinition = "decimal(15,2)")
    private java.math.BigDecimal freeFrom;

    @Column(name = "active", columnDefinition = "tinyint(1) default 1 not null")
    private Boolean active;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rate_card_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_shipping_rates_card"))
    private ShippingRateCardEntity rateCard;
}
