package com.essenza.draco.modules.marketplace.application.port;

import java.util.List;
import java.util.Optional;

/**
 * Proveedor de dispersión automática (pagos a cuentas o llaves Bre-B). Arranca en sandbox; producción
 * exige habilitarla explícitamente en la configuración.
 */
public interface PayoutDispatcher {

    String provider();

    /** Llaves, cuenta origen y ambiente permitido configurados. */
    boolean configured();

    /** sandbox o production. */
    String environment();

    Dispatched dispatch(DispatchRequest request);

    /** Lote ya creado con esa referencia (tras un envío con resultado incierto). */
    Optional<String> findByReference(String reference);

    /** Estado de los pagos del lote en el proveedor. */
    List<TxStatus> transactions(String payoutId);

    /** Entidades del proveedor (para ligarlas al catálogo). */
    List<ProviderBank> banks();

    /** Con {@code key} (llave Bre-B) no van entidad, tipo ni número de cuenta. */
    record DispatchItem(String reference, long amountInCents, String name, String email, String legalIdType, String legalId,
                        String key, String bankId, String accountType, String accountNumber) {
    }

    record DispatchRequest(String reference, String idempotencyKey, List<DispatchItem> items) {
    }

    record Dispatched(String payoutId, String status) {
    }

    record TxStatus(String reference, String id, String status, String detail) {
    }

    record ProviderBank(String id, String name, String code) {
    }

    /** {@code uncertain}: no se sabe si el proveedor alcanzó a crear el lote (timeout, 5xx). */
    class DispatchException extends RuntimeException {
        private final boolean uncertain;

        public DispatchException(String message, boolean uncertain) {
            super(message);
            this.uncertain = uncertain;
        }

        public boolean uncertain() {
            return uncertain;
        }
    }
}
