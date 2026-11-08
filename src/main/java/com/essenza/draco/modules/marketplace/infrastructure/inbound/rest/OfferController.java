package com.essenza.draco.modules.marketplace.infrastructure.inbound.rest;

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

import com.essenza.draco.modules.marketplace.application.dto.OfferDtos.OfferDetailDto;
import com.essenza.draco.modules.marketplace.application.dto.OfferDtos.OfferEventDto;
import com.essenza.draco.modules.marketplace.application.dto.OfferDtos.OfferStatsDto;
import com.essenza.draco.modules.marketplace.application.dto.OfferDtos.OfferSummaryDto;
import com.essenza.draco.modules.marketplace.application.dto.OfferDtos.OfferTransitionRequest;
import com.essenza.draco.modules.marketplace.application.dto.OfferDtos.SaveOfferRequest;
import com.essenza.draco.modules.marketplace.application.port.OfferStore.OfferFilter;
import com.essenza.draco.modules.marketplace.application.service.OfferService;
import com.essenza.draco.shared.common.domain.dto.PageResponse;
import com.essenza.draco.shared.common.web.PageableFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Ofertas de vendedores externos y su moderación. Empresa y permisos desde el token. */
@RestController
@RequestMapping("/marketplace/offers")
@Tag(name = "Marketplace · Offers", description = "Ofertas de vendedores sobre SKUs del catálogo y moderación")
public class OfferController {

    private static final Set<String> SORTABLE = Set.of("id", "price", "status", "productName", "sellerName",
            "availableQuantity", "updatedAt", "publishedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("updatedAt"));

    private final OfferService offers;

    public OfferController(OfferService offers) {
        this.offers = offers;
    }

    @Operation(summary = "Search", description = "Paginado. q: producto, SKU, referencia o vendedor; filtros status, sellerId y "
            + "skuId (siempre dentro de la empresa del token). Permiso OFERTAS.VER u OFERTAS.MODERAR.")
    @GetMapping("/search")
    public PageResponse<OfferSummaryDto> search(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "sellerId", required = false) Long sellerId,
            @RequestParam(value = "skuId", required = false) Long skuId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sort", required = false) String sort) {
        return PageResponse.from(offers.search(new OfferFilter(q, status, sellerId, skuId),
                PageableFactory.of(page, size, sort, SORTABLE, DEFAULT_SORT)));
    }

    @Operation(summary = "Stats", description = "Ofertas por estado (cola de moderación = pending_review).")
    @GetMapping("/stats")
    public OfferStatsDto stats(@RequestParam(value = "sellerId", required = false) Long sellerId) {
        return offers.stats(sellerId);
    }

    @Operation(summary = "Get", description = "Oferta con los datos vigentes del SKU, desviación de precio, visibilidad y "
            + "acciones permitidas al usuario.")
    @GetMapping("/{id}")
    public OfferDetailDto get(@PathVariable long id) {
        return offers.get(id);
    }

    @GetMapping("/{id}/events")
    public List<OfferEventDto> events(@PathVariable long id) {
        return offers.events(id);
    }

    @Operation(summary = "Create", description = "Borrador. Vendedor externo de la empresa y SKU existente. Permiso OFERTAS.GESTIONAR.")
    @PostMapping
    public ResponseEntity<OfferDetailDto> create(@RequestBody SaveOfferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(offers.create(request));
    }

    @Operation(summary = "Update", description = "Borrador/rechazada: todo. Publicada/pausada: precio, cantidad y alistamiento. "
            + "Requiere lock_version.")
    @PutMapping("/{id}")
    public OfferDetailDto update(@PathVariable long id, @RequestBody SaveOfferRequest request) {
        return offers.update(id, request);
    }

    @Operation(summary = "Transition", description = "SUBMIT, PAUSE, RESUME, CLOSE (OFERTAS.GESTIONAR) o APPROVE, REJECT "
            + "(OFERTAS.MODERAR). Motivo obligatorio en REJECT y CLOSE.")
    @PostMapping("/{id}/transitions")
    public OfferDetailDto transition(@PathVariable long id, @RequestBody OfferTransitionRequest request) {
        return offers.transition(id, request);
    }
}
