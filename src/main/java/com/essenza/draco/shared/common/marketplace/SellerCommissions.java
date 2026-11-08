package com.essenza.draco.shared.common.marketplace;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Contrato entre ventas y marketplace para las comisiones: ventas avisa cada cambio de estado de
 * un subpedido de vendedor externo, dentro de su transacción, con los importes de la venta. Lo
 * implementa el marketplace (calcula, devenga, anula o reversa la comisión con el plan vigente).
 */
public interface SellerCommissions {

    /**
     * @param status        estado nuevo del subpedido (PAID, DELIVERED, CANCELLED…)
     * @param fromStatus    estado anterior
     * @param itemsSubtotal productos antes de descuentos, sin impuestos
     * @param discount      descuentos sobre productos, sin impuestos
     * @param shipping      envío cobrado (neto de su descuento)
     * @param taxes         impuestos cobrados
     * @param at            momento del cambio
     */
    record SaleChange(Long orderId, Long purchaseId, Long sellerId, String orderNumber, String status, String fromStatus,
                      String currency, BigDecimal itemsSubtotal, BigDecimal discount, BigDecimal shipping, BigDecimal taxes,
                      Instant at) {
    }

    void orderChanged(SaleChange change);

    /** Sin marketplace (pruebas): no hace nada. */
    SellerCommissions NONE = change -> {
    };
}
