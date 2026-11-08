package com.essenza.draco.modules.inventory.infrastructure.inbound.rest;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.OptionDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.OrderDetailDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.OrderSummaryDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.ReceiveRequest;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.SaveOrderRequest;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.StatsDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.StatusRequest;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.SupplierOptionDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.SupplierProductDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.SuggestionsDto;
import com.essenza.draco.modules.inventory.application.services.purchase.PurchaseOrderService;
import com.essenza.draco.shared.common.domain.dto.PageResponse;
import com.essenza.draco.shared.common.web.PageableFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Órdenes de compra a proveedores y su recepción en bodega. Empresa y permisos desde el token. */
@RestController
@RequestMapping("/inventory/purchase-orders")
@Tag(name = "Purchase orders", description = "Órdenes de compra a proveedores: borrador, envío, recepciones y cierre")
public class PurchaseOrderController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "expectedDate", "total");

    private final PurchaseOrderService orders;

    public PurchaseOrderController(PurchaseOrderService orders) {
        this.orders = orders;
    }

    @Operation(summary = "Search", description = "status: un estado, OPEN (enviadas o en parte) u OVERDUE (fecha esperada vencida). COMPRAS.VER.")
    @GetMapping("/search")
    public PageResponse<OrderSummaryDto> search(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "supplierId", required = false) Long supplierId,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sort", required = false) String sort) {
        return PageResponse.from(orders.search(status, supplierId, q,
                PageableFactory.of(page, size, sort, SORTABLE, Sort.by(Sort.Order.desc("createdAt")))));
    }

    @Operation(summary = "Stats", description = "Borradores, abiertas, vencidas, en parte y lo recibido en 30 días. COMPRAS.VER.")
    @GetMapping("/stats")
    public StatsDto stats() {
        return orders.stats();
    }

    @Operation(summary = "Options", description = "Proveedores y bodegas para el formulario. COMPRAS.VER.")
    @GetMapping("/options")
    public Options options() {
        return new Options(orders.supplierOptions(), orders.warehouseOptions());
    }

    @Operation(summary = "Supplier products", description = "Productos que surte el proveedor con su costo pactado. COMPRAS.VER.")
    @GetMapping("/suppliers/{supplierId}/products")
    public List<SupplierProductDto> supplierProducts(@PathVariable long supplierId) {
        return orders.supplierProducts(supplierId);
    }

    @Operation(summary = "Suggestions", description = "Sugerencia de compra desde el stock bajo, por bodega y proveedor sugerido."
            + " Reponer hasta el doble del mínimo menos lo que ya viene en órdenes abiertas. COMPRAS.VER.")
    @GetMapping("/suggestions")
    public SuggestionsDto suggestions(@RequestParam(value = "warehouseId", required = false) Long warehouseId) {
        return orders.suggestions(warehouseId);
    }

    @Operation(summary = "Get", description = "Orden con líneas, recepciones, historial y acciones permitidas. COMPRAS.VER.")
    @GetMapping("/{id}")
    public OrderDetailDto get(@PathVariable long id) {
        return orders.get(id);
    }

    @Operation(summary = "Create", description = "Crea un borrador. Costo vacío = el pactado con el proveedor. COMPRAS.GESTIONAR.")
    @PostMapping
    public ResponseEntity<OrderDetailDto> create(@RequestBody SaveOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orders.create(request));
    }

    @Operation(summary = "Update", description = "Edita un borrador (con lock_version). COMPRAS.GESTIONAR.")
    @PutMapping("/{id}")
    public OrderDetailDto update(@PathVariable long id, @RequestBody SaveOrderRequest request) {
        return orders.update(id, request);
    }

    @Operation(summary = "Send", description = "Envía la orden al proveedor. COMPRAS.GESTIONAR.")
    @PostMapping("/{id}/send")
    public OrderDetailDto send(@PathVariable long id, @RequestBody StatusRequest request) {
        return orders.send(id, request);
    }

    @Operation(summary = "Receive", description = "Recepción parcial o total en la bodega destino (suma al inventario). COMPRAS.RECIBIR.")
    @PostMapping("/{id}/receipts")
    public OrderDetailDto receive(@PathVariable long id, @RequestBody ReceiveRequest request) {
        return orders.receive(id, request);
    }

    @Operation(summary = "Cancel", description = "Cancela un borrador o una orden enviada sin recepciones. COMPRAS.GESTIONAR.")
    @PostMapping("/{id}/cancel")
    public OrderDetailDto cancel(@PathVariable long id, @RequestBody StatusRequest request) {
        return orders.cancel(id, request);
    }

    @Operation(summary = "Close", description = "Cierra una orden recibida en parte (lo pendiente ya no llega). COMPRAS.GESTIONAR.")
    @PostMapping("/{id}/close")
    public OrderDetailDto close(@PathVariable long id, @RequestBody StatusRequest request) {
        return orders.close(id, request);
    }

    public record Options(List<SupplierOptionDto> suppliers, List<OptionDto> warehouses) {
    }
}
