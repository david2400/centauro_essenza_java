package com.essenza.draco.modules.sales.application.dto.storefront;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** API pública de la tienda en línea (JSON en snake_case). Nada de costos, proveedores ni existencias exactas. */
public final class StorefrontDtos {

    private StorefrontDtos() {
    }

    public record PaymentOptionDto(Long id, String name, String provider) {
    }

    public record StoreInfoDto(String storeName, String currency, List<PaymentOptionDto> paymentMethods, List<String> docTypes,
                               BigDecimal shippingAmount) {
    }

    /**
     * Opción de envío para el comprador (sale del tarifario de logística). {@code id} se devuelve en el checkout para
     * elegirla; 0 = valor fijo de la tienda (sin tarifario configurado). {@code amount} ya incluye seguro; 0 si
     * {@code free}.
     */
    public record ShippingOptionDto(Long id, Long carrierId, String carrier, BigDecimal amount, boolean free, Integer minDays, Integer maxDays,
                                    java.time.LocalDate deliveryTo) {
    }

    /** {@code available} false = no se envía a ese lugar. Opciones ordenadas por valor y plazo. */
    public record ShippingQuoteDto(boolean available, List<ShippingOptionDto> options) {
    }

    /** Destino (ids del catálogo de parametros) y carrito, para cotizar con su peso real. */
    public record ShippingRequest(Long countryId, Long stateId, Long cityId, String city, String state, List<LineInput> lines) {
    }

    public record CategoryDto(Long id, String name, String slug, int products) {
    }

    /** {@code slug}: para la página pública de la marca (/marca/{slug}); vacío si la marca no tiene. */
    public record BrandDto(Long id, String name, int products, String slug) {
    }

    /** Página pública de una marca. */
    public record BrandProfileDto(Long id, String name, String slug, String description, int products) {
    }

    /**
     * Página pública de un vendedor del marketplace. Solo datos comerciales; {@code website} solo si es https;
     * {@code sinceYear}: año en que fue aprobado para vender.
     */
    public record SellerProfileDto(Long id, String name, String city, String description, String website, Integer sinceYear,
                                   int products) {
    }

    /** {@code priceFrom}: el menor precio entre la tienda y los vendedores; {@code sellers}: cuántos lo venden. */
    /**
     * Tarjeta del catálogo. {@code hoverImageUrl}: segunda foto (al pasar el mouse). {@code available}: unidades de la
     * opción más barata (máx. 10, como en la ficha). {@code quickAdd}: si el producto tiene una sola variante, lo
     * necesario para agregarlo al carrito desde la tarjeta (la opción más barata); null si hay que elegir variante.
     */
    public record ProductCardDto(Long id, String slug, String name, String imageUrl, String category, String brand,
                                 BigDecimal priceFrom, BigDecimal compareAtPrice, int sellers, String hoverImageUrl,
                                 int available, QuickAddDto quickAdd, BigDecimal ratingAverage, long ratingCount) {
    }

    /** Resumen de reseñas aprobadas: promedio (1 decimal), total y cuántas hay de 5, 4, 3, 2 y 1 estrellas. */
    public record ReviewSummaryDto(BigDecimal average, long count, List<Long> byStars) {
    }

    /**
     * Reseña pública: el autor va como "Ana P." (nunca el correo ni el apellido completo). {@code storeResponse}: respuesta
     * pública de la tienda (se escribe al moderar en el panel).
     */
    public record ReviewDto(long id, String author, int rating, String title, String comment, java.time.LocalDate date,
                            boolean verified, String storeResponse, java.time.LocalDate storeResponseDate) {

        public ReviewDto(long id, String author, int rating, String title, String comment, java.time.LocalDate date, boolean verified) {
            this(id, author, rating, title, comment, date, verified, null, null);
        }
    }

    public record ReviewPageDto(ReviewSummaryDto summary, List<ReviewDto> items, int page, int size, long total) {
    }

    /**
     * Productos de una compra que el comprador puede calificar desde su pedido (solo entregada). {@code status}: estado de
     * su reseña (PENDING, APPROVED, REJECTED) o nulo si aún no la escribe; {@code slug}: para enlazar al producto.
     */
    public record ReviewableDto(long productId, String name, boolean reviewed, String status, String slug) {

        public ReviewableDto(long productId, String name, boolean reviewed) {
            this(productId, name, reviewed, null, null);
        }
    }

    public record ReviewRequest(Long productId, Integer rating, String title, String comment) {
    }

    /** Agregar al carrito desde la tarjeta; el checkout vuelve a validar precio y existencias. */
    public record QuickAddDto(long skuId, Long offerId, String sellerName, BigDecimal price, int available, String skuName) {
    }

