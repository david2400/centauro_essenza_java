package com.essenza.draco.modules.marketplace.infrastructure.inbound.rest;

import java.time.LocalDate;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.essenza.draco.modules.marketplace.application.dto.BillingDtos.AccrualRunDto;
import com.essenza.draco.modules.marketplace.application.dto.BillingDtos.AdjustmentRequest;
import com.essenza.draco.modules.marketplace.application.dto.BillingDtos.BillingSummaryDto;
import com.essenza.draco.modules.marketplace.application.dto.BillingDtos.CommissionDto;
import com.essenza.draco.modules.marketplace.application.dto.BillingDtos.FeeRunDto;
import com.essenza.draco.modules.marketplace.application.dto.BillingDtos.SellerAccountDto;
import com.essenza.draco.modules.marketplace.application.dto.BillingDtos.SellerPaymentRequest;
import com.essenza.draco.modules.marketplace.application.port.CommissionStore.CommissionFilter;
import com.essenza.draco.modules.marketplace.application.service.CommissionService;
import com.essenza.draco.modules.marketplace.application.service.SellerAccountService;
import com.essenza.draco.shared.common.domain.dto.PageResponse;
import com.essenza.draco.shared.common.web.PageableFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Comisiones, mensualidades y estado de cuenta de los vendedores. Empresa y permisos desde el token. */
@RestController
@RequestMapping("/marketplace")
@Tag(name = "Marketplace · Billing", description = "Comisiones por venta, mensualidades y cuentas de los vendedores")
public class BillingController {

    private static final Set<String> SORTABLE = Set.of("id", "paidOn", "accrueOn", "amount", "status", "sellerName", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"));

    private final CommissionService commissions;
    private final SellerAccountService accounts;

    public BillingController(CommissionService commissions, SellerAccountService accounts) {
        this.commissions = commissions;
        this.accounts = accounts;
    }

    @Operation(summary = "Commissions", description = "Comisión por subpedido de vendedor externo. Filtros q (orden o "
            + "vendedor), sellerId y status (PENDING, ACCRUED, VOIDED, REVIEW). COMISIONES.VER.")
    @GetMapping("/billing/commissions/search")
    public PageResponse<CommissionDto> commissions(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "sellerId", required = false) Long sellerId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sort", required = false) String sort) {
        return PageResponse.from(commissions.search(new CommissionFilter(q, sellerId, status),
                PageableFactory.of(page, size, sort, SORTABLE, DEFAULT_SORT)));
    }

    @Operation(summary = "Summary", description = "Comisiones por estado y saldo por cobrar a vendedores. COMISIONES.VER.")
    @GetMapping("/billing/summary")
    public BillingSummaryDto summary() {
        return commissions.summary();
    }

    @Operation(summary = "Accrue due", description = "Devenga ya las comisiones cuya fecha llegó (también corre a diario). "
            + "COMISIONES.GESTIONAR.")
    @PostMapping("/billing/accrue")
    public AccrualRunDto accrue() {
        return commissions.accrueDueNow();
    }

    @Operation(summary = "Recalculate", description = "Recalcula una comisión en revisión con la vigencia del día del pago "
            + "(p. ej. tras asignar el plan que faltaba). COMISIONES.GESTIONAR.")
    @PostMapping("/billing/commissions/{id}/recalculate")
    public CommissionDto recalculate(@PathVariable long id) {
        return commissions.recalculate(id);
    }

    @Operation(summary = "Monthly fees", description = "Genera las mensualidades del ciclo AAAA-MM. Repetir no duplica. "
            + "COMISIONES.GESTIONAR.")
    @PostMapping("/billing/fees/{cycle}")
    public FeeRunDto fees(@PathVariable String cycle) {
        return accounts.generateFees(cycle);
    }

    @Operation(summary = "Seller account", description = "Estado de cuenta: cargos, abonos, saldo, vencido e interés de mora "
            + "estimado a la fecha. COMISIONES.VER.")
    @GetMapping("/sellers/{id}/account")
    public SellerAccountDto account(@PathVariable long id,
                                    @RequestParam(value = "asOf", required = false)
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return accounts.statement(id, asOf);
    }

    @Operation(summary = "Settlement", description = "Liquidación al vendedor (la tienda le paga lo cobrado en consignación, "
            + "hasta su saldo a favor). COMISIONES.GESTIONAR.")
    @PostMapping("/sellers/{id}/account/settlements")
    public SellerAccountDto settlement(@PathVariable long id, @RequestBody SellerPaymentRequest request) {
        return accounts.registerSettlement(id, request);
    }

    @Operation(summary = "Register seller payment", description = "Pago recibido del vendedor (con referencia). "
            + "COMISIONES.GESTIONAR.")
    @PostMapping("/sellers/{id}/account/payments")
    public SellerAccountDto payment(@PathVariable long id, @RequestBody SellerPaymentRequest request) {
        return accounts.registerPayment(id, request);
    }

    @Operation(summary = "Manual adjustment", description = "Ajuste con motivo (positivo suma a la deuda). COMISIONES.AJUSTAR.")
    @PostMapping("/sellers/{id}/account/adjustments")
    public SellerAccountDto adjustment(@PathVariable long id, @RequestBody AdjustmentRequest request) {
        return accounts.adjust(id, request);
    }
}
