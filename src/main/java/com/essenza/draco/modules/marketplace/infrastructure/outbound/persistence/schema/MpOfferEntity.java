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
 * Tabla {@code mp_offers}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 * <p>Llaves foráneas hacia otros módulos que no se declaran aquí (el módulo no importa entidades ajenas): fk_mp_offers_sku -> product_skus.
 */
@Getter
@Entity
@Table(name = "mp_offers",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_mp_offers_open", columnNames = {"open_key"})},
        indexes = {
        @Index(name = "fk_mp_offers_seller", columnList = "seller_id"),
        @Index(name = "fk_mp_offers_sku", columnList = "sku_id"),
        @Index(name = "ix_mp_offers_seller", columnList = "company_id, seller_id"),
        @Index(name = "ix_mp_offers_sku", columnList = "company_id, sku_id"),
        @Index(name = "ix_mp_offers_status", columnList = "company_id, status")},
        check = {
        @CheckConstraint(name = "ck_mp_offers_status", constraint = "status in ('DRAFT','PENDING_REVIEW','ACTIVE','REJECTED','PAUSED','CLOSED')"),
        @CheckConstraint(name = "ck_mp_offers_condition", constraint = "item_condition in ('NEW','USED','REFURBISHED')"),
        @CheckConstraint(name = "ck_mp_offers_fulfillment", constraint = "fulfillment in ('SELLER','PLATFORM')"),
        @CheckConstraint(name = "ck_mp_offers_values", constraint = "(price > 0) and (available_quantity >= 0) and (handling_days >= 0)")})
public class MpOfferEntity {

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

    @Column(name = "product_id", columnDefinition = "bigint")
    private Long productId;

    @Column(name = "sku_code", columnDefinition = "varchar(100)")
    private String skuCode;

    @Column(name = "product_name", columnDefinition = "varchar(300)")
    private String productName;

    @Column(name = "currency", columnDefinition = "char(3) not null")
    private String currency;

    @Column(name = "price", columnDefinition = "decimal(19,4) not null")
    private java.math.BigDecimal price;

    @Column(name = "available_quantity", columnDefinition = "int default 0 not null")
    private Integer availableQuantity;

    @Column(name = "handling_days", columnDefinition = "int default 1 not null")
    private Integer handlingDays;

    @Column(name = "item_condition", columnDefinition = "varchar(12) default 'NEW' not null")
    private String itemCondition;

    @Column(name = "fulfillment", columnDefinition = "varchar(10) default 'SELLER' not null")
    private String fulfillment;

    @Column(name = "seller_sku", columnDefinition = "varchar(60)")
    private String sellerSku;

    @Column(name = "status", columnDefinition = "varchar(16) not null")
    private String status;

    @Column(name = "status_reason", columnDefinition = "varchar(1000)")
    private String statusReason;

    @Column(name = "status_changed_at", columnDefinition = "datetime(6)")
    private java.time.LocalDateTime statusChangedAt;

    @Column(name = "published_at", columnDefinition = "datetime(6)")
    private java.time.LocalDateTime publishedAt;

    @Column(name = "lock_version", columnDefinition = "int default 0 not null")
    private Integer lockVersion;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;

    @Column(name = "created_by", columnDefinition = "varchar(64) not null")
    private String createdBy;

    @Column(name = "updated_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime updatedAt;

    @Column(name = "updated_by", columnDefinition = "varchar(64) not null")
    private String updatedBy;

    @Column(name = "open_key", columnDefinition = "varchar(41) generated always as ((case when (status <> 'CLOSED') then concat(seller_id,':',sku_id) end)) stored", insertable = false, updatable = false)
    private String openKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_offers_seller"))
    private MpSellerEntity seller;
}
