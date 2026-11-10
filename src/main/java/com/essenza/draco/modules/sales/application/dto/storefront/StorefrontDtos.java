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

    /** {@code available} false = no se envía a ese lugar. {@code free}: el subtotal alcanzó el envío gratis. */
    public record ShippingQuoteDto(boolean available, BigDecimal amount, BigDecimal freeFrom, Integer minDays, Integer maxDays,
                                   boolean free) {
    }

    /** Tarifa del panel. Departamento y ciudad vacíos = tarifa general. */
    public record ShippingRateDto(Long id, String state, String city, BigDecimal amount, BigDecimal freeFrom, Integer minDays,
                                  Integer maxDays, boolean active, String updatedByName, Instant updatedAt) {
    }

    public record SaveShippingRateRequest(String state, String city, BigDecimal amount, BigDecimal freeFrom, Integer minDays,
                                          Integer maxDays, Boolean active) {
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

    public record AddressInput(String recipientName, String phone, String address, String addressDetail, String city, String state,
                               String notes) {
    }

    public record LineInput(Long skuId, Long offerId, Integer quantity) {
    }

    public record CheckoutRequest(BuyerInput buyer, AddressInput delivery, List<LineInput> lines, Long paymentMethodId,
                                  Boolean acceptTerms) {
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
