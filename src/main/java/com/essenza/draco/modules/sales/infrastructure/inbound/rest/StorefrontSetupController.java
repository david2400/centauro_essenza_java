package com.essenza.draco.modules.sales.infrastructure.inbound.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontSetupDtos.ConnectionTestDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontSetupDtos.SetupDto;
import com.essenza.draco.modules.sales.application.services.storefront.StorefrontSetupService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Configuración de la tienda en línea para el dueño (panel). Requiere token; nunca expone llaves. */
@RestController
@RequestMapping("/sales/storefront/setup")
@Tag(name = "Storefront setup", description = "Qué falta para vender y cobrar en la tienda en línea")
public class StorefrontSetupController {

    private final StorefrontSetupService setup;

    public StorefrontSetupController(StorefrontSetupService setup) {
        this.setup = setup;
    }

    @Operation(summary = "Estado", description = "Tienda encendida, empresa, medios de pago y pasarelas, webhook, catálogo visible y avisos "
            + "al comprador. VENTAS.VER.")
    @GetMapping
    public SetupDto get() {
        return setup.setup();
    }

    @Operation(summary = "Probar pasarela", description = "Consulta de solo lectura a la pasarela (no crea cobros). SANDBOX, WOMPI o "
            + "MERCADO_PAGO. VENTAS.GESTIONAR.")
    @PostMapping("/gateways/{provider}/test")
    public ConnectionTestDto test(@PathVariable String provider) {
        return setup.testGateway(provider);
    }
}
