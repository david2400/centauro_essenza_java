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
import lombok.Getter;

/**
 * Tabla {@code mp_plan_events}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "mp_plan_events",
        indexes = {
        @Index(name = "ix_mp_plan_events_plan", columnList = "plan_id, created_at")})
public class MpPlanEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "plan_id", columnDefinition = "bigint not null")
    private Long planId;

    @Column(name = "event_type", columnDefinition = "varchar(30) not null")
    private String eventType;

    @Column(name = "detail", columnDefinition = "varchar(1000)")
    private String detail;

    @Column(name = "actor_id", columnDefinition = "varchar(64) not null")
    private String actorId;

    @Column(name = "actor_name", columnDefinition = "varchar(160)")
    private String actorName;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_plan_events_plan"))
    private MpPlanEntity plan;
}
