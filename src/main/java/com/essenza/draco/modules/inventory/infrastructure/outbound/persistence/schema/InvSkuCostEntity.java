package com.essenza.draco.modules.inventory.infrastructure.outbound.persistence.schema;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Tabla {@code inv_sku_costs}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 * <p>Llaves foráneas hacia otros módulos que no se declaran aquí (el módulo no importa entidades ajenas): fk_inv_sku_costs_sku -> product_skus.
 */
@Getter
@Entity
@Table(name = "inv_sku_costs",
        indexes = {
        @Index(name = "ix_inv_sku_costs_product", columnList = "product_id")},
        check = {
        @CheckConstraint(name = "ck_inv_sku_costs_values", constraint = "((avg_cost is null) or (avg_cost >= 0)) and ((last_cost is null) or (last_cost >= 0))")})
public class InvSkuCostEntity {

    @Id
    @Column(name = "sku_id", columnDefinition = "bigint", nullable = false)
    private Long skuId;

    @Column(name = "product_id", columnDefinition = "bigint not null")
    private Long productId;

    @Column(name = "avg_cost", columnDefinition = "decimal(19,4)")
    private java.math.BigDecimal avgCost;

    @Column(name = "last_cost", columnDefinition = "decimal(19,2)")
    private java.math.BigDecimal lastCost;

    @Column(name = "last_receipt_id", columnDefinition = "bigint")
    private Long lastReceiptId;

    @Column(name = "updated_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime updatedAt;
}
