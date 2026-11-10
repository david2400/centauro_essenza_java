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
import lombok.Getter;

/**
 * Tabla {@code mp_plan_assignments}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "mp_plan_assignments",
        indexes = {
        @Index(name = "ix_mp_plan_assignments_plan", columnList = "plan_id"),
        @Index(name = "ix_mp_plan_assignments_seller", columnList = "seller_id, starts_on")},
        check = {
        @CheckConstraint(name = "ck_mp_plan_assignments_dates", constraint = "(ends_on is null) or (ends_on >= starts_on)")})
public class MpPlanAssignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "seller_id", columnDefinition = "bigint not null")
    private Long sellerId;

    @Column(name = "plan_id", columnDefinition = "bigint not null")
    private Long planId;

    @Column(name = "starts_on", columnDefinition = "date not null")
    private java.time.LocalDate startsOn;

    @Column(name = "ends_on", columnDefinition = "date")
    private java.time.LocalDate endsOn;

    @Column(name = "override_commission_percent", columnDefinition = "decimal(7,4)")
    private java.math.BigDecimal overrideCommissionPercent;

    @Column(name = "override_monthly_fee", columnDefinition = "decimal(19,4)")
    private java.math.BigDecimal overrideMonthlyFee;

    @Column(name = "override_reason", columnDefinition = "varchar(1000)")
    private String overrideReason;

    @Column(name = "override_approved_by", columnDefinition = "varchar(64)")
    private String overrideApprovedBy;

    @Column(name = "agreed_terms", columnDefinition = "text not null")
    private String agreedTerms;

    @Column(name = "notes", columnDefinition = "varchar(1000)")
    private String notes;

    @Column(name = "cancelled_at", columnDefinition = "datetime(6)")
    private java.time.LocalDateTime cancelledAt;

    @Column(name = "cancelled_by", columnDefinition = "varchar(64)")
    private String cancelledBy;

    @Column(name = "cancel_reason", columnDefinition = "varchar(1000)")
    private String cancelReason;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;

    @Column(name = "created_by", columnDefinition = "varchar(64) not null")
    private String createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_plan_assignments_plan"))
    private MpPlanEntity plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_plan_assignments_seller"))
    private MpSellerEntity seller;
}
