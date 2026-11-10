package com.essenza.draco.modules.shipping_logistics.tariff.infrastructure.outbound.persistence.schema;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

/**
 * Tabla {@code shipping_zone_places}: Lugar de una zona: país, departamento o ciudad del catálogo. {@code place_key} único por empresa: un lugar pertenece a una sola zona.
 * Solo define el esquema; la lee y escribe {@code TariffJdbcStore}.
 */
@Getter
@Entity
@Table(name = "shipping_zone_places",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_shipping_zone_places_place", columnNames = {"company_id", "place_key"})},
        indexes = {
        @Index(name = "idx_shipping_zone_places_zone", columnList = "zone_id")},
        check = {
        @CheckConstraint(name = "ck_shipping_zone_places_city", constraint = "city_id is null or state_id is not null")})
public class ShippingZonePlaceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "zone_id", columnDefinition = "bigint not null")
    private Long zoneId;

    @Column(name = "country_id", columnDefinition = "bigint not null")
    private Long countryId;

    @Column(name = "state_id", columnDefinition = "bigint")
    private Long stateId;

    @Column(name = "city_id", columnDefinition = "bigint")
    private Long cityId;

    @Column(name = "place_key", columnDefinition = "varchar(64) not null")
    private String placeKey;

    @Column(name = "label", columnDefinition = "varchar(250)")
    private String label;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_shipping_zone_places_zone"))
    private ShippingZoneEntity zone;
}
