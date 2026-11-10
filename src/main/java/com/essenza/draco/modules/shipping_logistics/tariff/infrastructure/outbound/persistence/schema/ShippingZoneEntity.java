package com.essenza.draco.modules.shipping_logistics.tariff.infrastructure.outbound.persistence.schema;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

/**
 * Tabla {@code shipping_zones}: Zona propia de envío (p. ej. trayecto especial), formada por lugares del catálogo de parametros.
 * Solo define el esquema; la lee y escribe {@code TariffJdbcStore}.
 */
@Getter
@Entity
@Table(name = "shipping_zones",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_shipping_zones_code", columnNames = {"company_id", "code"})})
public class ShippingZoneEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "code", columnDefinition = "varchar(30) not null")
    private String code;

    @Column(name = "name", columnDefinition = "varchar(120) not null")
    private String name;

    @Column(name = "description", columnDefinition = "varchar(300)")
    private String description;

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
