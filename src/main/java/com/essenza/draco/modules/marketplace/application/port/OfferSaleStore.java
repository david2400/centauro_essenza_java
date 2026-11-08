package com.essenza.draco.modules.marketplace.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.essenza.draco.modules.marketplace.domain.offer.OfferReservation;

/** Venta de ofertas: disponibilidad y reservas por línea de pedido. */
public interface OfferSaleStore {

    /** Oferta con su vendedor, bloqueada (FOR UPDATE). */
    Optional<SaleOffer> lockForSale(long companyId, long offerId);

    /** Ofertas activas con disponible de vendedores externos aprobados, por precio. */
    List<SaleOffer> forSale(long companyId, long skuId);

    /** Vendedor propio no cerrado. */
    Optional<SaleOffer.Seller> ownSeller(long companyId);

    /** @return false si no alcanza el disponible */
    boolean decrement(long companyId, long offerId, int quantity, Instant now);

    void increment(long companyId, long offerId, int quantity, Instant now);

    long insertReservation(long companyId, long offerId, long orderId, long lineId, int quantity, Instant now);

    /** Reservas del pedido bloqueadas, en orden de oferta (orden fijo de bloqueo). */
    List<ReservationRow> reservations(long orderId, boolean forUpdate);

    void setReservationStatus(long reservationId, OfferReservation.Status status, Instant now);

    record SaleOffer(long id, long companyId, Seller seller, long skuId, String currency, BigDecimal price,
                     int availableQuantity, int handlingDays, String condition, String status, String fulfillment) {

        public record Seller(long id, String name, String kind, String status) {
        }
    }

    record ReservationRow(long id, long companyId, long offerId, long orderId, long lineId, int quantity,
                          OfferReservation.Status status) {
    }
}
