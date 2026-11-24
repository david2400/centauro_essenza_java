package com.essenza.draco.shared.common.sales;

/**
 * La orden avanzó en la entrega: despachada ({@code SHIPPED}), lista para retirar en tienda ({@code READY} con retiro)
 * o entregada ({@code DELIVERED}). Se publica dentro de la transacción del cambio de estado.
 */
public record OrderFulfillmentEvent(Long orderId, String status) {
}
