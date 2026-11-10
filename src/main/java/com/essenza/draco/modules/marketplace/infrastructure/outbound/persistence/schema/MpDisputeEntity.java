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
 * Tabla {@code mp_disputes}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "mp_disputes",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_mp_disputes_number", columnNames = {"company_id", "dispute_number"}),
        @UniqueConstraint(name = "uk_mp_disputes_open_order", columnNames = {"open_order"})},
        indexes = {
        @Index(name = "fk_mp_disputes_seller", columnList = "seller_id"),
        @Index(name = "ix_mp_disputes_order", columnList = "order_id"),
        @Index(name = "ix_mp_disputes_seller", columnList = "company_id, seller_id"),
        @Index(name = "ix_mp_disputes_status", columnList = "company_id, status, respond_by")},
        check = {
        @CheckConstraint(name = "ck_mp_disputes_status", constraint = "status in ('OPEN','SELLER_RESPONDED','RESOLVED_BUYER','RESOLVED_SELLER','CANCELLED')"),
        @CheckConstraint(name = "ck_mp_disputes_reason", constraint = "reason in ('NOT_RECEIVED','DEFECTIVE','NOT_AS_DESCRIBED','WRONG_ITEM','OTHER')"),
        @CheckConstraint(name = "ck_mp_disputes_amounts", constraint = "((claimed_amount is null) or (claimed_amount >= 0)) and ((resolution_amount is null) or (resolution_amount >= 0)) and ((seller_charge is null) or (seller_charge >= 0))")})
public class MpDisputeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "dispute_number", columnDefinition = "varchar(20)")
    private String disputeNumber;

    @Column(name = "order_id", columnDefinition = "bigint not null")
    private Long orderId;

    @Column(name = "purchase_id", columnDefinition = "bigint")
    private Long purchaseId;

    @Column(name = "seller_id", columnDefinition = "bigint not null")
    private Long sellerId;

    @Column(name = "reason", columnDefinition = "varchar(20) not null")
    private String reason;

    @Column(name = "description", columnDefinition = "varchar(1000) not null")
    private String description;

    @Column(name = "claimed_amount", columnDefinition = "decimal(19,4)")
    private java.math.BigDecimal claimedAmount;

    @Column(name = "status", columnDefinition = "varchar(20) not null")
    private String status;

    @Column(name = "respond_by", columnDefinition = "date not null")
    private java.time.LocalDate respondBy;

    @Column(name = "seller_response", columnDefinition = "varchar(1000)")
    private String sellerResponse;

    @Column(name = "responded_at", columnDefinition = "datetime(6)")
    private java.time.LocalDateTime respondedAt;

    @Column(name = "resolution", columnDefinition = "varchar(20)")
    private String resolution;

    @Column(name = "resolution_amount", columnDefinition = "decimal(19,4)")
    private java.math.BigDecimal resolutionAmount;

    @Column(name = "seller_charge", columnDefinition = "decimal(19,4)")
    private java.math.BigDecimal sellerCharge;

    @Column(name = "resolution_note", columnDefinition = "varchar(1000)")
    private String resolutionNote;

    @Column(name = "devolution_id", columnDefinition = "bigint")
    private Long devolutionId;

    @Column(name = "resolved_at", columnDefinition = "datetime(6)")
    private java.time.LocalDateTime resolvedAt;

    @Column(name = "resolved_by", columnDefinition = "varchar(64)")
    private String resolvedBy;

    @Column(name = "lock_version", columnDefinition = "int default 0 not null")
    private Integer lockVersion;

    @Column(name = "created_by", columnDefinition = "varchar(64) not null")
    private String createdBy;

    @Column(name = "created_by_name", columnDefinition = "varchar(120)")
    private String createdByName;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime updatedAt;

    @Column(name = "open_order", columnDefinition = "bigint generated always as ((case when (status in ('OPEN','SELLER_RESPONDED')) then order_id end)) stored", insertable = false, updatable = false)
    private Long openOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_disputes_seller"))
    private MpSellerEntity seller;
}
