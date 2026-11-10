package com.essenza.draco.modules.shipping_logistics.tariff.infrastructure.outbound.persistence.schema;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

/**
 * Tabla {@code shipping_quote_misses}: Destinos que se cotizaron sin cobertura (para saber qué tarifas faltan). Un contador por empresa y destino.
 * Solo define el esquema; la lee y escribe {@code TariffJdbcStore}.
 */
@Getter
@Entity
@Table(name = "shipping_quote_misses",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_shipping_quote_misses_place", columnNames = {"company_id", "place_key"})},
        indexes = {
        @Index(name = "idx_shipping_quote_misses_last", columnList = "company_id, last_at")})
public class ShippingQuoteMissEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "place_key", columnDefinition = "varchar(64) not null")
    private String placeKey;

    @Column(name = "country_id", columnDefinition = "bigint not null")
    private Long countryId;

    @Column(name = "state_id", columnDefinition = "bigint")
    private Long stateId;

    @Column(name = "city_id", columnDefinition = "bigint")
    private Long cityId;

    @Column(name = "zone_key", columnDefinition = "varchar(24)")
    private String zoneKey;

    @Column(name = "label", columnDefinition = "varchar(250)")
    private String label;

    @Column(name = "misses", columnDefinition = "int default 0 not null")
    private Integer misses;

    @Column(name = "last_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime lastAt;
}
