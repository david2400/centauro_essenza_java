package com.essenza.draco.modules.inventory.infrastructure.outbound.persistence.schema;

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
 * Tabla {@code inv_purchase_receipt_lines}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "inv_purchase_receipt_lines",
        indexes = {
        @Index(name = "fk_inv_purchase_receipt_lines_line", columnList = "line_id"),
        @Index(name = "ix_inv_purchase_receipt_lines_receipt", columnList = "receipt_id")},
        check = {
        @CheckConstraint(name = "ck_inv_purchase_receipt_lines_qty", constraint = "quantity > 0")})
public class InvPurchaseReceiptLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "receipt_id", columnDefinition = "bigint not null")
    private Long receiptId;

    @Column(name = "line_id", columnDefinition = "bigint not null")
    private Long lineId;

    @Column(name = "quantity", columnDefinition = "int not null")
    private Integer quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "line_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_inv_purchase_receipt_lines_line"))
    private InvPurchaseOrderLineEntity line;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_inv_purchase_receipt_lines_receipt"))
    private InvPurchaseReceiptEntity receipt;
}
