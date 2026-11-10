package com.essenza.draco.modules.sales.infrastructure.outbound.persistence.schema;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Tabla {@code order_events}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "order_events",
        indexes = {
        @Index(name = "ix_order_events_order", columnList = "order_id, created_at")})
public class OrderEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_order_event")
    private Long idOrderEvent;

    @Column(name = "order_id", columnDefinition = "bigint not null")
    private Long orderId;

    @Column(name = "event_type", columnDefinition = "varchar(20) not null")
    private String eventType;

    @Column(name = "from_state", columnDefinition = "varchar(20)")
    private String fromState;

    @Column(name = "to_state", columnDefinition = "varchar(20)")
    private String toState;

    @Column(name = "message", columnDefinition = "varchar(1000)")
    private String message;

    @Column(name = "actor", columnDefinition = "varchar(120)")
    private String actor;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;
}
