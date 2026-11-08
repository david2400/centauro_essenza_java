package com.essenza.draco.modules.marketplace.application.service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.marketplace.application.port.OfferSaleStore;
import com.essenza.draco.modules.marketplace.application.port.OfferSaleStore.ReservationRow;
import com.essenza.draco.modules.marketplace.application.port.OfferSaleStore.SaleOffer;
import com.essenza.draco.modules.marketplace.domain.offer.OfferReservation;
import com.essenza.draco.modules.marketplace.domain.offer.OfferReservation.Move;
import com.essenza.draco.modules.marketplace.domain.offer.OfferReservation.Operation;
import com.essenza.draco.shared.common.marketplace.OfferStock;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;

/**
 * Implementa {@link OfferStock} para ventas. La empresa siempre llega del actor ya validado en
 * ventas; las reservas guardan su empresa y las operaciones posteriores (pagar, cancelar, vencer)
 * solo tocan las reservas de ese pedido.
 */
@Service
@Transactional
public class OfferStockService implements OfferStock {

    private final OfferSaleStore store;
    private final Clock clock;
    private final ConsignmentService consignment;

    /** Para pruebas sin consignación. */
    public OfferStockService(OfferSaleStore store, Clock marketplaceClock) {
        this(store, marketplaceClock, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OfferStockService(OfferSaleStore store, Clock marketplaceClock, ConsignmentService consignment) {
        this.store = store;
        this.clock = marketplaceClock;
        this.consignment = consignment;
    }

    @Override
    public OfferQuote lockForSale(long companyId, Long offerId) {
        if (offerId == null) {
            throw new IllegalArgumentException("Indica la oferta.");
        }
        SaleOffer offer = store.lockForSale(companyId, offerId)
                .orElseThrow(() -> new NotFoundException("No se encontró la oferta " + offerId + "."));
        String seller = offer.seller().name();
        if (!"EXTERNAL".equals(offer.seller().kind())) {
            throw new ConflictException("La oferta " + offerId + " no es de un vendedor externo.");
        }
        if (!"APPROVED".equals(offer.seller().status())) {
            throw new ConflictException("El vendedor " + seller + " no está aprobado: su oferta no se puede vender.");
        }
        if (!"ACTIVE".equals(offer.status())) {
            throw new ConflictException("La oferta de " + seller + " no está activa.");
        }
        return quote(offer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OfferQuote> forSale(long companyId, Long skuId) {
        return skuId == null ? List.of() : store.forSale(companyId, skuId).stream().map(OfferStockService::quote).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SellerRef> ownSeller(long companyId) {
        return store.ownSeller(companyId).map(seller -> new SellerRef(seller.id(), seller.name()));
    }

    @Override
    public void reserveLine(long companyId, Long offerId, Long orderId, Long lineId, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
        }
        OfferQuote offer = lockForSale(companyId, offerId);
        if (!store.decrement(companyId, offerId, quantity, clock.instant())) {
            throw new ConflictException("Solo quedan " + offer.availableQuantity() + " unidades en la oferta de "
                    + offer.sellerName() + ".");
        }
        store.insertReservation(companyId, offerId, orderId, lineId, quantity, clock.instant());
    }

    @Override
    public void commitOrder(Long orderId) {
        apply(orderId, Operation.COMMIT);
    }

    @Override
    public void releaseOrder(Long orderId, boolean expired) {
        apply(orderId, expired ? Operation.EXPIRE : Operation.RELEASE);
    }

    @Override
    public void restockOrder(Long orderId) {
        apply(orderId, Operation.RESTOCK);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reservation> reservations(Long orderId) {
        return store.reservations(orderId, false).stream()
                .map(r -> new Reservation(r.id(), r.offerId(), r.lineId(), r.quantity(), r.status().name()))
                .toList();
    }

    private void apply(Long orderId, Operation operation) {
        if (orderId == null) {
            return;
        }
        for (ReservationRow reservation : store.reservations(orderId, true)) {
            Move move = OfferReservation.move(reservation.status(), operation);
            if (move == null) {
                continue;
            }
            if (move.restoresStock()) {
                store.increment(reservation.companyId(), reservation.offerId(), reservation.quantity(), clock.instant());
            }
            store.setReservationStatus(reservation.id(), move.to(), clock.instant());
            // Ofertas con despacho de la plataforma: pagar descuenta la consignación y cancelar lo pagado la repone.
            if (consignment != null && move.to() == OfferReservation.Status.COMMITTED) {
                consignment.sold(reservation.offerId(), reservation.orderId(), reservation.lineId(), reservation.quantity());
            } else if (consignment != null && move.to() == OfferReservation.Status.RETURNED) {
                consignment.unsold(reservation.offerId(), reservation.orderId(), reservation.lineId());
            }
        }
    }

    private static OfferQuote quote(SaleOffer offer) {
        return new OfferQuote(offer.id(), offer.seller().id(), offer.seller().name(), offer.skuId(), offer.currency(),
                offer.price(), offer.availableQuantity(), offer.handlingDays(), offer.condition(),
                "PLATFORM".equals(offer.fulfillment()));
    }
}
