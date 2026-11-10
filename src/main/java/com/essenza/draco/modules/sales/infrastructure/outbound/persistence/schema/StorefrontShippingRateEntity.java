package com.essenza.draco.modules.sales.infrastructure.outbound.persistence.schema;

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
 * Tabla {@code storefront_shipping_rates}: HISTÓRICO, ya no se usa. Eran las tarifas de la tienda en línea por
 * nombre de departamento y ciudad; desde el tarifario de logística (zonas y peso, con ids del catálogo) la tienda
 * cotiza con {@code ShippingQuotes}. Se conserva para no perder datos: se puede borrar cuando ya no haga falta.
 */
@Getter
@Entity
@Table(name = "storefront_shipping_rates",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_storefront_shipping_rates_place", columnNames = {"company_id", "state_key", "city_key"})},
        check = {
        @CheckConstraint(name = "ck_storefront_shipping_rates_amount", constraint = "amount >= 0")})
public class StorefrontShippingRateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "state", columnDefinition = "varchar(80)")
    private String state;

    @Column(name = "city", columnDefinition = "varchar(120)")
    private String city;

    @Column(name = "state_key", columnDefinition = "varchar(80) default '' not null")
    private String stateKey;

    @Column(name = "city_key", columnDefinition = "varchar(120) default '' not null")
    private String cityKey;

    @Column(name = "amount", columnDefinition = "decimal(15,2) not null")
    private java.math.BigDecimal amount;

    @Column(name = "free_from", columnDefinition = "decimal(15,2)")
    private java.math.BigDecimal freeFrom;

    @Column(name = "min_days", columnDefinition = "int")
    private Integer minDays;

    @Column(name = "max_days", columnDefinition = "int")
    private Integer maxDays;

    @Column(name = "active", columnDefinition = "tinyint(1) default 1 not null")
    private Boolean active;

    @Column(name = "updated_by", columnDefinition = "varchar(64)")
    private String updatedBy;

    @Column(name = "updated_by_name", columnDefinition = "varchar(120)")
    private String updatedByName;

    @Column(name = "updated_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime updatedAt;
}
