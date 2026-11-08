package com.essenza.draco.modules.operations.infrastructure.inbound.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.essenza.draco.modules.operations.application.dto.AlertDtos.AlertsDto;
import com.essenza.draco.modules.operations.application.service.OperationsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Centro de operaciones. Cada alerta exige el permiso de ver su módulo. */
@RestController
@RequestMapping("/operations")
@Tag(name = "Operations", description = "Alertas operativas: despachos, vendedores, consignación, facturas, devoluciones y stock")
public class OperationsController {

    private final OperationsService operations;

    public OperationsController(OperationsService operations) {
        this.operations = operations;
    }

    @Operation(summary = "Alerts", description = "Alertas visibles para el usuario con conteo, monto y hasta 5 ejemplos.")
    @GetMapping("/alerts")
    public AlertsDto alerts() {
        return operations.alerts();
    }
}
