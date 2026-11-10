package com.essenza.draco.modules.sales.infrastructure.outbound.persistence.mysql.shop;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.UniqueConstraint;

/** Arqueo congelado por medio de pago al cerrar el turno. */
@Data
@NoArgsConstructor
@Entity
@Table(name = "cash_session_counts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cash_session_counts", columnNames = {"cash_session_id", "payment_method_id"})})
public class CashSessionCountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cash_session_count")
    private Long id;

    @Column(name = "cash_session_id", nullable = false)
    private Long cashSessionId;

    @Column(name = "payment_method_id", nullable = false)
    private Long paymentMethodId;

    @Column(name = "method_name", nullable = false, length = 120)
    private String methodName;

    @Column(name = "kind", nullable = false, length = 30)
    private String kind;

    @Column(name = "payments_count", nullable = false)
    private Integer paymentsCount = 0;

    @Column(name = "expected_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal expectedAmount = BigDecimal.ZERO;

    @Column(name = "counted_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal countedAmount = BigDecimal.ZERO;

    @Column(name = "difference", nullable = false, precision = 15, scale = 2)
    private BigDecimal difference = BigDecimal.ZERO;

}
