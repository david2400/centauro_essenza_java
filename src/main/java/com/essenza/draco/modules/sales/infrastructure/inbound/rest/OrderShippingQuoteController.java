package com.essenza.draco.modules.sales.infrastructure.inbound.rest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.essenza.draco.modules.sales.application.services.order.OrderShippingQuoteService;
import com.essenza.draco.shared.common.logistics.ShippingQuotes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Cotización de envío para el formulario de la orden (tarifario de logística). */
@RestController
@RequestMapping("/sales/orders")
@Tag(name = "Orders")
public class OrderShippingQuoteController {

    private final OrderShippingQuoteService quotes;

    public OrderShippingQuoteController(OrderShippingQuoteService quotes) {
        this.quotes = quotes;
    }

    @Operation(summary = "Shipping options", description = "Opciones por transportadora (valor, peso cobrable y fechas) para el destino "
            + "y las líneas de la orden. No guarda nada. VENTAS.VER.")
    @PostMapping("/shipping-quote")
    public ShippingQuotes.QuoteResult quote(@RequestBody OrderShippingQuoteService.Request request) {
        return quotes.quote(request);
    }
}
