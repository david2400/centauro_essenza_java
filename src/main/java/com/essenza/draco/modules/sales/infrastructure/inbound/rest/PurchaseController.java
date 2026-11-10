package com.essenza.draco.modules.sales.infrastructure.inbound.rest;

import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.essenza.draco.modules.sales.application.dto.payment.PurchaseCheckoutDto;
import com.essenza.draco.modules.sales.application.dto.payment.StartPurchaseCheckoutDto;
import com.essenza.draco.modules.sales.application.dto.purchase.PurchaseDtos.CreatePurchaseRequest;
import com.essenza.draco.modules.sales.application.dto.purchase.PurchaseDtos.OrderPurchaseDto;
import com.essenza.draco.modules.sales.application.dto.purchase.PurchaseDtos.PurchaseDetailDto;
import com.essenza.draco.modules.sales.application.dto.purchase.PurchaseDtos.PurchaseSummaryDto;
import com.essenza.draco.modules.sales.application.dto.purchase.PurchaseDtos.SkuOptionsDto;
import com.essenza.draco.modules.sales.application.output.repository.PurchaseLedger.PurchaseFilter;
import com.essenza.draco.modules.sales.application.services.payment.StartPurchaseCheckoutService;
import com.essenza.draco.modules.sales.application.services.purchase.PurchaseService;
import com.essenza.draco.shared.common.domain.dto.PageResponse;
import com.essenza.draco.shared.common.web.PageableFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Compras multivendedor: un subpedido por vendedor. Empresa y permisos desde el token. */
@RestController
@RequestMapping("/sales/purchases")
@Tag(name = "Purchases", description = "Compras con productos de varios vendedores (subpedidos por vendedor)")
public class PurchaseController {

    private static final Set<String> SORTABLE = Set.of("id", "number", "createdAt", "total", "status");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"));

    private final PurchaseService purchases;
    private final StartPurchaseCheckoutService checkout;

    public PurchaseController(PurchaseService purchases, StartPurchaseCheckoutService checkout) {
        this.purchases = purchases;
        this.checkout = checkout;
    }

    @Operation(summary = "Search", description = "Paginado. q: número, cliente, documento o correo; status: PENDING, "
            + "PARTIALLY_PAID, PAID, IN_DELIVERY, DELIVERED o CANCELLED (derivado de los subpedidos). VENTAS.VER.")
    @GetMapping("/search")
    public PageResponse<PurchaseSummaryDto> search(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sort", required = false) String sort) {
        return PageResponse.from(purchases.search(new PurchaseFilter(q, status),
                PageableFactory.of(page, size, sort, SORTABLE, DEFAULT_SORT)));
    }

    @Operation(summary = "Get", description = "Compra con sus subpedidos (vendedor, estado, total). VENTAS.VER.")
    @GetMapping("/{id}")
    public PurchaseDetailDto get(@PathVariable long id) {
        return purchases.get(id);
    }

    @Operation(summary = "Create", description = "Crea la compra y un subpedido PENDIENTE por vendedor. Los productos propios "
            + "reservan stock de bodega; los de ofertas, la cantidad de la oferta. 409 si no alcanza o la oferta ya no "
            + "está a la venta. VENTAS.GESTIONAR.")
    @PostMapping
    public ResponseEntity<PurchaseDetailDto> create(@Valid @RequestBody CreatePurchaseRequest input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purchases.create(input));
    }

    @Operation(summary = "Checkout", description = "Un solo link de pago en línea por el saldo de todos los subpedidos "
            + "(también los de vendedores externos: los cobra la tienda y les liquida). Crea un pago pendiente por subpedido; "
            + "la aprobación de la pasarela los aprueba a todos. VENTAS.GESTIONAR.")
    @PostMapping("/{id}/checkout")
    public ResponseEntity<PurchaseCheckoutDto> checkout(@PathVariable long id, @Valid @RequestBody StartPurchaseCheckoutDto input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(checkout.start(id, input));
    }

    @Operation(summary = "Sku options", description = "Quién vende el SKU: tienda propia y ofertas activas. VENTAS.GESTIONAR.")
    @GetMapping("/sku-options/{skuId}")
    public SkuOptionsDto skuOptions(@PathVariable long skuId) {
        return purchases.skuOptions(skuId);
    }

    @Operation(summary = "Purchase of an order", description = "Compra y vendedor de un subpedido; 404 si la orden no "
            + "pertenece a una compra de la empresa. VENTAS.VER.")
    @GetMapping("/by-order/{orderId}")
    public OrderPurchaseDto ofOrder(@PathVariable long orderId) {
        return purchases.ofOrder(orderId);
    }
}
