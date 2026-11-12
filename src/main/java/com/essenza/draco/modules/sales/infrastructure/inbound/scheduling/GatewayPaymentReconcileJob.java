package com.essenza.draco.modules.sales.infrastructure.inbound.scheduling;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.essenza.draco.modules.sales.application.input.payment.ReconcileGatewayPaymentsUseCase;

/**
 * Cada {@code essenza.payments.reconcile.interval} (10 min) consulta en la pasarela los pagos en línea
 * pendientes con más de {@code min-age} (5 min, margen para que llegue el webhook) y menos de
 * {@code max-age} (3 días; los más viejos los vence la reserva de la orden). Se apaga con
 * {@code essenza.payments.reconcile.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "essenza.payments.reconcile", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GatewayPaymentReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(GatewayPaymentReconcileJob.class);

    private final ReconcileGatewayPaymentsUseCase reconcile;
    private final Duration minAge;
    private final Duration maxAge;
    private final int batch;

    public GatewayPaymentReconcileJob(ReconcileGatewayPaymentsUseCase reconcile,
            @Value("${essenza.payments.reconcile.min-age:PT5M}") Duration minAge,
            @Value("${essenza.payments.reconcile.max-age:P3D}") Duration maxAge,
            @Value("${essenza.payments.reconcile.batch:50}") int batch) {
        this.reconcile = reconcile;
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.batch = batch;
    }

    @Scheduled(fixedDelayString = "${essenza.payments.reconcile.interval:PT10M}",
            initialDelayString = "${essenza.payments.reconcile.initial-delay:PT2M}")
    public void run() {
        ReconcileGatewayPaymentsUseCase.Result result = reconcile.reconcile(minAge, maxAge, batch);
        if (result.checked() > 0) {
            log.info("Conciliación con pasarela: {} consultado(s), {} con estado nuevo, {} con error",
                    result.checked(), result.changed(), result.failed());
        }
    }
}
