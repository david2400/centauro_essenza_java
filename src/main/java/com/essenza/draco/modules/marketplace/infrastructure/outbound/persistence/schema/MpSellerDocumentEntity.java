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
 * Tabla {@code mp_seller_documents}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "mp_seller_documents",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_mp_seller_documents_key", columnNames = {"storage_key"})},
        indexes = {
        @Index(name = "ix_mp_seller_documents_seller", columnList = "seller_id, kind")},
        check = {
        @CheckConstraint(name = "ck_mp_seller_documents_review", constraint = "review_status in ('PENDING','ACCEPTED','REJECTED')")})
public class MpSellerDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "seller_id", columnDefinition = "bigint not null")
    private Long sellerId;

    @Column(name = "kind", columnDefinition = "varchar(30) not null")
    private String kind;

    @Column(name = "file_name", columnDefinition = "varchar(200) not null")
    private String fileName;

    @Column(name = "content_type", columnDefinition = "varchar(100) not null")
    private String contentType;

    @Column(name = "size_bytes", columnDefinition = "bigint not null")
    private Long sizeBytes;

    @Column(name = "sha256", columnDefinition = "char(64) not null")
    private String sha256;

    @Column(name = "storage_key", columnDefinition = "varchar(100) not null")
    private String storageKey;

    @Column(name = "review_status", columnDefinition = "varchar(10) default 'PENDING' not null")
    private String reviewStatus;

    @Column(name = "review_note", columnDefinition = "varchar(1000)")
    private String reviewNote;

    @Column(name = "reviewed_at", columnDefinition = "datetime(6)")
    private java.time.LocalDateTime reviewedAt;

    @Column(name = "reviewed_by", columnDefinition = "varchar(64)")
    private String reviewedBy;

    @Column(name = "superseded_at", columnDefinition = "datetime(6)")
    private java.time.LocalDateTime supersededAt;

    @Column(name = "uploaded_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime uploadedAt;

    @Column(name = "uploaded_by", columnDefinition = "varchar(64) not null")
    private String uploadedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_seller_documents_seller"))
    private MpSellerEntity seller;
}
