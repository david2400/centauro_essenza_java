package com.essenza.draco.modules.devolution.infrastructure.outbound.persistence.mysql.shop;

import com.essenza.draco.shared.common.domain.entity.AuditInfo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.persistence.Index;

@SuperBuilder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "order_devolutions",
        indexes = {
                @Index(name = "ix_order_devolutions_order", columnList = "order_id, state")})
public class OrderDevolutionEntity extends AuditInfo {

    private final String orderDevolution = "orderDevolution";
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_order_devolution")
    private Long id;

    @Column(nullable = false)
    private String observation;

    @Column(nullable = false, length = 1)
    private String  state = "P";

    @Column(name = "return_method_id")
    private Long returnMethodId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "return_method_id", insertable = false, updatable = false)
    private ReturnMethodEntity returnMethod;

    @Column(name = "refund_method_id")
    private Long refundMethodId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "refund_method_id", insertable = false, updatable = false)
    private RefundMethodEntity refundMethod;

    @Column(name = "total_refund_amount", precision = 15, scale = 2)
    private BigDecimal totalRefundAmount;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "received_by", length = 100)
    private String receivedBy;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "inspection_notes", length = 255)
    private String inspectionNotes;

    @Column(name = "external_reference", length = 50)
    private String externalReference;

    /** V10: bodega donde reingresa lo devuelto (nula = principal). */
    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "rejected_reason", length = 255)
    private String rejectedReason;

    @Column(name = "rejected_by", length = 100)
    private String rejectedBy;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "inspected_by", length = 100)
    private String inspectedBy;

    @Column(name = "inspected_at")
    private LocalDateTime inspectedAt;

    /** Dinero devuelto sobre los pagos de la orden (puede ser menor que el valor acreditado). */
    @Column(name = "refunded_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "refunded_by", length = 100)
    private String refundedBy;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    /** Pagos reembolsados (referencia y monto). */
    @Column(name = "refund_detail", length = 500)
    private String refundDetail;

    @PrePersist
    void defaultsV10() {
        if (refundedAmount == null) {
            refundedAmount = BigDecimal.ZERO;
        }
        if (state == null || state.isBlank()) {
            state = "P";
        }
    }

    @Column(name = "motive_devolution_id", nullable = false)
    private Long motiveDevolutionId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "motive_devolution_id", insertable = false, updatable = false)
    private MotiveDevolutionEntity motiveDevolution;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    // Sin asociación JPA hacia la orden (módulo sales): se referencia solo por id (Fase 1, fronteras entre módulos).

    @OneToMany(mappedBy = orderDevolution, fetch = FetchType.LAZY)
    private List<OrderDevolutionDetailEntity> orderDevolutionDetail;

    @OneToMany(mappedBy = orderDevolution, fetch = FetchType.LAZY)
    private List<OrderDevolutionEvidenceEntity> orderDevolutionEvidences;

}


