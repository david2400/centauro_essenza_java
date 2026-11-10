package com.essenza.draco.modules.sales.application.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Link de pago único por el saldo de todos los subpedidos de una compra. */
public record StartPurchaseCheckoutDto(
        @NotNull Long paymentMethodId,
        @Email @Size(max = 160) String payerEmail,
        @Schema(description = "A dónde vuelve el cliente al terminar (vacío = la configurada)") @Size(max = 1000) String returnUrl) {
}
