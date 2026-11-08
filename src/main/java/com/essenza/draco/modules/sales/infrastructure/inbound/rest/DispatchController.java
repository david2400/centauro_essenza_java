package com.essenza.draco.modules.sales.infrastructure.inbound.rest;

import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.essenza.draco.modules.sales.application.dto.dispatch.DispatchDtos.BackfillResultDto;
import com.essenza.draco.modules.sales.application.dto.dispatch.DispatchDtos.DispatchRowDto;
import com.essenza.draco.modules.sales.application.dto.dispatch.DispatchDtos.DispatchStatsDto;
import com.essenza.draco.modules.sales.application.services.dispatch.DispatchService;
import com.essenza.draco.shared.common.domain.dto.PageResponse;
import com.essenza.draco.shared.common.web.PageableFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Tablero de despachos por subpedido. Empresa y permisos desde el token. */
@RestController
@RequestMapping("/sales/dispatch")
@Tag(name = "Dispatch", description = "Despachos por subpedido: fecha límite, pendientes, vencidos y cumplimiento")
public class DispatchController {

    private static final Set<String> SORTABLE = Set.of("shipBy", "paidAt", "shippedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("shipBy"));

    private final DispatchService dispatch;

    public DispatchController(DispatchService dispatch) {
        this.dispatch = dispatch;
    }

    @Operation(summary = "Search", description = "view: TO_SHIP (defecto), OVERDUE, DUE_TODAY, IN_TRANSIT, DELIVERED (30 días) "
            + "o ALL. seller: own o id del vendedor. q: orden, compra, cliente o guía. VENTAS.VER.")
    @GetMapping("/search")
    public PageResponse<DispatchRowDto> search(
            @RequestParam(value = "view", required = false) String view,
            @RequestParam(value = "seller", required = false) String seller,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sort", required = false) String sort) {
        return PageResponse.from(dispatch.search(view, seller, q, PageableFactory.of(page, size, sort, SORTABLE, DEFAULT_SORT)));
    }

    @Operation(summary = "Stats", description = "Pendientes, vencidos, para hoy, en camino, entregados y cumplimiento en 30 días, "
            + "con la carga por vendedor. VENTAS.VER.")
    @GetMapping("/stats")
    public DispatchStatsDto stats() {
        return dispatch.stats();
    }

    @Operation(summary = "Backfill", description = "Calcula la fecha límite de las órdenes abiertas que no la tienen "
            + "(hasta 500 por llamada, idempotente). VENTAS.GESTIONAR.")
    @PostMapping("/promises/backfill")
    public BackfillResultDto backfill() {
        return dispatch.backfill();
    }
}
