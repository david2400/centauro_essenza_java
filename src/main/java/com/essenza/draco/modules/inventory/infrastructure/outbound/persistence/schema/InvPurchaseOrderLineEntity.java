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
 * Tabla {@code inv_purchase_order_lines}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 * <p>Llaves foráneas hacia otros módulos que no se declaran aquí (el módulo no importa entidades ajenas): fk_inv_purchase_order_lines_sku -> product_skus.
 */
@Getter
@Entity
@Table(name = "inv_purchase_order_lines",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_inv_purchase_order_lines_sku", columnNames = {"order_id", "sku_id"})},
        indexes = {
        @Index(name = "fk_inv_purchase_order_lines_sku", columnList = "sku_id")},
        check = {
        @CheckConstraint(name = "ck_inv_purchase_order_lines_qty", constraint = "(quantity > 0) and (received_quantity >= 0) and (received_quantity <= quantity) and (unit_cost >= 0)")})
public class InvPurchaseOrderLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "order_id", columnDefinition = "bigint not null")
    private Long orderId;

    @Column(name = "sku_id", columnDefinition = "bigint not null")
    private Long skuId;

    @Column(name = "product_id", columnDefinition = "bigint not null")
    private Long productId;

    @Column(name = "sku_code", columnDefinition = "varchar(80)")
    private String skuCode;

    @Column(name = "product_name", columnDefinition = "varchar(300)")
    private String productName;

    @Column(name = "supplier_code", columnDefinition = "varchar(60)")
    private String supplierCode;

    @Column(name = "quantity", columnDefinition = "int not null")
    private Integer quantity;

    @Column(name = "received_quantity", columnDefinition = "int default 0 not null")
    private Integer receivedQuantity;

    @Column(name = "unit_cost", columnDefinition = "decimal(19,2) not null")
    private java.math.BigDecimal unitCost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_inv_purchase_order_lines_order"))
    private InvPurchaseOrderEntity order;
}
