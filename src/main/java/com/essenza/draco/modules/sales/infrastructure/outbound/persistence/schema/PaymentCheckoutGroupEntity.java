package com.essenza.draco.modules.sales.infrastructure.outbound.persistence.schema;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Tabla {@code payment_checkout_groups}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "payment_checkout_groups",
        indexes = {
        @Index(name = "ix_payment_checkout_groups_purchase", columnList = "company_id, purchase_id")},
        check = {
        @CheckConstraint(name = "ck_payment_checkout_groups_amount", constraint = "amount > 0")})
public class PaymentCheckoutGroupEntity {

    @Id
    @Column(name = "reference", columnDefinition = "varchar(40)", nullable = false)
    private String reference;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "purchase_id", columnDefinition = "bigint not null")
    private Long purchaseId;

    @Column(name = "provider", columnDefinition = "varchar(30) not null")
    private String provider;

    @Column(name = "amount", columnDefinition = "decimal(15,2) not null")
    private java.math.BigDecimal amount;

    @Column(name = "created_by", columnDefinition = "varchar(64)")
    private String createdBy;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;
}
