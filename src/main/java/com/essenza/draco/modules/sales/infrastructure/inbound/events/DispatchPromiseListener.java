package com.essenza.draco.modules.sales.infrastructure.inbound.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.essenza.draco.modules.sales.application.services.dispatch.DispatchService;
import com.essenza.draco.shared.common.sales.OrderPaidEvent;

/**
 * Fija la fecha límite de despacho dentro de la misma transacción del pago (antes del commit).
 * Si falla, el pago sigue: la fecha se puede calcular después desde el tablero.
 */
@Component
public class DispatchPromiseListener {

    private static final Logger log = LoggerFactory.getLogger(DispatchPromiseListener.class);

    private final DispatchService dispatch;

    public DispatchPromiseListener(DispatchService dispatch) {
        this.dispatch = dispatch;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onOrderPaid(OrderPaidEvent event) {
        try {
            dispatch.promise(event.orderId());
        } catch (RuntimeException ex) {
            log.warn("No se pudo fijar la fecha límite de despacho de la orden {}: {}", event.orderId(), ex.getMessage());
        }
    }
}
