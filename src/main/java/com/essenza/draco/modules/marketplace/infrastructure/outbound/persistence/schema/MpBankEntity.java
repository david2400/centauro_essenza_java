package com.essenza.draco.modules.marketplace.infrastructure.outbound.persistence.schema;

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
 * Tabla {@code mp_banks}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "mp_banks",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_mp_banks_code", columnNames = {"code"})},
        check = {
        @CheckConstraint(name = "ck_mp_banks_kind", constraint = "kind in ('BANK','DIGITAL_BANK','WALLET','COOPERATIVE','FINANCIAL')")})
public class MpBankEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "code", columnDefinition = "varchar(40) not null")
    private String code;

    @Column(name = "name", columnDefinition = "varchar(80) not null")
    private String name;

    @Column(name = "kind", columnDefinition = "varchar(20) not null")
    private String kind;

    @Column(name = "ach_code", columnDefinition = "varchar(10)")
    private String achCode;

    @Column(name = "wompi_bank_id", columnDefinition = "varchar(40)")
    private String wompiBankId;

    @Column(name = "active", columnDefinition = "tinyint(1) default 1 not null")
    private Boolean active;

    @Column(name = "position", columnDefinition = "int default 100 not null")
    private Integer position;

    @Column(name = "updated_by", columnDefinition = "varchar(64)")
    private String updatedBy;

    @Column(name = "updated_at", columnDefinition = "datetime(6)")
    private java.time.LocalDateTime updatedAt;
}
