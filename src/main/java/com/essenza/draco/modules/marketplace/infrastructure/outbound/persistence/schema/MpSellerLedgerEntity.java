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
 * Tabla {@code mp_seller_ledger}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "mp_seller_ledger",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_mp_seller_ledger_key", columnNames = {"company_id", "idempotency_key"})},
        indexes = {
        @Index(name = "fk_mp_seller_ledger_commission", columnList = "commission_id"),
        @Index(name = "fk_mp_seller_ledger_seller", columnList = "seller_id"),
        @Index(name = "ix_mp_seller_ledger_seller", columnList = "company_id, seller_id, entry_date")},
        check = {
        @CheckConstraint(name = "ck_mp_seller_ledger_amount", constraint = "amount <> 0"),
        @CheckConstraint(name = "ck_mp_seller_ledger_type", constraint = "entry_type in ('COMMISSION','COMMISSION_REVERSAL','MONTHLY_FEE','PAYMENT','ADJUSTMENT','CONSIGNMENT_SALE','CONSIGNMENT_REVERSAL','SETTLEMENT','STORAGE_FEE','DISPUTE_CHARGE','MARKETPLACE_SALE','MARKETPLACE_REVERSAL')")})
public class MpSellerLedgerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "seller_id", columnDefinition = "bigint not null")
    private Long sellerId;

    @Column(name = "entry_type", columnDefinition = "varchar(20) not null")
    private String entryType;

    @Column(name = "amount", columnDefinition = "decimal(19,4) not null")
    private java.math.BigDecimal amount;

    @Column(name = "currency", columnDefinition = "char(3) not null")
    private String currency;

    @Column(name = "entry_date", columnDefinition = "date not null")
    private java.time.LocalDate entryDate;

    @Column(name = "due_on", columnDefinition = "date")
    private java.time.LocalDate dueOn;

    @Column(name = "interest_monthly_pct", columnDefinition = "decimal(7,4) default 0.0000 not null")
    private java.math.BigDecimal interestMonthlyPct;

    @Column(name = "reference", columnDefinition = "varchar(80)")
    private String reference;

    @Column(name = "commission_id", columnDefinition = "bigint")
    private Long commissionId;

    @Column(name = "fee_cycle", columnDefinition = "char(7)")
    private String feeCycle;

    @Column(name = "idempotency_key", columnDefinition = "varchar(80) not null")
    private String idempotencyKey;

    @Column(name = "note", columnDefinition = "varchar(500)")
    private String note;

    @Column(name = "created_by", columnDefinition = "varchar(64) not null")
    private String createdBy;

    @Column(name = "created_by_name", columnDefinition = "varchar(120)")
    private String createdByName;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commission_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_seller_ledger_commission"))
    private MpCommissionEntity commission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_seller_ledger_seller"))
    private MpSellerEntity seller;
}
