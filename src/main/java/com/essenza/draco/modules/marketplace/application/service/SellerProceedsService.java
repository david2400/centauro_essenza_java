package com.essenza.draco.modules.marketplace.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.marketplace.application.port.ConsignmentStore;
import com.essenza.draco.modules.marketplace.application.port.SellerLedgerStore;

/**
 * Lo que la tienda cobra por cuenta del vendedor externo (V28, {@code essenza.marketplace.collection=STORE}):
 * al pagarse una línea de su oferta, la tienda le debe lo que pagó el comprador (abono
 * MARKETPLACE_SALE en su estado de cuenta; la comisión va aparte). Cancelar lo pagado o
 * reembolsar al comprador por un reclamo lo reversa (MARKETPLACE_REVERSAL). Claves idempotentes.
 *
 * <p>Las ofertas con despacho de la plataforma (consignación) siguen con sus propios asientos
 * (CONSIGNMENT_SALE / CONSIGNMENT_REVERSAL). Con {@code collection=SELLER} no se abona nada: el
 * comprador le paga al vendedor por fuera.
 */
@Service
@Transactional(propagation = Propagation.REQUIRED)
public class SellerProceedsService {

    static final String SALE = "MARKETPLACE_SALE";
    static final String REVERSAL = "MARKETPLACE_REVERSAL";
    private static final String SYSTEM = "system";

    private final ConsignmentStore store;
    private final SellerLedgerStore ledger;
    private final Clock clock;
    private final boolean storeCollects;

    public SellerProceedsService(ConsignmentStore store, SellerLedgerStore ledger, Clock marketplaceClock,
                                 @Value("${essenza.marketplace.collection:STORE}") String collection) {
        this.store = store;
        this.ledger = ledger;
        this.clock = marketplaceClock;
        this.storeCollects = !"SELLER".equalsIgnoreCase(collection == null ? "" : collection.trim());
    }

    static String saleKey(long lineId) {
        return "MP-SALE:" + lineId;
    }

    /** Línea pagada (la llama OfferStockService dentro de la transacción del pago). */
    public void sold(long offerId, long lineId) {
        if (!storeCollects) {
            return;
        }
        ConsignmentStore.OfferRef offer = sellerOffer(offerId);
        if (offer == null) {
            return;
        }
        store.lineSale(lineId).ifPresent(sale -> post(offer.companyId(), offer.sellerId(), SALE, sale.total().negate(),
                reference(sale), saleKey(lineId), null, SYSTEM, null));
    }

    /** Línea pagada que se cancela: reversa lo abonado, si se abonó. */
    public void unsold(long offerId, long lineId) {
        ConsignmentStore.OfferRef offer = sellerOffer(offerId);
        if (offer == null || !store.ledgerKeyExists(offer.companyId(), saleKey(lineId))) {
            return;
        }
        // Nunca más de lo que le queda abonado por la orden (p. ej. si ya hubo un reembolso por reclamo).
        store.lineSale(lineId).ifPresent(sale -> post(offer.companyId(), offer.sellerId(), REVERSAL,
                sale.total().min(ledger.netSaleCredits(offer.companyId(), sale.orderId()).max(BigDecimal.ZERO)), reference(sale),
                "MP-CANCEL:" + lineId, null, SYSTEM, null));
    }

    /**
     * Reembolso al comprador decidido en un reclamo: si la tienda cobró la orden, el vendedor deja
     * de tener a favor ese valor (hasta lo que le queda abonado por la orden).
     *
     * @return lo reversado (0 si la tienda no cobró la orden o ya no queda nada abonado)
     */
    public BigDecimal refund(long companyId, long sellerId, long orderId, String reference, String idempotencyKey, BigDecimal amount,
                             String note, String actorId, String actorName) {
        if (amount == null || amount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal credited = ledger.netSaleCredits(companyId, orderId);
        BigDecimal value = amount.min(credited == null ? BigDecimal.ZERO : credited).setScale(2, RoundingMode.HALF_UP);
        if (value.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return post(companyId, sellerId, REVERSAL, value, reference, idempotencyKey, note, actorId, actorName) ? value : BigDecimal.ZERO;
    }

    private ConsignmentStore.OfferRef sellerOffer(long offerId) {
        ConsignmentStore.OfferRef offer = store.offer(offerId).orElse(null);
        return offer == null || "PLATFORM".equals(offer.fulfillment()) ? null : offer;
    }

    private static String reference(ConsignmentStore.LineSale sale) {
        return sale.orderNumber() != null ? sale.orderNumber() : "#" + sale.orderId();
    }

    private boolean post(long companyId, long sellerId, String type, BigDecimal amount, String reference, String key, String note,
                         String actorId, String actorName) {
        if (amount == null || amount.signum() == 0) {
            return false;
        }
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, clock.getZone());
        return ledger.post(companyId, new SellerLedgerStore.NewEntry(sellerId, type, amount, "COP", today, null, BigDecimal.ZERO,
                reference, null, null, key, note), actorId, actorName, now);
    }
}
