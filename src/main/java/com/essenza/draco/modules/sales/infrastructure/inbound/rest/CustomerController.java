package com.essenza.draco.modules.sales.infrastructure.inbound.rest;

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

import com.essenza.draco.modules.sales.application.dto.customer.CustomerDtos.CustomerDetailDto;
import com.essenza.draco.modules.sales.application.dto.customer.CustomerDtos.CustomerLinkDto;
import com.essenza.draco.modules.sales.application.dto.customer.CustomerDtos.CustomerSummaryDto;
import com.essenza.draco.modules.sales.application.dto.customer.CustomerDtos.ImportResultDto;
import com.essenza.draco.modules.sales.application.dto.customer.CustomerDtos.SaveCustomerRequest;
import com.essenza.draco.modules.sales.application.services.customer.CustomerService;
import com.essenza.draco.shared.common.domain.dto.PageResponse;
import com.essenza.draco.shared.common.web.PageableFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Clientes persistentes y ficha 360. Empresa y permisos desde el token. */
@RestController
@RequestMapping("/sales/customers")
@Tag(name = "Customers", description = "Clientes de la tienda y su ficha 360")
public class CustomerController {

    private static final Set<String> SORTABLE = Set.of("id", "name", "createdAt", "lastOrderAt", "totalSpent", "ordersCount");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("lastOrderAt"));

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @Operation(summary = "Search", description = "q: nombre, documento, correo o teléfono. VENTAS.VER.")
    @GetMapping("/search")
    public PageResponse<CustomerSummaryDto> search(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sort", required = false) String sort) {
        return PageResponse.from(customers.search(q, PageableFactory.of(page, size, sort, SORTABLE, DEFAULT_SORT)));
    }

    @Operation(summary = "By document", description = "Cliente registrado para un documento (id nulo si no existe). "
            + "Enlaza la orden con la ficha. VENTAS.VER.")
    @GetMapping("/by-document")
    public CustomerLinkDto byDocument(@RequestParam(value = "docType", required = false) String docType,
                                      @RequestParam("docNumber") String docNumber) {
        return customers.findByDocument(docType, docNumber);
    }

    @Operation(summary = "Get", description = "Cliente con su ficha 360 (órdenes por documento). VENTAS.VER.")
    @GetMapping("/{id}")
    public CustomerDetailDto get(@PathVariable long id) {
        return customers.get(id);
    }

    @Operation(summary = "Create", description = "Documento obligatorio y único por empresa. VENTAS.GESTIONAR.")
    @PostMapping
    public ResponseEntity<CustomerDetailDto> create(@RequestBody SaveCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customers.create(request));
    }

    @Operation(summary = "Update", description = "Con lock_version. VENTAS.GESTIONAR.")
    @PutMapping("/{id}")
    public CustomerDetailDto update(@PathVariable long id, @RequestBody SaveCustomerRequest request) {
        return customers.update(id, request);
    }

    @Operation(summary = "Import from orders", description = "Registra como clientes los documentos de órdenes anteriores "
            + "(idempotente). VENTAS.GESTIONAR.")
    @PostMapping("/import-from-orders")
    public ImportResultDto importFromOrders() {
        return customers.importFromOrders();
    }
}
