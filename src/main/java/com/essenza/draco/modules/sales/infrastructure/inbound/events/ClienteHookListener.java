package com.essenza.draco.modules.sales.infrastructure.inbound.events;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.essenza.draco.modules.sales.application.input.storefront.ClienteHooksUseCase;
import com.essenza.draco.shared.common.sales.OrderFulfillmentEvent;
import com.essenza.draco.shared.common.sales.OrderPaidEvent;

/**
 * Encola los avisos a centauro_cliente dentro de la transacción del pago o del cambio de estado (antes del commit): si
 * se revierte, el aviso tampoco existe. El caso de uso no lanza, así que un fallo aquí no afecta el pago.
 */
@Component
public class ClienteHookListener {

    private final ClienteHooksUseCase hooks;

    public ClienteHookListener(ClienteHooksUseCase hooks) {
        this.hooks = hooks;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onOrderPaid(OrderPaidEvent event) {
        if (event.orderId() != null) {
            hooks.orderPaid(event.orderId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onFulfillment(OrderFulfillmentEvent event) {
        if (event.orderId() != null) {
            hooks.orderFulfillment(event.orderId(), event.status());
        }
    }
}
