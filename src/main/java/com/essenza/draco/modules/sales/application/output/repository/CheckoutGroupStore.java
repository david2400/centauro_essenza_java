package com.essenza.draco.modules.sales.application.output.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Link de pago único para una compra multivendedor: la pasarela recibe una sola transacción con
 * la referencia del grupo y aquí se reparte en un pago por subpedido.
 */
public interface CheckoutGroupStore {

    void create(String reference, long companyId, long purchaseId, String provider, BigDecimal amount, List<Long> paymentIds,
                String actorId, Instant now);

    Optional<Group> byReference(String reference);

    Optional<Group> byPayment(long paymentId);

    /** {@code paymentIds} en orden ascendente (orden fijo al bloquearlos). */
    record Group(String reference, long companyId, long purchaseId, String provider, BigDecimal amount, List<Long> paymentIds) {
    }
}
