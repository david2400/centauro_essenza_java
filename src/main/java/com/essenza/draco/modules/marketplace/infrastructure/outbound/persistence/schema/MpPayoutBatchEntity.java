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
 * Tabla {@code mp_payout_batches}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "mp_payout_batches",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_mp_payout_batches_number", columnNames = {"company_id", "batch_number"}),
        @UniqueConstraint(name = "uk_mp_payout_batches_provider", columnNames = {"dispatch_provider", "provider_batch_id"})},
        indexes = {
        @Index(name = "ix_mp_payout_batches_status", columnList = "company_id, status, created_at")},
        check = {
        @CheckConstraint(name = "ck_mp_payout_batches_status", constraint = "status in ('OPEN','CLOSED','CANCELLED')"),
        @CheckConstraint(name = "ck_mp_payout_batches_dispatch", constraint = "(dispatch_status is null) or (dispatch_status in ('SENT','FAILED','DONE'))")})
public class MpPayoutBatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "batch_number", columnDefinition = "varchar(20)")
    private String batchNumber;

    @Column(name = "status", columnDefinition = "varchar(20) not null")
    private String status;

    @Column(name = "hold_days", columnDefinition = "int not null")
    private Integer holdDays;

    @Column(name = "note", columnDefinition = "varchar(500)")
    private String note;

    @Column(name = "status_reason", columnDefinition = "varchar(500)")
    private String statusReason;

    @Column(name = "created_by", columnDefinition = "varchar(64) not null")
    private String createdBy;

    @Column(name = "created_by_name", columnDefinition = "varchar(120)")
    private String createdByName;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;

    @Column(name = "closed_at", columnDefinition = "datetime(6)")
    private java.time.LocalDateTime closedAt;

    @Column(name = "dispatch_provider", columnDefinition = "varchar(20)")
    private String dispatchProvider;

    @Column(name = "dispatch_status", columnDefinition = "varchar(20)")
    private String dispatchStatus;

    @Column(name = "provider_batch_id", columnDefinition = "varchar(64)")
    private String providerBatchId;

    @Column(name = "idempotency_key", columnDefinition = "varchar(64)")
    private String idempotencyKey;

    @Column(name = "dispatch_detail", columnDefinition = "varchar(500)")
    private String dispatchDetail;

    @Column(name = "dispatched_at", columnDefinition = "datetime(6)")
    private java.time.LocalDateTime dispatchedAt;

    @Column(name = "dispatched_by", columnDefinition = "varchar(64)")
    private String dispatchedBy;
}
