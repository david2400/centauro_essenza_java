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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

/**
 * Tabla {@code inv_sku_cost_history}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "inv_sku_cost_history",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_inv_sku_cost_history_receipt", columnNames = {"receipt_id", "sku_id"})},
        indexes = {
        @Index(name = "ix_inv_sku_cost_history_sku", columnList = "sku_id, id")},
        check = {
        @CheckConstraint(name = "ck_inv_sku_cost_history_qty", constraint = "(quantity > 0) and (unit_cost >= 0) and (avg_after >= 0)")})
public class InvSkuCostHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "sku_id", columnDefinition = "bigint not null")
    private Long skuId;

    @Column(name = "receipt_id", columnDefinition = "bigint not null")
    private Long receiptId;

    @Column(name = "units_before", columnDefinition = "bigint not null")
    private Long unitsBefore;

    @Column(name = "avg_before", columnDefinition = "decimal(19,4)")
    private java.math.BigDecimal avgBefore;

    @Column(name = "quantity", columnDefinition = "int not null")
    private Integer quantity;

    @Column(name = "unit_cost", columnDefinition = "decimal(19,2) not null")
    private java.math.BigDecimal unitCost;

    @Column(name = "avg_after", columnDefinition = "decimal(19,4) not null")
    private java.math.BigDecimal avgAfter;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_inv_sku_cost_history_receipt"))
    private InvPurchaseReceiptEntity receipt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id", referencedColumnName = "sku_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_inv_sku_cost_history_sku"))
    private InvSkuCostEntity sku;
}
