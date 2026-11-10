package com.essenza.draco.modules.marketplace.infrastructure.outbound.persistence.schema;

import jakarta.persistence.CheckConstraint;
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
 * Tabla {@code mp_sellers}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "mp_sellers",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_mp_sellers_code", columnNames = {"company_id", "code"}),
        @UniqueConstraint(name = "uk_mp_sellers_doc", columnNames = {"company_id", "doc_type", "doc_number"}),
        @UniqueConstraint(name = "uk_mp_sellers_own", columnNames = {"own_company_id"})},
        indexes = {
        @Index(name = "ix_mp_sellers_name", columnList = "company_id, legal_name"),
        @Index(name = "ix_mp_sellers_status", columnList = "company_id, status")},
        check = {
        @CheckConstraint(name = "ck_mp_sellers_kind", constraint = "kind in ('OWN','EXTERNAL')"),
        @CheckConstraint(name = "ck_mp_sellers_status", constraint = "status in ('DRAFT','IN_REVIEW','CHANGES_REQUESTED','APPROVED','SUSPENDED','CLOSED')"),
        @CheckConstraint(name = "ck_mp_sellers_doc_type", constraint = "doc_type in ('NIT','CC','CE','PP')")})
public class MpSellerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "code", columnDefinition = "varchar(30)")
    private String code;

    @Column(name = "kind", columnDefinition = "varchar(10) not null")
    private String kind;

    @Column(name = "legal_name", columnDefinition = "varchar(200) not null")
    private String legalName;

    @Column(name = "trade_name", columnDefinition = "varchar(200)")
    private String tradeName;

    @Column(name = "doc_type", columnDefinition = "varchar(5) not null")
    private String docType;

    @Column(name = "doc_number", columnDefinition = "varchar(30) not null")
    private String docNumber;

    @Column(name = "email", columnDefinition = "varchar(160)")
    private String email;

    @Column(name = "phone", columnDefinition = "varchar(40)")
    private String phone;

    @Column(name = "website", columnDefinition = "varchar(200)")
    private String website;

    @Column(name = "address", columnDefinition = "varchar(250)")
    private String address;

    @Column(name = "city", columnDefinition = "varchar(120)")
    private String city;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "account_manager_id", columnDefinition = "varchar(64)")
    private String accountManagerId;

    @Column(name = "account_manager_name", columnDefinition = "varchar(160)")
    private String accountManagerName;

    @Column(name = "status", columnDefinition = "varchar(20) not null")
    private String status;

    @Column(name = "status_reason", columnDefinition = "varchar(1000)")
    private String statusReason;

    @Column(name = "status_changed_at", columnDefinition = "datetime(6)")
    private java.time.LocalDateTime statusChangedAt;

    @Column(name = "approved_at", columnDefinition = "datetime(6)")
    private java.time.LocalDateTime approvedAt;

    @Column(name = "approved_by", columnDefinition = "varchar(64)")
    private String approvedBy;

    @Column(name = "lock_version", columnDefinition = "int default 0 not null")
    private Integer lockVersion;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;

    @Column(name = "created_by", columnDefinition = "varchar(64) not null")
    private String createdBy;

    @Column(name = "updated_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime updatedAt;

    @Column(name = "updated_by", columnDefinition = "varchar(64) not null")
    private String updatedBy;

    @Column(name = "own_company_id", columnDefinition = "bigint generated always as ((case when (kind = 'OWN') then company_id end)) stored", insertable = false, updatable = false)
    private Long ownCompanyId;
}
