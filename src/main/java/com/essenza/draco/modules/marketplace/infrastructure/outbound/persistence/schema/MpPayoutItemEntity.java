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
 * Tabla {@code mp_payout_items}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "mp_payout_items",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_mp_payout_items_open_seller", columnNames = {"open_seller"})},
        indexes = {
        @Index(name = "ix_mp_payout_items_batch", columnList = "batch_id, id"),
        @Index(name = "ix_mp_payout_items_seller", columnList = "seller_id")},
        check = {
        @CheckConstraint(name = "ck_mp_payout_items_amount", constraint = "amount > 0"),
        @CheckConstraint(name = "ck_mp_payout_items_status", constraint = "status in ('PENDING','PAID','REJECTED')")})
public class MpPayoutItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "batch_id", columnDefinition = "bigint not null")
    private Long batchId;

    @Column(name = "seller_id", columnDefinition = "bigint not null")
    private Long sellerId;

    @Column(name = "seller_name", columnDefinition = "varchar(200)")
    private String sellerName;

    @Column(name = "seller_doc", columnDefinition = "varchar(40)")
    private String sellerDoc;

    @Column(name = "amount", columnDefinition = "decimal(19,2) not null")
    private java.math.BigDecimal amount;

    @Column(name = "currency", columnDefinition = "char(3) default 'COP' not null")
    private String currency;

    @Column(name = "bank_name", columnDefinition = "varchar(80) not null")
    private String bankName;

    @Column(name = "bank_code", columnDefinition = "varchar(10)")
    private String bankCode;

    @Column(name = "bank_id", columnDefinition = "bigint")
    private Long bankId;

    @Column(name = "account_type", columnDefinition = "varchar(20) not null")
    private String accountType;

    @Column(name = "key_type", columnDefinition = "varchar(20)")
    private String keyType;

    @Column(name = "account_number", columnDefinition = "varchar(120) not null")
    private String accountNumber;

    @Column(name = "holder_name", columnDefinition = "varchar(160) not null")
    private String holderName;

    @Column(name = "holder_doc_type", columnDefinition = "varchar(10) not null")
    private String holderDocType;

    @Column(name = "holder_doc_number", columnDefinition = "varchar(30) not null")
    private String holderDocNumber;

    @Column(name = "status", columnDefinition = "varchar(20) not null")
    private String status;

    @Column(name = "reference", columnDefinition = "varchar(80)")
    private String reference;

    @Column(name = "paid_on", columnDefinition = "date")
    private java.time.LocalDate paidOn;

    @Column(name = "note", columnDefinition = "varchar(500)")
    private String note;

    @Column(name = "actor_id", columnDefinition = "varchar(64)")
    private String actorId;

    @Column(name = "actor_name", columnDefinition = "varchar(120)")
    private String actorName;

    @Column(name = "updated_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime updatedAt;

    @Column(name = "open_seller", columnDefinition = "bigint generated always as (if((status = 'PENDING'),seller_id,NULL)) stored", insertable = false, updatable = false)
    private Long openSeller;

    @Column(name = "provider_reference", columnDefinition = "varchar(40)")
    private String providerReference;

    @Column(name = "provider_tx_id", columnDefinition = "varchar(64)")
    private String providerTxId;

    @Column(name = "provider_status", columnDefinition = "varchar(20)")
    private String providerStatus;

    @Column(name = "provider_detail", columnDefinition = "varchar(300)")
    private String providerDetail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_payout_items_batch"))
    private MpPayoutBatchEntity batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_payout_items_seller"))
    private MpSellerEntity seller;
}
