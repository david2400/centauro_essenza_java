package com.essenza.draco.modules.sales.infrastructure.outbound.persistence.schema;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

/**
 * Tabla {@code storefront_access}: enlace privado de seguimiento de una compra de la tienda pública. Guarda solo el
 * SHA-256 del secreto. Solo define el esquema; la escribe {@code StorefrontJdbcAdapter}.
 */
@Getter
@Entity
@Table(name = "storefront_access",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_storefront_access_token", columnNames = {"token_hash"})},
        indexes = {
        @Index(name = "ix_storefront_access_company", columnList = "company_id")})
public class StorefrontAccessEntity {

    @Id
    @Column(name = "purchase_id", columnDefinition = "bigint", nullable = false)
    private Long purchaseId;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "token_hash", columnDefinition = "char(64) not null")
    private String tokenHash;

    @Column(name = "email", columnDefinition = "varchar(160)")
    private String email;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;
}
