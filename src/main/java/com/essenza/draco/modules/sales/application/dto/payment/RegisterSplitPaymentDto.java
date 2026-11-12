package com.essenza.draco.modules.sales.application.dto.payment;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cobro de una orden repartido entre varios medios (p. ej. parte en efectivo y parte con datáfono). */
public record RegisterSplitPaymentDto(
        @NotNull Long orderId,
        @NotNull @Size(min = 1, max = 10) @Valid List<Line> lines,
        @Size(max = 500) @Schema(description = "Notas comunes a todos los pagos") String notes,
        @Schema(description = "Turno de caja en el que se cobra (opcional)") Long cashSessionId) {

    /** Un medio y su parte del cobro. */
    public record Line(
            @NotNull Long paymentMethodId,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @Schema(description = "Efectivo recibido en esta parte (para calcular el cambio)") BigDecimal tenderedAmount,
            @Size(max = 120) String reference,
            @Schema(description = "Dejar esta parte pendiente de confirmación") Boolean pending) {
    }
}
