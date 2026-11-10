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
 * Tabla {@code mp_consignment_movements}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "mp_consignment_movements",
        indexes = {
        @Index(name = "ix_mp_consignment_movements_document", columnList = "document_id"),
        @Index(name = "ix_mp_consignment_movements_order", columnList = "order_id, order_line_id"),
        @Index(name = "ix_mp_consignment_movements_seller", columnList = "company_id, seller_id, created_at"),
        @Index(name = "ix_mp_consignment_movements_stock", columnList = "stock_id, id")},
        check = {
        @CheckConstraint(name = "ck_mp_consignment_movements_kind", constraint = "kind in ('RECEIPT','RETURN_TO_SELLER','ADJUSTMENT','SALE','SALE_CANCEL','CUSTOMER_RETURN')"),
        @CheckConstraint(name = "ck_mp_consignment_movements_quantity", constraint = "(quantity <> 0) and (balance_after >= 0)")})
public class MpConsignmentMovementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "stock_id", columnDefinition = "bigint not null")
    private Long stockId;

    @Column(name = "seller_id", columnDefinition = "bigint not null")
    private Long sellerId;

    @Column(name = "sku_id", columnDefinition = "bigint not null")
    private Long skuId;

    @Column(name = "warehouse_id", columnDefinition = "bigint not null")
    private Long warehouseId;

    @Column(name = "kind", columnDefinition = "varchar(20) not null")
    private String kind;

    @Column(name = "quantity", columnDefinition = "int not null")
    private Integer quantity;

    @Column(name = "balance_after", columnDefinition = "int not null")
    private Integer balanceAfter;

    @Column(name = "document_id", columnDefinition = "bigint")
    private Long documentId;

    @Column(name = "order_id", columnDefinition = "bigint")
    private Long orderId;

    @Column(name = "order_line_id", columnDefinition = "bigint")
    private Long orderLineId;

    @Column(name = "return_case_id", columnDefinition = "bigint")
    private Long returnCaseId;

    @Column(name = "note", columnDefinition = "varchar(500)")
    private String note;

    @Column(name = "created_by", columnDefinition = "varchar(64) not null")
    private String createdBy;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_consignment_movements_document"))
    private MpConsignmentDocumentEntity document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_consignment_movements_stock"))
    private MpConsignmentStockEntity stock;
}
