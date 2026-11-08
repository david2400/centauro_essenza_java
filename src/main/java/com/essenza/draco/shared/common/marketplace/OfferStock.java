package com.essenza.draco.shared.common.marketplace;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Contrato entre ventas y marketplace para vender ofertas de vendedores externos. La cantidad
 * disponible de la oferta hace de inventario del vendedor: reservar la descuenta de inmediato
 * (nadie más la vende), pagar la confirma, cancelar o vencer la devuelve. Se ejecuta dentro de
 * la transacción de ventas. Lo implementa el marketplace.
 */
public interface OfferStock {

    /**
     * Oferta lista para venderse en la empresa, bloqueada hasta el fin de la transacción: activa,
     * de un vendedor externo aprobado. La vigencia del SKU la valida ventas con el catálogo.
     *
     * @throws com.essenza.draco.shared.exceptions.NotFoundException si no existe en la empresa
     * @throws com.essenza.draco.shared.exceptions.ConflictException si no está a la venta
     */
    OfferQuote lockForSale(long companyId, Long offerId);

    /** Ofertas a la venta de un SKU en la empresa, de menor a mayor precio. */
    List<OfferQuote> forSale(long companyId, Long skuId);

    /** Vendedor propio de la empresa, si existe. */
    Optional<SellerRef> ownSeller(long companyId);

    /** Reserva {@code quantity} unidades de la oferta para la línea (las descuenta del disponible). */
    void reserveLine(long companyId, Long offerId, Long orderId, Long lineId, int quantity);

    /** Confirma lo reservado por la orden (pagada). */
    void commitOrder(Long orderId);

    /** Devuelve al disponible lo reservado por la orden sin pagar; {@code expired} marca la reserva como vencida. */
    void releaseOrder(Long orderId, boolean expired);

    /** Devuelve al disponible lo confirmado por una orden pagada que se cancela. */
    void restockOrder(Long orderId);

    /** Reservas de ofertas de la orden (vacío si no vende ofertas). */
    List<Reservation> reservations(Long orderId);

    /** {@code consignment}: la despacha la tienda desde la mercancía del vendedor en consignación. */
    record OfferQuote(Long offerId, Long sellerId, String sellerName, Long skuId, String currency, BigDecimal price,
                      int availableQuantity, int handlingDays, String condition, boolean consignment) {
    }

    record SellerRef(Long id, String name) {
    }

    record Reservation(Long id, Long offerId, Long lineId, int quantity, String status) {
    }

    /** Sin marketplace (pruebas o despliegues sin el módulo): no hay ofertas a la venta. */
    OfferStock NONE = new OfferStock() {
        @Override
        public OfferQuote lockForSale(long companyId, Long offerId) {
            throw new com.essenza.draco.shared.exceptions.NotFoundException("No se encontró la oferta " + offerId + ".");
        }

        @Override
        public List<OfferQuote> forSale(long companyId, Long skuId) {
            return List.of();
        }

        @Override
        public Optional<SellerRef> ownSeller(long companyId) {
            return Optional.empty();
        }

        @Override
        public void reserveLine(long companyId, Long offerId, Long orderId, Long lineId, int quantity) {
            throw new IllegalStateException("Sin marketplace no se reservan ofertas.");
        }

        @Override
        public void commitOrder(Long orderId) {
        }

        @Override
        public void releaseOrder(Long orderId, boolean expired) {
        }

        @Override
        public void restockOrder(Long orderId) {
        }

        @Override
        public List<Reservation> reservations(Long orderId) {
            return List.of();
        }
    };
}
