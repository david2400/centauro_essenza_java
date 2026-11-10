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
 * Tabla {@code mp_commission_adjustments}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "mp_commission_adjustments",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_mp_commission_adjustments_source", columnNames = {"source_key"})},
        indexes = {
        @Index(name = "ix_mp_commission_adjustments_commission", columnList = "commission_id")},
        check = {
        @CheckConstraint(name = "ck_mp_commission_adjustments_kind", constraint = "kind in ('CANCEL','REFUND')"),
        @CheckConstraint(name = "ck_mp_commission_adjustments_amount", constraint = "amount >= 0")})
public class MpCommissionAdjustmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "commission_id", columnDefinition = "bigint not null")
    private Long commissionId;

    @Column(name = "kind", columnDefinition = "varchar(10) not null")
    private String kind;

    @Column(name = "source_key", columnDefinition = "varchar(80) not null")
    private String sourceKey;

    @Column(name = "refunded_base", columnDefinition = "decimal(19,4) not null")
    private java.math.BigDecimal refundedBase;

    @Column(name = "amount", columnDefinition = "decimal(19,4) not null")
    private java.math.BigDecimal amount;

    @Column(name = "posted", columnDefinition = "tinyint(1) default 0 not null")
    private Boolean posted;

    @Column(name = "note", columnDefinition = "varchar(500)")
    private String note;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commission_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_commission_adjustments_commission"))
    private MpCommissionEntity commission;
}
