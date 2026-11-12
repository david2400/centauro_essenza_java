package com.essenza.draco.modules.sales.application.input.payment;

import java.time.Duration;

/** Concilia con la pasarela los pagos en línea que siguen pendientes (webhook perdido o tardío). */
public interface ReconcileGatewayPaymentsUseCase {

    /**
     * Consulta en la pasarela los pagos pendientes creados hace entre {@code minAge} y {@code maxAge}.
     *
     * @return resultado de la corrida (consultados, cambiados de estado y fallidos)
     */
    Result reconcile(Duration minAge, Duration maxAge, int batch);

    record Result(int checked, int changed, int failed) {
    }
}
