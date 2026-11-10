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
 * Tabla {@code mp_commissions}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "mp_commissions",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_mp_commissions_order", columnNames = {"order_id"})},
        indexes = {
        @Index(name = "fk_mp_commissions_assignment", columnList = "assignment_id"),
        @Index(name = "fk_mp_commissions_seller", columnList = "seller_id"),
        @Index(name = "ix_mp_commissions_due", columnList = "status, accrue_on"),
        @Index(name = "ix_mp_commissions_seller", columnList = "company_id, seller_id, status")},
        check = {
        @CheckConstraint(name = "ck_mp_commissions_status", constraint = "status in ('PENDING','ACCRUED','VOIDED','REVIEW')"),
        @CheckConstraint(name = "ck_mp_commissions_amounts", constraint = "(amount >= 0) and (reversed_amount >= 0) and (reversed_amount <= amount)")})
public class MpCommissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "seller_id", columnDefinition = "bigint not null")
    private Long sellerId;

    @Column(name = "order_id", columnDefinition = "bigint not null")
    private Long orderId;

    @Column(name = "purchase_id", columnDefinition = "bigint")
    private Long purchaseId;

    @Column(name = "order_number", columnDefinition = "varchar(30)")
    private String orderNumber;

    @Column(name = "assignment_id", columnDefinition = "bigint")
    private Long assignmentId;

    @Column(name = "plan_code", columnDefinition = "varchar(40)")
    private String planCode;

    @Column(name = "currency", columnDefinition = "char(3) not null")
    private String currency;

    @Column(name = "items_subtotal", columnDefinition = "decimal(19,4) not null")
    private java.math.BigDecimal itemsSubtotal;

    @Column(name = "discount", columnDefinition = "decimal(19,4) not null")
    private java.math.BigDecimal discount;

    @Column(name = "shipping", columnDefinition = "decimal(19,4) not null")
    private java.math.BigDecimal shipping;

    @Column(name = "taxes", columnDefinition = "decimal(19,4) not null")
    private java.math.BigDecimal taxes;

    @Column(name = "base", columnDefinition = "decimal(19,4) not null")
    private java.math.BigDecimal base;

    @Column(name = "percent", columnDefinition = "decimal(7,4) not null")
    private java.math.BigDecimal percent;

    @Column(name = "percent_part", columnDefinition = "decimal(19,4) not null")
    private java.math.BigDecimal percentPart;

    @Column(name = "fixed_part", columnDefinition = "decimal(19,4) not null")
    private java.math.BigDecimal fixedPart;

    @Column(name = "amount", columnDefinition = "decimal(19,4) not null")
    private java.math.BigDecimal amount;

    @Column(name = "reversed_amount", columnDefinition = "decimal(19,4) default 0.0000 not null")
    private java.math.BigDecimal reversedAmount;

    @Column(name = "accrual_event", columnDefinition = "varchar(24)")
    private String accrualEvent;

    @Column(name = "return_window_days", columnDefinition = "int default 0 not null")
    private Integer returnWindowDays;

    @Column(name = "paid_on", columnDefinition = "date not null")
    private java.time.LocalDate paidOn;

    @Column(name = "delivered_on", columnDefinition = "date")
    private java.time.LocalDate deliveredOn;

    @Column(name = "accrue_on", columnDefinition = "date")
    private java.time.LocalDate accrueOn;

    @Column(name = "status", columnDefinition = "varchar(10) not null")
    private String status;

    @Column(name = "review_reason", columnDefinition = "varchar(300)")
    private String reviewReason;

    @Column(name = "accrued_at", columnDefinition = "datetime(6)")
    private java.time.LocalDateTime accruedAt;

    @Column(name = "voided_at", columnDefinition = "datetime(6)")
    private java.time.LocalDateTime voidedAt;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_commissions_assignment"))
    private MpPlanAssignmentEntity assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_commissions_seller"))
    private MpSellerEntity seller;
}
