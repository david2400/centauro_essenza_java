package com.essenza.draco.modules.inventory.infrastructure.outbound.persistence.mysql.shop;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Index;
import jakarta.persistence.CheckConstraint;

/** Reserva de stock por línea de orden y bodega (tabla {@code stock_reservations}, V6). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "stock_reservations",
        indexes = {
                @Index(name = "idx_reservations_order", columnList = "order_id"),
                @Index(name = "idx_reservations_line", columnList = "order_line_id"),
                @Index(name = "idx_reservations_status", columnList = "status, expires_at"),
                @Index(name = "idx_reservations_stock", columnList = "sku_id, warehouse_id")},
        check = {
                @CheckConstraint(name = "ck_reservations_quantity", constraint = "quantity >= 0"),
                @CheckConstraint(name = "ck_reservations_status", constraint = "status IN ('ACTIVE', 'COMMITTED', 'RELEASED', 'EXPIRED', 'RETURNED')")})
public class StockReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reservation")
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_line_id", nullable = false)
    private Long orderLineId;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

}
