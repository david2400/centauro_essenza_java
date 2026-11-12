package com.essenza.draco.modules.sales.application.output.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.essenza.draco.modules.sales.application.dto.payment.PaymentFilter;
import com.essenza.draco.modules.sales.domain.model.payment.Payment;

/** Pagos de las órdenes. */
public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    /** Bloquea el pago para actualizarlo (webhooks concurrentes, acciones del panel). */
    Optional<Payment> lockById(Long id);

    Optional<Payment> lockByPaymentReference(String paymentReference);

    Optional<Payment> lockByProvider(String provider, String providerReference);

    List<Payment> findByOrderId(Long orderId);

    /**
     * Ids de pagos en línea (con pasarela) que siguen pendientes y se crearon entre {@code from} y
     * {@code to}, del más antiguo al más nuevo; para conciliarlos si no llegó el webhook.
     */
    List<Long> findPendingOnline(java.time.Instant from, java.time.Instant to, int limit);

    Page<Payment> search(PaymentFilter filter, Pageable pageable);

    Totals totals(PaymentFilter filter);

    record Totals(long count, BigDecimal approved, BigDecimal pending, BigDecimal refunded, BigDecimal fees,
                  List<MethodTotals> byMethod, List<StatusTotals> byStatus) {
    }

    record MethodTotals(Long methodId, long count, BigDecimal approved, BigDecimal refunded, BigDecimal fees) {
    }

    record StatusTotals(String status, long count, BigDecimal amount) {
    }
}
