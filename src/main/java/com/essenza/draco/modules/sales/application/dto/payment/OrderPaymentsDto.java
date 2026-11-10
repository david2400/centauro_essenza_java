package com.essenza.draco.modules.sales.application.dto.payment;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Cobro de una orden: total, pagado, en proceso, saldo y pagos. */
public record OrderPaymentsDto(
        Long orderId,
        String orderState,
        BigDecimal orderTotal,
        @Schema(description = "Aprobado menos reembolsado") BigDecimal paidAmount,
        @Schema(description = "Pagos pendientes de confirmar") BigDecimal pendingAmount,
        BigDecimal balance,
        @Schema(description = "Lo que se puede cobrar ahora (saldo menos lo pendiente)") BigDecimal collectable,
        @Schema(description = "UNPAID, PARTIAL, PAID, OVERPAID") String paymentStatus,
        List<PaymentDto> payments,
        @Schema(description = "Valor acreditado por devoluciones (ya descontado de order_total)") BigDecimal returnedAmount,
        @Schema(description = "Subpedido de vendedor externo que cobra el vendedor por fuera: aquí no se cobra en línea ni en caja")
        boolean sellerCollects) {

    public OrderPaymentsDto(Long orderId, String orderState, BigDecimal orderTotal, BigDecimal paidAmount, BigDecimal pendingAmount,
                            BigDecimal balance, BigDecimal collectable, String paymentStatus, List<PaymentDto> payments,
                            BigDecimal returnedAmount) {
        this(orderId, orderState, orderTotal, paidAmount, pendingAmount, balance, collectable, paymentStatus, payments, returnedAmount,
                false);
    }
}
