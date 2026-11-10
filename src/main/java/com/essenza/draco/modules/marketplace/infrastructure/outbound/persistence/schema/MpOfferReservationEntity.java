package com.essenza.draco.modules.marketplace.infrastructure.outbound.persistence.schema;

import jakarta.persistence.CheckConstraint;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

/**
 * Tabla {@code mp_offer_reservations}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "mp_offer_reservations",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_mp_offer_reservations_line", columnNames = {"order_line_id"})},
        indexes = {
        @Index(name = "ix_mp_offer_reservations_offer", columnList = "offer_id, status"),
        @Index(name = "ix_mp_offer_reservations_order", columnList = "order_id")},
        check = {
        @CheckConstraint(name = "ck_mp_offer_reservations_quantity", constraint = "quantity > 0"),
        @CheckConstraint(name = "ck_mp_offer_reservations_status", constraint = "status in ('ACTIVE','COMMITTED','RELEASED','EXPIRED','RETURNED')")})
public class MpOfferReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "offer_id", columnDefinition = "bigint not null")
    private Long offerId;

    @Column(name = "order_id", columnDefinition = "bigint not null")
    private Long orderId;

    @Column(name = "order_line_id", columnDefinition = "bigint not null")
    private Long orderLineId;

    @Column(name = "quantity", columnDefinition = "int not null")
    private Integer quantity;

    @Column(name = "status", columnDefinition = "varchar(12) not null")
    private String status;

    @Column(name = "created_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_offer_reservations_offer"))
    private MpOfferEntity offer;
}
