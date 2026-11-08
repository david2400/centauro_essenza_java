package com.essenza.draco.modules.sales.domain.model.purchase;

import java.util.Collection;

import com.essenza.draco.modules.sales.domain.model.OrderStatus;

/**
 * Estado de una compra multivendedor. No se guarda: se deriva de sus subpedidos (uno por
 * vendedor), de modo que nunca contradice lo que pasa en cada uno.
 * <ul>
 *   <li>CANCELLED: todos los subpedidos cancelados.</li>
 *   <li>PENDING: ningún subpedido vigente pagado.</li>
 *   <li>PARTIALLY_PAID: hay subpedidos vigentes pagados y otros pendientes. Una compra
 *   nunca figura pagada si solo se pagó una parte.</li>
 *   <li>PAID: todos los vigentes pagados, ninguno despachado ni entregado.</li>
 *   <li>IN_DELIVERY: todos pagados y al menos uno despachado o entregado.</li>
 *   <li>DELIVERED: todos los vigentes entregados.</li>
 * </ul>
 * Los subpedidos cancelados no cuentan como vigentes (ver {@link Counts#partiallyCancelled()}).
 */
public enum PurchaseStatus {
    PENDING,
    PARTIALLY_PAID,
    PAID,
    IN_DELIVERY,
    DELIVERED,
    CANCELLED;

    /** Conteos de los subpedidos por estado (sirven igual desde memoria o desde un GROUP BY). */
    public record Counts(int total, int cancelled, int pending, int shippedOrDelivered, int delivered) {

        public Counts {
            if (total < 0 || cancelled < 0 || pending < 0 || shippedOrDelivered < 0 || delivered < 0
                    || cancelled + pending > total || delivered > shippedOrDelivered
                    || shippedOrDelivered + pending + cancelled > total) {
                throw new IllegalArgumentException("Conteos de subpedidos inconsistentes.");
            }
        }

        public static Counts of(Collection<OrderStatus> statuses) {
            int cancelled = 0;
            int pending = 0;
            int shipped = 0;
            int delivered = 0;
            for (OrderStatus status : statuses) {
                switch (status) {
                    case CANCELLED -> cancelled++;
                    case PENDING -> pending++;
                    case SHIPPED -> shipped++;
                    case DELIVERED -> {
                        shipped++;
                        delivered++;
                    }
                    default -> {
                    }
                }
            }
            return new Counts(statuses.size(), cancelled, pending, shipped, delivered);
        }

        public int active() {
            return total - cancelled;
        }

        /** Hay subpedidos cancelados y otros vigentes. */
        public boolean partiallyCancelled() {
            return cancelled > 0 && active() > 0;
        }
    }

    public static PurchaseStatus derive(Collection<OrderStatus> subOrders) {
        return derive(Counts.of(subOrders));
    }

    public static PurchaseStatus derive(Counts counts) {
        int active = counts.active();
        if (counts.total() == 0) {
            return PENDING;
        }
        if (active == 0) {
            return CANCELLED;
        }
        if (counts.pending() == active) {
            return PENDING;
        }
        if (counts.pending() > 0) {
            return PARTIALLY_PAID;
        }
        if (counts.delivered() == active) {
            return DELIVERED;
        }
        return counts.shippedOrDelivered() > 0 ? IN_DELIVERY : PAID;
    }
}
