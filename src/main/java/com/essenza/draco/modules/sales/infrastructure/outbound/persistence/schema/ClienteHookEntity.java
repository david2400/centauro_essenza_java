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
 * Tabla {@code cliente_hooks}: avisos pendientes para centauro_cliente (outbox). Se escribe en la misma transacción
 * que el pago o el cambio de estado y la envía {@code ClienteHookJob}; un aviso por evento ({@code event_key} único). Solo define el
 * esquema; la escribe {@code ClienteHookJdbcStore}.
 */
@Getter
@Entity
@Table(name = "cliente_hooks",
        uniqueConstraints = @UniqueConstraint(name = "uk_cliente_hooks_event", columnNames = {"event_key"}),
        indexes = @Index(name = "ix_cliente_hooks_due", columnList = "status, next_attempt_at"))
public class ClienteHookEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "event", columnDefinition = "varchar(30) not null")
    private String event;

    @Column(name = "event_key", columnDefinition = "varchar(80) not null")
    private String eventKey;

    @Column(name = "purchase_number", columnDefinition = "varchar(20) not null")
    private String purchaseNumber;

    /** Subpedido (orden) del aviso: en el marketplace cada vendedor despacha el suyo. */
    @Column(name = "order_number", columnDefinition = "varchar(30)")
    private String orderNumber;

    /** Copia de la guía y la transportadora al momento del evento (para el correo de despacho). */
    @Column(name = "tracking_number", columnDefinition = "varchar(80)")
    private String trackingNumber;

    @Column(name = "carrier_name", columnDefinition = "varchar(120)")
    private String carrierName;

    /** PENDING, SENT o FAILED (se agotaron los intentos). */
    @Column(name = "status", columnDefinition = "varchar(10) not null")
    private String status;

    @Column(name = "attempts", columnDefinition = "int not null")
    private Integer attempts;

    @Column(name = "next_attempt_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "varchar(300)")
    private String lastError;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;

    @Column(name = "sent_at", columnDefinition = "datetime(6)")
    private java.time.LocalDateTime sentAt;
}
