package com.essenza.draco.modules.marketplace.infrastructure.inbound.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.essenza.draco.modules.marketplace.application.dto.SellerInvoiceDtos.IssueRequest;
import com.essenza.draco.modules.marketplace.application.dto.SellerInvoiceDtos.IssueResultDto;
import com.essenza.draco.modules.marketplace.application.dto.SellerInvoiceDtos.PeriodPreviewDto;
import com.essenza.draco.modules.marketplace.application.service.SellerInvoiceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Factura mensual de la plataforma a cada vendedor (comisiones y cargos). Empresa y permisos desde el token. */
@RestController
@RequestMapping("/marketplace/billing/seller-invoices")
@Tag(name = "Seller invoices", description = "Factura electrónica mensual de comisiones y cargos a los vendedores externos")
public class SellerInvoiceController {

    private final SellerInvoiceService invoices;

    public SellerInvoiceController(SellerInvoiceService invoices) {
        this.invoices = invoices;
    }

    @Operation(summary = "Preview", description = "Conceptos por vendedor del periodo (AAAA-MM), lo recaudado por su cuenta y su "
            + "factura si ya la tiene. COMISIONES.VER.")
    @GetMapping
    public PeriodPreviewDto preview(@RequestParam("period") String period) {
        return invoices.preview(period);
    }

    @Operation(summary = "Issue", description = "Emite la factura del mes cerrado a los vendedores elegidos (vacío = todos los "
            + "pendientes). Una vigente por vendedor y periodo. COMISIONES.GESTIONAR.")
    @PostMapping
    public IssueResultDto issue(@RequestBody IssueRequest request) {
        return invoices.issue(request);
    }
}
