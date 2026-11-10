package com.essenza.draco.modules.marketplace.infrastructure.outbound.persistence.schema;

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
 * Tabla {@code mp_consignment_documents}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "mp_consignment_documents",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_mp_consignment_documents_number", columnNames = {"company_id", "document_number"})},
        indexes = {
        @Index(name = "fk_mp_consignment_documents_seller", columnList = "seller_id"),
        @Index(name = "ix_mp_consignment_documents_seller", columnList = "company_id, seller_id, created_at")},
        check = {
        @CheckConstraint(name = "ck_mp_consignment_documents_kind", constraint = "kind in ('RECEIPT','RETURN_TO_SELLER','ADJUSTMENT')")})
public class MpConsignmentDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "document_number", columnDefinition = "varchar(20)")
    private String documentNumber;

    @Column(name = "kind", columnDefinition = "varchar(20) not null")
    private String kind;

    @Column(name = "seller_id", columnDefinition = "bigint not null")
    private Long sellerId;

    @Column(name = "warehouse_id", columnDefinition = "bigint not null")
    private Long warehouseId;

    @Column(name = "reference", columnDefinition = "varchar(80)")
    private String reference;

    @Column(name = "notes", columnDefinition = "varchar(500)")
    private String notes;

    @Column(name = "units", columnDefinition = "int not null")
    private Integer units;

    @Column(name = "created_by", columnDefinition = "varchar(64) not null")
    private String createdBy;

    @Column(name = "created_by_name", columnDefinition = "varchar(120)")
    private String createdByName;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_consignment_documents_seller"))
    private MpSellerEntity seller;
}
