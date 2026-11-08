package com.essenza.draco.modules.marketplace.infrastructure.inbound.rest;

import java.util.List;
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

import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.AdjustmentRequest;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.DocumentDto;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.MovementDto;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.MovementFilter;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.OptionDto;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.SaveDocumentRequest;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.StockFilter;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.StockRowDto;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.StockStatsDto;
import com.essenza.draco.modules.marketplace.application.service.ConsignmentService;
import com.essenza.draco.shared.common.domain.dto.PageResponse;
import com.essenza.draco.shared.common.web.PageableFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Consignación: mercancía de vendedores externos en bodegas propias. Empresa y permisos desde el token. */
@RestController
@RequestMapping("/marketplace/consignment")
@Tag(name = "Consignment", description = "Stock de vendedores en bodegas propias: recepciones, devoluciones, ajustes y kardex")
public class ConsignmentController {

    private static final Set<String> STOCK_SORTABLE = Set.of("onHand", "firstReceivedAt", "lastMovementAt", "sellerName", "skuCode");
    private static final Set<String> HISTORY_SORTABLE = Set.of("createdAt", "quantity", "units");

    private final ConsignmentService consignment;

    public ConsignmentController(ConsignmentService consignment) {
        this.consignment = consignment;
    }

    @Operation(summary = "Stock", description = "Saldo por vendedor, SKU y bodega. all=true incluye saldos en cero. CONSIGNACION.VER.")
    @GetMapping("/stock")
    public PageResponse<StockRowDto> stock(
            @RequestParam(value = "sellerId", required = false) Long sellerId,
            @RequestParam(value = "warehouseId", required = false) Long warehouseId,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "all", required = false, defaultValue = "false") boolean all,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sort", required = false) String sort) {
        return PageResponse.from(consignment.stock(new StockFilter(sellerId, warehouseId, q, !all),
                PageableFactory.of(page, size, sort, STOCK_SORTABLE, Sort.by(Sort.Order.asc("firstReceivedAt")))));
    }

    @Operation(summary = "Stats", description = "Unidades, SKU, vendedores y bodegas con saldo, reservado y saldos sin oferta. CONSIGNACION.VER.")
    @GetMapping("/stock/stats")
    public StockStatsDto stats() {
        return consignment.stats();
    }

    @Operation(summary = "Movements", description = "Kardex de consignación. kind: RECEIPT, RETURN_TO_SELLER, ADJUSTMENT, SALE, "
            + "SALE_CANCEL o CUSTOMER_RETURN. CONSIGNACION.VER.")
    @GetMapping("/movements")
    public PageResponse<MovementDto> movements(
            @RequestParam(value = "sellerId", required = false) Long sellerId,
            @RequestParam(value = "skuId", required = false) Long skuId,
            @RequestParam(value = "warehouseId", required = false) Long warehouseId,
            @RequestParam(value = "orderId", required = false) Long orderId,
            @RequestParam(value = "kind", required = false) String kind,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sort", required = false) String sort) {
        return PageResponse.from(consignment.movements(new MovementFilter(sellerId, skuId, warehouseId, orderId, kind),
                PageableFactory.of(page, size, sort, HISTORY_SORTABLE, Sort.by(Sort.Order.desc("createdAt")))));
    }

    @Operation(summary = "Documents", description = "Recepciones, devoluciones al vendedor y ajustes. CONSIGNACION.VER.")
    @GetMapping("/documents")
    public PageResponse<DocumentDto> documents(
            @RequestParam(value = "sellerId", required = false) Long sellerId,
            @RequestParam(value = "kind", required = false) String kind,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sort", required = false) String sort) {
        return PageResponse.from(consignment.documents(sellerId, kind,
                PageableFactory.of(page, size, sort, HISTORY_SORTABLE, Sort.by(Sort.Order.desc("createdAt")))));
    }

    @Operation(summary = "Document", description = "Documento con sus líneas. CONSIGNACION.VER.")
    @GetMapping("/documents/{id}")
    public DocumentDto document(@PathVariable long id) {
        return consignment.document(id);
    }

    @Operation(summary = "Save document", description = "kind RECEIPT (recibir del vendedor) o RETURN_TO_SELLER (devolverle). "
            + "Actualiza el disponible de su oferta con despacho de la plataforma. CONSIGNACION.GESTIONAR.")
    @PostMapping("/documents")
    public ResponseEntity<DocumentDto> saveDocument(@RequestBody SaveDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(consignment.saveDocument(request));
    }

    @Operation(summary = "Adjust", description = "Conteo físico de un SKU del vendedor en una bodega (motivo obligatorio). "
            + "CONSIGNACION.GESTIONAR.")
    @PostMapping("/adjustments")
    public ResponseEntity<DocumentDto> adjust(@RequestBody AdjustmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(consignment.adjust(request));
    }

    @Operation(summary = "Options", description = "Vendedores y bodegas para filtros y formularios. CONSIGNACION.VER.")
    @GetMapping("/options")
    public Options options() {
        return new Options(consignment.sellerOptions(), consignment.warehouseOptions());
    }

    public record Options(List<OptionDto> sellers, List<OptionDto> warehouses) {
    }
}
