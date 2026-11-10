package com.essenza.draco.modules.inventory.infrastructure.outbound.persistence.schema;

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
 * Tabla {@code inv_purchase_orders}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "inv_purchase_orders",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_inv_purchase_orders_number", columnNames = {"company_id", "order_number"})},
        indexes = {
        @Index(name = "fk_inv_purchase_orders_warehouse", columnList = "warehouse_id"),
        @Index(name = "ix_inv_purchase_orders_status", columnList = "company_id, status, expected_date"),
        @Index(name = "ix_inv_purchase_orders_supplier", columnList = "supplier_id")},
        check = {
        @CheckConstraint(name = "ck_inv_purchase_orders_status", constraint = "status in ('DRAFT','SENT','PARTIALLY_RECEIVED','RECEIVED','CANCELLED','CLOSED')")})
public class InvPurchaseOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "order_number", columnDefinition = "varchar(20)")
    private String orderNumber;

    @Column(name = "supplier_id", columnDefinition = "bigint not null")
    private Long supplierId;

    @Column(name = "warehouse_id", columnDefinition = "bigint not null")
    private Long warehouseId;

    @Column(name = "status", columnDefinition = "varchar(20) not null")
    private String status;

    @Column(name = "currency", columnDefinition = "char(3) default 'COP' not null")
    private String currency;

    @Column(name = "expected_date", columnDefinition = "date")
    private java.time.LocalDate expectedDate;

    @Column(name = "notes", columnDefinition = "varchar(1000)")
    private String notes;

    @Column(name = "total", columnDefinition = "decimal(19,2) default 0.00 not null")
    private java.math.BigDecimal total;

    @Column(name = "status_reason", columnDefinition = "varchar(500)")
    private String statusReason;

    @Column(name = "sent_at", columnDefinition = "datetime(6)")
    private java.time.LocalDateTime sentAt;

    @Column(name = "closed_at", columnDefinition = "datetime(6)")
    private java.time.LocalDateTime closedAt;

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

}
