package com.essenza.draco.modules.sales.infrastructure.outbound.persistence.schema;

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
 * Tabla {@code purchases}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "purchases",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_purchases_number", columnNames = {"purchase_number"})},
        indexes = {
        @Index(name = "ix_purchases_company", columnList = "company_id, created_at"),
        @Index(name = "ix_purchases_customer", columnList = "company_id, customer_doc_number")})
public class PurchaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_purchase")
    private Long idPurchase;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "purchase_number", columnDefinition = "varchar(20)")
    private String purchaseNumber;

    @Column(name = "channel", columnDefinition = "varchar(20) not null")
    private String channel;

    @Column(name = "currency", columnDefinition = "char(3) not null")
    private String currency;

    @Column(name = "customer_name", columnDefinition = "varchar(200)")
    private String customerName;

    @Column(name = "customer_doc_type", columnDefinition = "varchar(10)")
    private String customerDocType;

    @Column(name = "customer_doc_number", columnDefinition = "varchar(30)")
    private String customerDocNumber;

    @Column(name = "customer_email", columnDefinition = "varchar(160)")
    private String customerEmail;

    @Column(name = "customer_phone", columnDefinition = "varchar(40)")
    private String customerPhone;

    @Column(name = "created_by", columnDefinition = "varchar(64) not null")
    private String createdBy;

    @Column(name = "created_by_name", columnDefinition = "varchar(120)")
    private String createdByName;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;
}
