package com.essenza.draco.modules.inventory.infrastructure.outbound.persistence.schema;

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
import lombok.Getter;

/**
 * Tabla {@code inv_purchase_order_events}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "inv_purchase_order_events",
        indexes = {
        @Index(name = "ix_inv_purchase_order_events_order", columnList = "order_id, id")})
public class InvPurchaseOrderEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "order_id", columnDefinition = "bigint not null")
    private Long orderId;

    @Column(name = "action", columnDefinition = "varchar(20) not null")
    private String action;

    @Column(name = "from_status", columnDefinition = "varchar(20)")
    private String fromStatus;

    @Column(name = "to_status", columnDefinition = "varchar(20) not null")
    private String toStatus;

    @Column(name = "note", columnDefinition = "varchar(1000)")
    private String note;

    @Column(name = "actor_id", columnDefinition = "varchar(64) not null")
    private String actorId;

    @Column(name = "actor_name", columnDefinition = "varchar(120)")
    private String actorName;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_inv_purchase_order_events_order"))
    private InvPurchaseOrderEntity order;
}
