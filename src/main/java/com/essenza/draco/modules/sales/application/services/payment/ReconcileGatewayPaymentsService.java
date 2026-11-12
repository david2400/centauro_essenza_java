package com.essenza.draco.modules.sales.application.services.payment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.essenza.draco.modules.sales.application.dto.payment.PaymentDto;
import com.essenza.draco.modules.sales.application.input.payment.ReconcileGatewayPaymentsUseCase;
import com.essenza.draco.modules.sales.application.input.payment.SyncPaymentUseCase;
import com.essenza.draco.modules.sales.application.output.repository.PaymentRepository;

/**
 * Recorre los pagos en línea pendientes y pide a la pasarela su estado con {@link SyncPaymentUseCase}
 * (la misma acción que el botón "Consultar" del panel). Sin transacción propia: cada pago se concilia
 * en la suya, así un fallo de la pasarela con un pago no revierte los demás. Es idempotente: si el
 * estado no cambió, no registra nada nuevo.
 */
@Service
public class ReconcileGatewayPaymentsService implements ReconcileGatewayPaymentsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReconcileGatewayPaymentsService.class);

    private final PaymentRepository payments;
    private final SyncPaymentUseCase sync;
    private final Clock clock;

    @Autowired
    public ReconcileGatewayPaymentsService(PaymentRepository payments, SyncPaymentUseCase sync) {
        this(payments, sync, Clock.systemUTC());
    }

    ReconcileGatewayPaymentsService(PaymentRepository payments, SyncPaymentUseCase sync, Clock clock) {
        this.payments = payments;
        this.sync = sync;
        this.clock = clock;
    }

    @Override
    public Result reconcile(Duration minAge, Duration maxAge, int batch) {
        Instant now = clock.instant();
        int checked = 0;
        int changed = 0;
        int failed = 0;
        for (Long id : payments.findPendingOnline(now.minus(maxAge), now.minus(minAge), batch)) {
            checked++;
            try {
                PaymentDto result = sync.sync(id);
                if (result != null && result.status() != null && !"PENDING".equals(result.status())) {
                    changed++;
                }
            } catch (RuntimeException ex) {
                failed++;
                log.warn("No se pudo conciliar el pago {} con la pasarela: {}", id, ex.getMessage());
            }
        }
        return new Result(checked, changed, failed);
    }
}
