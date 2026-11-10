package com.essenza.draco.modules.sales.application.output.storefront;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lecturas de la tienda pública: productos activos con algo para vender (stock propio en bodegas que venden en
 * línea u ofertas activas de vendedores aprobados de la empresa), sus SKU y opciones, categorías y medios de pago.
 */
public interface StorefrontReadStore {

    record ProductPage(List<Long> ids, long total) {
    }

    ProductPage search(long companyId, String currency, String like, Long categoryId, String sort, int offset, int limit);

    Optional<Long> productBySlug(String slugOrId);

    record ProductRow(long id, String slug, String name, String description, String imageUrl, String category, String brand) {
    }

    Map<Long, ProductRow> products(Collection<Long> ids);

    record SkuRow(long skuId, long productId, String code, String name, String imageUrl, boolean isDefault, BigDecimal price,
                  BigDecimal compareAtPrice, long ownAvailable) {
    }

    /** SKU vendibles (activos, producto activo) de los productos. */
    List<SkuRow> skus(Collection<Long> productIds);

    record OfferRow(long offerId, long skuId, String sellerName, BigDecimal price, long available, Integer handlingDays,
                    String condition) {
    }

    List<OfferRow> offers(long companyId, String currency, Collection<Long> skuIds);

    Map<Long, List<String>> images(Collection<Long> productIds);

    record CategoryRow(long id, String name, String slug, int products) {
    }

    List<CategoryRow> categories(long companyId, String currency);

    record PaymentOption(long id, String name, String provider) {
    }

    /** Pasarelas en línea activas. */
    List<PaymentOption> gateways();

    record LineRow(long orderId, String name, String skuCode, int quantity, BigDecimal unitPrice, BigDecimal total) {
    }

    List<LineRow> orderLines(Collection<Long> orderIds);

    /** Precio de venta propio de cada SKU vendible. */
    Map<Long, BigDecimal> ownPrices(Collection<Long> skuIds);

    /** Precio de cada oferta activa de la empresa en la moneda. */
    Map<Long, BigDecimal> offerPrices(long companyId, String currency, Collection<Long> offerIds);

    /** Link de pago pendiente y vigente de cualquiera de las órdenes. */
    Optional<String> openCheckout(Collection<Long> orderIds, Instant now);
}
