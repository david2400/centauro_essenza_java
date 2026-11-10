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
 * Tabla {@code mp_plans}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "mp_plans",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_mp_plans_draft", columnNames = {"company_id", "draft_code"}),
        @UniqueConstraint(name = "uk_mp_plans_version", columnNames = {"company_id", "code", "version"})},
        indexes = {
        @Index(name = "ix_mp_plans_status", columnList = "company_id, status")},
        check = {
        @CheckConstraint(name = "ck_mp_plans_amounts", constraint = "(monthly_fee >= 0) and (fixed_fee_per_sale >= 0) and (commission_percent between 0 and 100) and (late_interest_monthly_percent between 0 and 100)"),
        @CheckConstraint(name = "ck_mp_plans_modality", constraint = "modality in ('COMMISSION','SUBSCRIPTION','MIXED')"),
        @CheckConstraint(name = "ck_mp_plans_status", constraint = "status in ('DRAFT','ACTIVE','SUPERSEDED','RETIRED')")})
public class MpPlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "code", columnDefinition = "varchar(30) not null")
    private String code;

    @Column(name = "version", columnDefinition = "int not null")
    private Integer version;

    @Column(name = "name", columnDefinition = "varchar(120) not null")
    private String name;

    @Column(name = "description", columnDefinition = "varchar(1000)")
    private String description;

    @Column(name = "status", columnDefinition = "varchar(12) not null")
    private String status;

    @Column(name = "modality", columnDefinition = "varchar(12) not null")
    private String modality;

    @Column(name = "currency", columnDefinition = "char(3) not null")
    private String currency;

    @Column(name = "monthly_fee", columnDefinition = "decimal(19,4) default 0.0000 not null")
    private java.math.BigDecimal monthlyFee;

    @Column(name = "commission_percent", columnDefinition = "decimal(7,4) default 0.0000 not null")
    private java.math.BigDecimal commissionPercent;

    @Column(name = "fixed_fee_per_sale", columnDefinition = "decimal(19,4) default 0.0000 not null")
    private java.math.BigDecimal fixedFeePerSale;

    @Column(name = "commission_base", columnDefinition = "varchar(30) not null")
    private String commissionBase;

    @Column(name = "include_shipping", columnDefinition = "tinyint(1) default 0 not null")
    private Boolean includeShipping;

    @Column(name = "include_taxes", columnDefinition = "tinyint(1) default 0 not null")
    private Boolean includeTaxes;

    @Column(name = "accrual_event", columnDefinition = "varchar(30) not null")
    private String accrualEvent;

    @Column(name = "return_window_days", columnDefinition = "int default 0 not null")
    private Integer returnWindowDays;

    @Column(name = "refund_policy", columnDefinition = "varchar(30) not null")
    private String refundPolicy;

    @Column(name = "fee_billing", columnDefinition = "varchar(12) not null")
    private String feeBilling;

    @Column(name = "grace_days", columnDefinition = "int default 0 not null")
    private Integer graceDays;

    @Column(name = "late_interest_monthly_percent", columnDefinition = "decimal(7,4) default 0.0000 not null")
    private java.math.BigDecimal lateInterestMonthlyPercent;

    @Column(name = "valid_from", columnDefinition = "date not null")
    private java.time.LocalDate validFrom;

    @Column(name = "valid_to", columnDefinition = "date")
    private java.time.LocalDate validTo;

    @Column(name = "published_at", columnDefinition = "datetime(6)")
    private java.time.LocalDateTime publishedAt;

    @Column(name = "published_by", columnDefinition = "varchar(64)")
    private String publishedBy;

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

    @Column(name = "draft_code", columnDefinition = "varchar(30) generated always as ((case when (status = 'DRAFT') then code end)) stored", insertable = false, updatable = false)
    private String draftCode;

    @Column(name = "storage_fee_unit_day", columnDefinition = "decimal(19,4) default 0.0000 not null")
    private java.math.BigDecimal storageFeeUnitDay;
}
