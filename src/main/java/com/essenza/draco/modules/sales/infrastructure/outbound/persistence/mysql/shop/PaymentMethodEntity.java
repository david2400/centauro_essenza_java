package com.essenza.draco.modules.sales.infrastructure.outbound.persistence.mysql.shop;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.essenza.draco.shared.common.domain.entity.AuditInfo;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Index;

/** Medio de pago (tabla histórica payment_types, ampliada en V8). */
@SuperBuilder
@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "payment_types",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_types_code_active", columnNames = {"code_active"})},
        indexes = {
                @Index(name = "idx_payment_types_active", columnList = "active, position")})
public class PaymentMethodEntity extends AuditInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_payment_type")
    private Long id;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "instructions", length = 1000)
    private String instructions;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "kind", nullable = false, length = 30)
    private String kind;

    @Column(name = "provider", length = 30)
    private String provider;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "fee_percent", nullable = false, precision = 6, scale = 3)
    private BigDecimal feePercent;

    @Column(name = "fee_fixed", nullable = false, precision = 15, scale = 2)
    private BigDecimal feeFixed;

    @Column(name = "min_amount", precision = 15, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "requires_reference", nullable = false)
    private boolean requiresReference;

    @Column(name = "allows_change", nullable = false)
    private boolean allowsChange;

    /** Columna generada (solo lectura para JPA). */
    @Column(name = "code_active", columnDefinition = "varchar(40) generated always as (if(deleted = 0, code, null)) stored", insertable = false, updatable = false)
    private String codeActive;
}
