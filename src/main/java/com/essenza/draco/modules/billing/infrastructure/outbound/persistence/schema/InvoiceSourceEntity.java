package com.essenza.draco.modules.billing.infrastructure.outbound.persistence.schema;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Tabla {@code invoice_sources}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "invoice_sources",
        indexes = {
        @Index(name = "ix_invoice_sources_source", columnList = "source_type, source_key")})
public class InvoiceSourceEntity {

    @Id
    @Column(name = "invoice_id", columnDefinition = "bigint", nullable = false)
    private Long invoiceId;

    @Column(name = "source_type", columnDefinition = "varchar(40) not null")
    private String sourceType;

    @Column(name = "source_key", columnDefinition = "varchar(80) not null")
    private String sourceKey;

}
