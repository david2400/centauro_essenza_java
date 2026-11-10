package com.essenza.draco.modules.sales.infrastructure.inbound.rest;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.SaveShippingRateRequest;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ShippingRateDto;
import com.essenza.draco.modules.sales.application.services.storefront.ShippingRateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Tarifas de envío de la tienda en línea (panel). Empresa y permisos desde el token. */
@RestController
@RequestMapping("/sales/storefront/shipping-rates")
@Tag(name = "Storefront shipping", description = "Tarifas de envío de la tienda en línea por departamento y ciudad")
public class ShippingRateController {

    private final ShippingRateService rates;

    public ShippingRateController(ShippingRateService rates) {
        this.rates = rates;
    }

    @Operation(summary = "List", description = "Tarifas de la empresa. VENTAS.VER.")
    @GetMapping
    public List<ShippingRateDto> list() {
        return rates.list();
    }

    @Operation(summary = "Create", description = "Departamento y ciudad vacíos = tarifa general. VENTAS.GESTIONAR.")
    @PostMapping
    public ResponseEntity<ShippingRateDto> create(@RequestBody SaveShippingRateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rates.save(null, request));
    }

    @Operation(summary = "Update", description = "Valor, envío gratis, días y activa. VENTAS.GESTIONAR.")
    @PutMapping("/{id}")
    public ShippingRateDto update(@PathVariable long id, @RequestBody SaveShippingRateRequest request) {
        return rates.save(id, request);
    }
}
