package com.essenza.draco.modules.inventory.infrastructure.outbound.persistence.schema;

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
 * Tabla {@code inv_purchase_receipts}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "inv_purchase_receipts",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_inv_purchase_receipts_number", columnNames = {"company_id", "receipt_number"})},
        indexes = {
        @Index(name = "ix_inv_purchase_receipts_order", columnList = "order_id")})
public class InvPurchaseReceiptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "receipt_number", columnDefinition = "varchar(20)")
    private String receiptNumber;

    @Column(name = "order_id", columnDefinition = "bigint not null")
    private Long orderId;

    @Column(name = "warehouse_id", columnDefinition = "bigint not null")
    private Long warehouseId;

    @Column(name = "reference", columnDefinition = "varchar(80)")
    private String reference;

    @Column(name = "notes", columnDefinition = "varchar(500)")
    private String notes;

    @Column(name = "units", columnDefinition = "int not null")
    private Integer units;

    @Column(name = "created_by", columnDefinition = "varchar(64) not null")
    private String createdBy;

    @Column(name = "created_by_name", columnDefinition = "varchar(120)")
    private String createdByName;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_inv_purchase_receipts_order"))
    private InvPurchaseOrderEntity order;
}
