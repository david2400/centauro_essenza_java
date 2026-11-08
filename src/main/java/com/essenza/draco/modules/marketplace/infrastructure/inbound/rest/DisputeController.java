package com.essenza.draco.modules.marketplace.infrastructure.inbound.rest;

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

import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.CancelRequest;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.DisputeDetailDto;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.DisputeStatsDto;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.DisputeSummaryDto;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.OpenDisputeRequest;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.OrderDisputeDto;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.ResolveRequest;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.RespondRequest;
import com.essenza.draco.modules.marketplace.application.service.DisputeService;
import com.essenza.draco.shared.common.domain.dto.PageResponse;
import com.essenza.draco.shared.common.web.PageableFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Reclamos de compradores contra vendedores externos. Empresa y permisos desde el token. */
@RestController
@RequestMapping("/marketplace/disputes")
@Tag(name = "Disputes", description = "Reclamos: apertura, respuesta del vendedor, decisión y cargo al vendedor")
public class DisputeController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "respondBy");

    private final DisputeService disputes;

    public DisputeController(DisputeService disputes) {
        this.disputes = disputes;
    }

    @Operation(summary = "Search", description = "status: un estado, ACTIVE (abiertos) u OVERDUE (sin respuesta y vencidos). DISPUTAS.VER.")
    @GetMapping("/search")
    public PageResponse<DisputeSummaryDto> search(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "sellerId", required = false) Long sellerId,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sort", required = false) String sort) {
        return PageResponse.from(disputes.search(status, sellerId, q,
                PageableFactory.of(page, size, sort, SORTABLE, Sort.by(Sort.Order.desc("createdAt")))));
    }

    @Operation(summary = "Stats", description = "Abiertos, vencidos, por decidir y decididos en 30 días. DISPUTAS.VER.")
    @GetMapping("/stats")
    public DisputeStatsDto stats() {
        return disputes.stats();
    }

    @Operation(summary = "Get", description = "Reclamo con historial y acciones permitidas. DISPUTAS.VER.")
    @GetMapping("/{id}")
    public DisputeDetailDto get(@PathVariable long id) {
        return disputes.get(id);
    }

    @Operation(summary = "Of order", description = "Reclamo abierto (o el último) de una orden; 204 si no tiene. DISPUTAS.VER.")
    @GetMapping("/by-order/{orderId}")
    public ResponseEntity<OrderDisputeDto> ofOrder(@PathVariable long orderId) {
        return disputes.ofOrder(orderId).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @Operation(summary = "Open", description = "Abre un reclamo sobre el subpedido de un vendedor externo. DISPUTAS.GESTIONAR.")
    @PostMapping
    public ResponseEntity<DisputeDetailDto> open(@RequestBody OpenDisputeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(disputes.open(request));
    }

    @Operation(summary = "Respond", description = "Registra la respuesta del vendedor. DISPUTAS.GESTIONAR.")
    @PostMapping("/{id}/respond")
    public DisputeDetailDto respond(@PathVariable long id, @RequestBody RespondRequest request) {
        return disputes.respond(id, request);
    }

    @Operation(summary = "Resolve", description = "Decide el reclamo (REFUND, REPLACEMENT o REJECTED) y el cargo al vendedor. "
            + "DISPUTAS.RESOLVER.")
    @PostMapping("/{id}/resolve")
    public DisputeDetailDto resolve(@PathVariable long id, @RequestBody ResolveRequest request) {
        return disputes.resolve(id, request);
    }

    @Operation(summary = "Cancel", description = "El comprador retira el reclamo. DISPUTAS.GESTIONAR.")
    @PostMapping("/{id}/cancel")
    public DisputeDetailDto cancel(@PathVariable long id, @RequestBody CancelRequest request) {
        return disputes.cancel(id, request);
    }
}
