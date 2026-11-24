package com.essenza.draco.modules.sales.application.output.storefront;

import java.time.Instant;
import java.util.List;

/** Outbox de avisos a centauro_cliente (misma base y transacción que la venta). */
public interface ClienteHookOutbox {

    /** Eventos que se avisan. */
    String ORDER_PAID = "ORDER_PAID";
    String ORDER_SHIPPED = "ORDER_SHIPPED";
    String ORDER_READY_FOR_PICKUP = "ORDER_READY_FOR_PICKUP";
    String ORDER_DELIVERED = "ORDER_DELIVERED";

    record Hook(long id, String event, String eventKey, String purchaseNumber, String orderNumber, String trackingNumber,
                String carrierName, int attempts, Instant nextAttemptAt) {
    }

    /**
     * Encola el evento de la orden si pertenece a una compra de la tienda pública (con enlace de seguimiento), con una
     * copia del número de orden, la guía y la transportadora. Idempotente por {@code event:orden}.
     */
    void enqueue(long orderId, String event, Instant now);

    /** Avisos pendientes con intento vencido, los más viejos primero. */
    List<Hook> due(Instant now, int limit);

    /**
     * Reserva el aviso para este envío moviendo su próximo intento a {@code leaseUntil}, solo si nadie lo tomó antes
     * (otra instancia). @return false si ya lo tomó otro.
     */
    boolean claim(Hook hook, Instant leaseUntil);

    void markSent(long id, Instant now);

    /** Suma un intento; con {@code nextAttemptAt} nulo queda FAILED (no se reintenta). */
    void markFailed(long id, String error, Instant nextAttemptAt);
}
