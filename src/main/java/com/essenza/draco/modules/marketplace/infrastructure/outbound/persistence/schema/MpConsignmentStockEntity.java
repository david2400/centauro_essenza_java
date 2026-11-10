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
 * Tabla {@code mp_consignment_stock}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 * <p>Llaves foráneas hacia otros módulos que no se declaran aquí (el módulo no importa entidades ajenas): fk_mp_consignment_stock_sku -> product_skus, fk_mp_consignment_stock_warehouse -> warehouses.
 */
@Getter
@Entity
@Table(name = "mp_consignment_stock",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_mp_consignment_stock", columnNames = {"company_id", "seller_id", "sku_id", "warehouse_id"})},
        indexes = {
        @Index(name = "fk_mp_consignment_stock_seller", columnList = "seller_id"),
        @Index(name = "ix_mp_consignment_stock_sku", columnList = "sku_id"),
        @Index(name = "ix_mp_consignment_stock_warehouse", columnList = "warehouse_id")},
        check = {
        @CheckConstraint(name = "ck_mp_consignment_stock_on_hand", constraint = "on_hand >= 0")})
public class MpConsignmentStockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "seller_id", columnDefinition = "bigint not null")
    private Long sellerId;

    @Column(name = "sku_id", columnDefinition = "bigint not null")
    private Long skuId;

    @Column(name = "warehouse_id", columnDefinition = "bigint not null")
    private Long warehouseId;

    @Column(name = "on_hand", columnDefinition = "int default 0 not null")
    private Integer onHand;

    @Column(name = "first_received_at", columnDefinition = "datetime(6)")
    private java.time.LocalDateTime firstReceivedAt;

    @Column(name = "last_movement_at", columnDefinition = "datetime(6)")
    private java.time.LocalDateTime lastMovementAt;

    @Column(name = "version", columnDefinition = "bigint default 0 not null")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_consignment_stock_seller"))
    private MpSellerEntity seller;
}
