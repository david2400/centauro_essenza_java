package com.essenza.draco.modules.sales.infrastructure.outbound.persistence.mysql.shop;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.Index;
import jakarta.persistence.CheckConstraint;

/** Entrada/salida de efectivo (inmutable). */
@Data
@NoArgsConstructor
@Entity
@Table(name = "cash_movements",
        indexes = {
                @Index(name = "idx_cash_movements_session", columnList = "cash_session_id, created_at")},
        check = {
                @CheckConstraint(name = "ck_cash_movements_type", constraint = "type IN ('IN', 'OUT')"),
                @CheckConstraint(name = "ck_cash_movements_amount", constraint = "amount > 0")})
public class CashMovementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cash_movement")
    private Long id;

    @Column(name = "cash_session_id", nullable = false)
    private Long cashSessionId;

    @Column(name = "type", nullable = false, length = 10)
    private String type;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "reference", length = 120)
    private String reference;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

}
