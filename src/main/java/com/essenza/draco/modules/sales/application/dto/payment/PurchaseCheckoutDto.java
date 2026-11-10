package com.essenza.draco.modules.sales.application.dto.payment;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Link de pago de la compra y el pago pendiente que se creó en cada subpedido. */
public record PurchaseCheckoutDto(
        @Schema(description = "Referencia que recibe la pasarela") String reference,
        BigDecimal amount,
        @Schema(description = "Link de pago de la pasarela") String checkoutUrl,
        List<PaymentDto> payments) {
}
