package com.essenza.draco.modules.sales.infrastructure.outbound.persistence.mysql.shop;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Index;

/** Bitácora de pagos y de webhooks (V8). Sin borrado lógico: es un registro histórico. */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "payment_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_events_key", columnNames = {"source", "event_key"})},
        indexes = {
                @Index(name = "idx_payment_events_payment", columnList = "payment_id, created_at")})
public class PaymentEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_payment_event")
    private Long id;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "source", nullable = false, length = 30)
    private String source;

    @Column(name = "event_key", length = 160)
    private String eventKey;

    @Column(name = "type", nullable = false, length = 40)
    private String type;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", length = 20)
    private String toStatus;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

}
