package com.essenza.draco.modules.sales.infrastructure.outbound.persistence.mysql.shop;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.essenza.draco.shared.common.domain.entity.AuditInfo;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Index;
import jakarta.persistence.CheckConstraint;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@Entity
@Table(name = "cash_sessions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cash_sessions_open_register", columnNames = {"open_register"})},
        indexes = {
                @Index(name = "idx_cash_sessions_register", columnList = "cash_register_id, opened_at")},
        check = {
                @CheckConstraint(name = "ck_cash_sessions_status", constraint = "status IN ('OPEN', 'CLOSED')"),
                @CheckConstraint(name = "ck_cash_sessions_opening", constraint = "opening_amount >= 0")})
public class CashSessionEntity extends AuditInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cash_session")
    private Long id;

    @Column(name = "cash_register_id", nullable = false)
    private Long cashRegisterId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "opened_by", nullable = false, length = 120)
    private String openedBy;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "opening_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal openingAmount = BigDecimal.ZERO;

    @Column(name = "opening_notes", length = 500)
    private String openingNotes;

    @Column(name = "closed_by", length = 120)
    private String closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "expected_cash", precision = 15, scale = 2)
    private BigDecimal expectedCash;

    @Column(name = "counted_cash", precision = 15, scale = 2)
    private BigDecimal countedCash;

    @Column(name = "cash_difference", precision = 15, scale = 2)
    private BigDecimal cashDifference;

    @Column(name = "closing_notes", length = 500)
    private String closingNotes;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    /** Columna generada (solo lectura para JPA). */
    @Column(name = "open_register", columnDefinition = "bigint generated always as (if(status = 'OPEN' and deleted = 0, cash_register_id, null)) stored", insertable = false, updatable = false)
    private Long openRegister;

}
