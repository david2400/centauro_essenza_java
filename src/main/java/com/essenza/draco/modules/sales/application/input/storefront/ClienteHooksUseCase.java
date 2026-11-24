package com.essenza.draco.modules.sales.application.input.storefront;

/** Avisos a centauro_cliente (correos al comprador e historial de su cuenta). */
public interface ClienteHooksUseCase {

    /** Dentro de la transacción del pago. Nunca lanza: un fallo aquí no puede tumbar el pago. */
    void orderPaid(long orderId);

    /**
     * Dentro de la transacción del cambio de estado: {@code SHIPPED}, {@code READY} (retiro en tienda) o
     * {@code DELIVERED}. Nunca lanza.
     */
    void orderFulfillment(long orderId, String status);

    record Result(int sent, int failed) {
    }

    /** Envía los avisos pendientes (fuera de transacción). */
    Result dispatch(int batch);
}
