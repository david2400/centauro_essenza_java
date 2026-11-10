package com.essenza.draco.modules.sales.infrastructure.outbound.persistence.schema;

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
 * Tabla {@code customers}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "customers",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_customers_doc", columnNames = {"company_id", "doc_type", "doc_number"})},
        indexes = {
        @Index(name = "ix_customers_email", columnList = "company_id, email"),
        @Index(name = "ix_customers_name", columnList = "company_id, name")},
        check = {
        @CheckConstraint(name = "ck_customers_kind", constraint = "kind in ('PERSON','COMPANY')"),
        @CheckConstraint(name = "ck_customers_source", constraint = "source in ('PANEL','ORDERS')")})
public class CustomerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_customer")
    private Long idCustomer;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "kind", columnDefinition = "varchar(10) not null")
    private String kind;

    @Column(name = "doc_type", columnDefinition = "varchar(10) not null")
    private String docType;

    @Column(name = "doc_number", columnDefinition = "varchar(30) not null")
    private String docNumber;

    @Column(name = "name", columnDefinition = "varchar(200) not null")
    private String name;

    @Column(name = "email", columnDefinition = "varchar(160)")
    private String email;

    @Column(name = "phone", columnDefinition = "varchar(40)")
    private String phone;

    @Column(name = "billing_address", columnDefinition = "varchar(200)")
    private String billingAddress;

    @Column(name = "billing_city", columnDefinition = "varchar(120)")
    private String billingCity;

    @Column(name = "store_user_id", columnDefinition = "bigint")
    private Long storeUserId;

    @Column(name = "marketing_consent", columnDefinition = "tinyint(1) default 0 not null")
    private Boolean marketingConsent;

    @Column(name = "consent_at", columnDefinition = "datetime(6)")
    private java.time.LocalDateTime consentAt;

    @Column(name = "notes", columnDefinition = "varchar(2000)")
    private String notes;

    @Column(name = "source", columnDefinition = "varchar(12) not null")
    private String source;

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
}
