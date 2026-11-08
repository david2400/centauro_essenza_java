package com.essenza.draco.modules.marketplace.domain.offer;

/**
 * Reserva de unidades de una oferta por una línea de pedido. La cantidad se descuenta del
 * disponible al reservar; al liberar o devolver se repone.
 * <pre>
 * ACTIVE → COMMITTED (pedido pagado) | RELEASED (cancelado sin pagar) | EXPIRED (venció sin pagar)
 * COMMITTED → RETURNED (pedido pagado que se cancela)
 * </pre>
 */
public final class OfferReservation {

    public enum Status {
        ACTIVE, COMMITTED, RELEASED, EXPIRED, RETURNED
    }

    /** Qué hacer con la reserva y si la cantidad vuelve al disponible. */
    public record Move(Status to, boolean restoresStock) {
    }

    public enum Operation {
        COMMIT, RELEASE, EXPIRE, RESTOCK
    }

    private OfferReservation() {
    }

    /** {@code null} si la operación no aplica a ese estado (se ignora: las operaciones son repetibles). */
    public static Move move(Status from, Operation operation) {
        return switch (operation) {
            case COMMIT -> from == Status.ACTIVE ? new Move(Status.COMMITTED, false) : null;
            case RELEASE -> from == Status.ACTIVE ? new Move(Status.RELEASED, true) : null;
            case EXPIRE -> from == Status.ACTIVE ? new Move(Status.EXPIRED, true) : null;
            case RESTOCK -> from == Status.COMMITTED ? new Move(Status.RETURNED, true) : null;
        };
    }
}