    public record PageDto<T>(List<T> items, int page, int size, long total) {
    }

    /** {@code offerId} vacío = lo vende la tienda. {@code available}: hasta 10 (no se publica el número exacto). */
    /** {@code sellerId}: vendedor de la oferta (para enlazar su página); vacío cuando vende la tienda. */
    public record OptionDto(Long offerId, String sellerName, boolean store, BigDecimal price, int available, Integer handlingDays,
                            String condition, Long sellerId) {

        public OptionDto(Long offerId, String sellerName, boolean store, BigDecimal price, int available, Integer handlingDays,
                         String condition) {
            this(offerId, sellerName, store, price, available, handlingDays, condition, null);
        }
    }

    public record SkuDto(Long skuId, String code, String name, String imageUrl, BigDecimal compareAtPrice, List<OptionDto> options) {
    }

    public record ProductDetailDto(Long id, String slug, String name, String description, String category, String brand,
                                   List<String> images, List<SkuDto> skus, String brandSlug) {

        public ProductDetailDto(Long id, String slug, String name, String description, String category, String brand,
                                List<String> images, List<SkuDto> skus) {
            this(id, slug, name, description, category, brand, images, skus, null);
        }
    }

    public record BuyerInput(String name, String docType, String docNumber, String email, String phone) {
    }

    /** Ubicación con los ids del catálogo de parametros (para cotizar) y sus nombres (para la guía y la factura). */
    public record AddressInput(String recipientName, String phone, String address, String addressDetail, String city, String state,
                               String notes, Long countryId, Long stateId, Long cityId, String country) {
    }

    public record LineInput(Long skuId, Long offerId, Integer quantity) {
    }

    /**
     * {@code shippingOptionId}: opción elegida de la cotización (vacío = la más económica). {@code couponCode}: cupón de
     * la tienda (aplica a su subpedido); el descuento lo calcula el servidor.
     */
    public record CheckoutRequest(BuyerInput buyer, AddressInput delivery, List<LineInput> lines, Long paymentMethodId,
                                  Boolean acceptTerms, Long shippingOptionId, String couponCode) {

        public CheckoutRequest(BuyerInput buyer, AddressInput delivery, List<LineInput> lines, Long paymentMethodId,
                               Boolean acceptTerms, Long shippingOptionId) {
            this(buyer, delivery, lines, paymentMethodId, acceptTerms, shippingOptionId, null);
        }
    }

    public record CouponRequest(String code, List<LineInput> lines) {
    }

    /**
     * Vista previa del cupón para el carrito (no reserva el cupón: el checkout lo vuelve a validar).
     * {@code discount}: sobre los productos que vende la tienda; {@code freeShipping}: además descuenta el envío.
     */
    public record CouponQuoteDto(boolean valid, String code, BigDecimal discount, boolean freeShipping, String message) {
    }

    /** {@code token}: secreto del enlace de seguimiento (solo se entrega aquí); {@code checkoutUrl}: pago en la pasarela. */
    public record CheckoutResultDto(String token, String purchaseNumber, BigDecimal total, String checkoutUrl) {
    }

    public record PayRequest(Long paymentMethodId) {
    }

    /** {@code skuId}, {@code offerId} y {@code productSlug}: para "volver a comprar" (el checkout revalida todo). */
    public record LineViewDto(String name, String skuCode, int quantity, BigDecimal unitPrice, BigDecimal total, Long skuId,
                              Long offerId, String productSlug) {

        public LineViewDto(String name, String skuCode, int quantity, BigDecimal unitPrice, BigDecimal total) {
            this(name, skuCode, quantity, unitPrice, total, null, null, null);
        }
    }

    /**
     * Entrega del subpedido: {@code pickup} con la tienda donde se retira, o envío con transportadora y guía (si ya se
     * despachó).
     */
    public record DeliveryViewDto(boolean pickup, String carrier, String trackingNumber, String pickupStore, String pickupAddress,
                                  String pickupHours) {
    }

    public record SubOrderViewDto(String orderNumber, String sellerName, boolean store, String state, BigDecimal total,
                                  List<LineViewDto> lines, DeliveryViewDto delivery) {
    }

    /** {@code checkoutUrl}: link de pago vigente; {@code canPay}: hay saldo y se puede generar uno nuevo. */
    public record OrderViewDto(String purchaseNumber, String status, String currency, BigDecimal total, BigDecimal pendingTotal,
                               Instant createdAt, String customerName, String customerEmail, String checkoutUrl, boolean canPay,
                               List<SubOrderViewDto> subOrders) {
    }
}
