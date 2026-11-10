package com.essenza.draco.modules.marketplace.infrastructure.outbound.persistence.schema;

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
 * Tabla {@code mp_seller_invoices}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "mp_seller_invoices",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_mp_seller_invoices_invoice", columnNames = {"invoice_id"})},
        indexes = {
        @Index(name = "fk_mp_seller_invoices_seller", columnList = "seller_id"),
        @Index(name = "ix_mp_seller_invoices_period", columnList = "company_id, period, seller_id")})
public class MpSellerInvoiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "seller_id", columnDefinition = "bigint not null")
    private Long sellerId;

    @Column(name = "period", columnDefinition = "char(7) not null")
    private String period;

    @Column(name = "invoice_id", columnDefinition = "bigint not null")
    private Long invoiceId;

    @Column(name = "invoice_number", columnDefinition = "varchar(30)")
    private String invoiceNumber;

    @Column(name = "total", columnDefinition = "decimal(19,2) not null")
    private java.math.BigDecimal total;

    @Column(name = "created_by", columnDefinition = "varchar(64) not null")
    private String createdBy;

    @Column(name = "created_by_name", columnDefinition = "varchar(120)")
    private String createdByName;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_seller_invoices_seller"))
    private MpSellerEntity seller;
}
