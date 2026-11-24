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

    /**
     * Filtros del catálogo. {@code like}: patrón ya escapado; {@code minPrice}/{@code maxPrice} se comparan con el
     * "desde" del producto (el menor precio entre la tienda, si tiene unidades, y las ofertas activas).
     */
    record SearchFilter(String like, Long categoryId, Long brandId, BigDecimal minPrice, BigDecimal maxPrice, Long sellerId) {

        public SearchFilter(String like, Long categoryId, Long brandId, BigDecimal minPrice, BigDecimal maxPrice) {
            this(like, categoryId, brandId, minPrice, maxPrice, null);
        }
    }

    /** {@code sort}: NEWEST, NAME, PRICE_ASC o PRICE_DESC. */
    ProductPage search(long companyId, String currency, SearchFilter filter, String sort, int offset, int limit);

    default ProductPage search(long companyId, String currency, String like, Long categoryId, String sort, int offset, int limit) {
        return search(companyId, currency, new SearchFilter(like, categoryId, null, null, null), sort, offset, limit);
    }

    Optional<Long> productBySlug(String slugOrId);

    record ProductRow(long id, String slug, String name, String description, String imageUrl, String category, String brand,
                      String brandSlug) {

        public ProductRow(long id, String slug, String name, String description, String imageUrl, String category, String brand) {
            this(id, slug, name, description, imageUrl, category, brand, null);
        }
    }

    Map<Long, ProductRow> products(Collection<Long> ids);

    record SkuRow(long skuId, long productId, String code, String name, String imageUrl, boolean isDefault, BigDecimal price,
                  BigDecimal compareAtPrice, long ownAvailable) {
    }

    /** SKU vendibles (activos, producto activo) de los productos. */
    List<SkuRow> skus(Collection<Long> productIds);

    record OfferRow(long offerId, long skuId, String sellerName, BigDecimal price, long available, Integer handlingDays,
                    String condition, Long sellerId) {

        public OfferRow(long offerId, long skuId, String sellerName, BigDecimal price, long available, Integer handlingDays,
                        String condition) {
            this(offerId, skuId, sellerName, price, available, handlingDays, condition, null);
        }
    }

    List<OfferRow> offers(long companyId, String currency, Collection<Long> skuIds);

    Map<Long, List<String>> images(Collection<Long> productIds);

    record CategoryRow(long id, String name, String slug, int products) {
    }

    List<CategoryRow> categories(long companyId, String currency);

    record BrandRow(long id, String name, int products, String slug) {

        public BrandRow(long id, String name, int products) {
            this(id, name, products, null);
        }
    }

    /** Marcas con productos a la venta. */
    List<BrandRow> brands(long companyId, String currency);

    /** Perfil público de una marca con productos a la venta (por slug o id). */
    record BrandProfileRow(long id, String name, String slug, String description, int products) {
    }

    default Optional<BrandProfileRow> brand(long companyId, String currency, String slugOrId) {
        return Optional.empty();
    }

    /**
     * Perfil público de un vendedor externo APROBADO de la empresa. Solo datos comerciales: nombre comercial, ciudad,
     * descripción, sitio web y desde cuándo vende. Nunca documento, correo, teléfono ni dirección.
     */
    record SellerRow(long id, String name, String city, String description, String website, java.time.LocalDateTime approvedAt,
                     int products) {
    }

    default Optional<SellerRow> seller(long companyId, String currency, long sellerId) {
        return Optional.empty();
    }

    record PaymentOption(long id, String name, String provider) {
    }

    /** Pasarelas en línea activas. */
    List<PaymentOption> gateways();

    /** Línea de una orden. {@code skuId}, {@code offerId} y {@code productSlug}: para "volver a comprar". */
    record LineRow(long orderId, String name, String skuCode, int quantity, BigDecimal unitPrice, BigDecimal total, Long skuId,
                   Long offerId, String productSlug) {

        public LineRow(long orderId, String name, String skuCode, int quantity, BigDecimal unitPrice, BigDecimal total) {
            this(orderId, name, skuCode, quantity, unitPrice, total, null, null, null);
        }
    }

    List<LineRow> orderLines(Collection<Long> orderIds);

    /**
     * Entrega de cada orden para el comprador: retiro en tienda (con la tienda) o envío (con transportadora y guía si ya
     * se despachó).
     */
    record DeliveryRow(long orderId, boolean pickup, String carrier, String trackingNumber, String pickupStore,
                       String pickupAddress, String pickupHours) {
    }

    Map<Long, DeliveryRow> deliveries(Collection<Long> orderIds);

    record SkuRef(long skuId, long productId, Long categoryId, BigDecimal price) {
    }

    /** Producto, categoría y precio propio de los SKU vendibles (para cotizar cupones de la tienda). */
    Map<Long, SkuRef> skuRefs(Collection<Long> skuIds);

    /** Precio de venta propio de cada SKU vendible. */
    Map<Long, BigDecimal> ownPrices(Collection<Long> skuIds);

    /** Precio de cada oferta activa de la empresa en la moneda. */
    Map<Long, BigDecimal> offerPrices(long companyId, String currency, Collection<Long> offerIds);

    /** Link de pago pendiente y vigente de cualquiera de las órdenes. */
    Optional<String> openCheckout(Collection<Long> orderIds, Instant now);
}
