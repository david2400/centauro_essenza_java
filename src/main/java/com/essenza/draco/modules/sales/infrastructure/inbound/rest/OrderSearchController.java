package com.essenza.draco.modules.sales.infrastructure.inbound.rest;

import java.util.Set;

import jakarta.validation.Valid;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.essenza.draco.shared.common.domain.dto.BulkIdsRequest;
import com.essenza.draco.shared.common.domain.dto.BulkOperationResult;
import com.essenza.draco.shared.common.domain.dto.PageResponse;
import com.essenza.draco.shared.common.web.PageableFactory;
import com.essenza.draco.modules.sales.application.input.order.BulkDeleteOrdersUseCase;
import com.essenza.draco.modules.sales.application.dto.order.OrderDto;

/**
 * Endpoints de administración (búsqueda paginada y lote) sobre la misma ruta
 * base que el CRUD existente.
 */
@RestController
@RequestMapping("/sales/orders")
@Tag(name = "Orders")
public class OrderSearchController {

    private static final Set<String> SORTABLE = Set.of("id", "total", "state", "createdAt", "updatedAt", "orderNumber",
            "customerName", "promisedDate", "priority", "deliveryMethod", "channel");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("id"));

    private final BulkDeleteOrdersUseCase bulkDeleteOrders;
    private final com.essenza.draco.modules.sales.application.input.order.OrderInsightsUseCase insights;

    public OrderSearchController(BulkDeleteOrdersUseCase bulkDeleteOrders,
                                 com.essenza.draco.modules.sales.application.input.order.OrderInsightsUseCase insights) {
        this.bulkDeleteOrders = bulkDeleteOrders;
        this.insights = insights;
    }

    @Operation(summary = "Search", description = "q busca en número, cliente, documento, correo, teléfono, referencia y guía; "
            + "filtros state, channel, storeId, priority, deliveryMethod, paymentStatus (UNPAID | PARTIAL | PAID), "
            + "period (TODAY | LAST_7 | LAST_30 | THIS_MONTH) overdue y origin (OWN | MARKETPLACE); page, size, sort=campo,asc|desc")
    @GetMapping("/search")
    public PageResponse<OrderDto> search(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "channel", required = false) String channel,
            @RequestParam(value = "storeId", required = false) Long storeId,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "deliveryMethod", required = false) String deliveryMethod,
            @RequestParam(value = "paymentStatus", required = false) String paymentStatus,
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "overdue", required = false) Boolean overdue,
            @RequestParam(value = "origin", required = false) String origin,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sort", required = false) String sort) {
        return insights.search(new com.essenza.draco.modules.sales.application.dto.order.OrderFilter(q, state, channel, storeId,
                priority, deliveryMethod, paymentStatus, period, overdue, origin), PageableFactory.of(page, size, sort, SORTABLE, DEFAULT_SORT));
    }

    @Operation(summary = "Summary", description = "KPIs con los mismos filtros de /search: ventas, ticket promedio y colas de trabajo.")
    @GetMapping("/summary")
    public com.essenza.draco.modules.sales.application.dto.order.OrderSummaryDto summary(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "channel", required = false) String channel,
            @RequestParam(value = "storeId", required = false) Long storeId,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "deliveryMethod", required = false) String deliveryMethod,
            @RequestParam(value = "paymentStatus", required = false) String paymentStatus,
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "overdue", required = false) Boolean overdue,
            @RequestParam(value = "origin", required = false) String origin) {
        return insights.summary(new com.essenza.draco.modules.sales.application.dto.order.OrderFilter(q, state, channel, storeId,
                priority, deliveryMethod, paymentStatus, period, overdue, origin));
    }

    @Operation(summary = "Bulk delete", description = "Elimina varios registros; los fallos se informan en 'failed'.")
    @PostMapping("/bulk-delete")
    public BulkOperationResult bulkDelete(@Valid @RequestBody BulkIdsRequest request) {
        return bulkDeleteOrders.deleteAll(request.ids());
    }
}
