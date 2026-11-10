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

    /** {@code priceFrom}: el menor precio entre la tienda y los vendedores; {@code sellers}: cuántos lo venden. */
    public record ProductCardDto(Long id, String slug, String name, String imageUrl, String category, String brand,
                                 BigDecimal priceFrom, BigDecimal compareAtPrice, int sellers) {
    }

    public record PageDto<T>(List<T> items, int page, int size, long total) {
    }

    /** {@code offerId} vacío = lo vende la tienda. {@code available}: hasta 10 (no se publica el número exacto). */
    public record OptionDto(Long offerId, String sellerName, boolean store, BigDecimal price, int available, Integer handlingDays,
                            String condition) {
    }

    public record SkuDto(Long skuId, String code, String name, String imageUrl, BigDecimal compareAtPrice, List<OptionDto> options) {
    }

    public record ProductDetailDto(Long id, String slug, String name, String description, String category, String brand,
                                   List<String> images, List<SkuDto> skus) {
    }

    public record BuyerInput(String name, String docType, String docNumber, String email, String phone) {
    }

    /** Ubicación con los ids del catálogo de parametros (para cotizar) y sus nombres (para la guía y la factura). */
    public record AddressInput(String recipientName, String phone, String address, String addressDetail, String city, String state,
                               String notes, Long countryId, Long stateId, Long cityId, String country) {
    }

    public record LineInput(Long skuId, Long offerId, Integer quantity) {
    }

    /** {@code shippingOptionId}: opción elegida de la cotización (vacío = la más económica). */
    public record CheckoutRequest(BuyerInput buyer, AddressInput delivery, List<LineInput> lines, Long paymentMethodId,
                                  Boolean acceptTerms, Long shippingOptionId) {
    }

    /** {@code token}: secreto del enlace de seguimiento (solo se entrega aquí); {@code checkoutUrl}: pago en la pasarela. */
    public record CheckoutResultDto(String token, String purchaseNumber, BigDecimal total, String checkoutUrl) {
    }

    public record PayRequest(Long paymentMethodId) {
    }

    public record LineViewDto(String name, String skuCode, int quantity, BigDecimal unitPrice, BigDecimal total) {
    }

    public record SubOrderViewDto(String orderNumber, String sellerName, boolean store, String state, BigDecimal total,
                                  List<LineViewDto> lines) {
    }

    /** {@code checkoutUrl}: link de pago vigente; {@code canPay}: hay saldo y se puede generar uno nuevo. */
    public record OrderViewDto(String purchaseNumber, String status, String currency, BigDecimal total, BigDecimal pendingTotal,
                               Instant createdAt, String customerName, String customerEmail, String checkoutUrl, boolean canPay,
                               List<SubOrderViewDto> subOrders) {
    }
}
