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
 * Tabla {@code mp_payout_formats}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "mp_payout_formats",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_mp_payout_formats_name", columnNames = {"name"})},
        check = {
        @CheckConstraint(name = "ck_mp_payout_formats_encoding", constraint = "encoding in ('UTF-8','ISO-8859-1','windows-1252')"),
        @CheckConstraint(name = "ck_mp_payout_formats_ext", constraint = "file_extension in ('csv','txt','prn','dat')")})
public class MpPayoutFormatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", columnDefinition = "varchar(80) not null")
    private String name;

    @Column(name = "file_extension", columnDefinition = "varchar(5) not null")
    private String fileExtension;

    @Column(name = "encoding", columnDefinition = "varchar(20) not null")
    private String encoding;

    @Column(name = "layout_json", columnDefinition = "text not null")
    private String layoutJson;

    @Column(name = "active", columnDefinition = "tinyint(1) default 1 not null")
    private Boolean active;

    @Column(name = "updated_by", columnDefinition = "varchar(64) not null")
    private String updatedBy;

    @Column(name = "updated_by_name", columnDefinition = "varchar(120)")
    private String updatedByName;

    @Column(name = "updated_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime updatedAt;
}
