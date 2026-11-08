package com.essenza.draco.modules.marketplace.infrastructure.inbound.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.AssignPlanRequest;
import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.AssignmentDto;
import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.CancelAssignmentRequest;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.DocumentDto;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.DocumentFile;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.ReviewDocumentRequest;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.SaveSellerRequest;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.SellerDetailDto;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.SellerEventDto;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.SellerStatsDto;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.SellerSummaryDto;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.SellerTransitionRequest;
import com.essenza.draco.modules.marketplace.application.port.SellerStore.SellerFilter;
import com.essenza.draco.modules.marketplace.application.service.AssignmentService;
import com.essenza.draco.modules.marketplace.application.service.SellerDocumentService;
import com.essenza.draco.modules.marketplace.application.service.SellerService;
import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.shared.common.domain.dto.PageResponse;
import com.essenza.draco.shared.common.web.PageableFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Vendedores del marketplace. La empresa y los permisos salen del token (nunca del cuerpo);
 * cada operación responde 401/403 si no corresponde. No hay DELETE: un vendedor se cierra.
 */
@RestController
@RequestMapping("/marketplace/sellers")
@Tag(name = "Marketplace · Sellers", description = "Vendedores propios y externos: ficha, documentos, estados, planes")
public class SellerController {

    private static final Set<String> SORTABLE = Set.of("id", "code", "legalName", "status", "kind", "city", "createdAt",
            "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("updatedAt"));

    private final SellerService sellers;
    private final SellerDocumentService documents;
    private final AssignmentService assignments;

    public SellerController(SellerService sellers, SellerDocumentService documents, AssignmentService assignments) {
        this.sellers = sellers;
        this.documents = documents;
        this.assignments = assignments;
    }

    @Operation(summary = "Search", description = "Paginado en servidor. q: razón social, nombre comercial, código, documento, "
            + "correo o ciudad. Filtros status y kind (OWN | EXTERNAL). Permiso VENDEDORES.VER.")
    @GetMapping("/search")
    public PageResponse<SellerSummaryDto> search(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "kind", required = false) String kind,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sort", required = false) String sort) {
        return PageResponse.from(sellers.search(new SellerFilter(q, status, kind),
                PageableFactory.of(page, size, sort, SORTABLE, DEFAULT_SORT)));
    }

    @Operation(summary = "Lookup", description = "Hasta 20 vendedores no cerrados por nombre, código o documento (buscadores). "
            + "kind opcional (OWN | EXTERNAL).")
    @GetMapping("/lookup")
    public List<com.essenza.draco.modules.marketplace.application.dto.OfferDtos.SellerOptionDto> lookup(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "kind", required = false) String kind) {
        return sellers.lookup(q, kind);
    }

    @Operation(summary = "Stats", description = "Totales por estado y tipo.")
    @GetMapping("/stats")
    public SellerStatsDto stats() {
        return sellers.stats();
    }

    @Operation(summary = "Get", description = "Ficha, contactos, documentos, lo que falta para revisar/aprobar, acciones "
            + "permitidas al usuario y planes.")
    @GetMapping("/{id}")
    public SellerDetailDto get(@PathVariable long id) {
        return sellers.get(id);
    }

    @Operation(summary = "Create", description = "Queda en DRAFT. Código vacío = VEN-00001. Permiso VENDEDORES.GESTIONAR.")
    @PostMapping
    public ResponseEntity<SellerDetailDto> create(@RequestBody SaveSellerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sellers.create(request));
    }

    @Operation(summary = "Update", description = "Reemplaza ficha y contactos. Requiere version (409 si cambió). "
            + "No se edita en revisión ni cerrado.")
    @PutMapping("/{id}")
    public SellerDetailDto update(@PathVariable long id, @RequestBody SaveSellerRequest request) {
        return sellers.update(id, request);
    }

    @Operation(summary = "Transition", description = "SUBMIT (VENDEDORES.GESTIONAR) o REQUEST_CHANGES, APPROVE, SUSPEND, "
            + "REACTIVATE, CLOSE (VENDEDORES.APROBAR). Motivo obligatorio salvo SUBMIT y APPROVE.")
    @PostMapping("/{id}/transitions")
    public SellerDetailDto transition(@PathVariable long id, @RequestBody SellerTransitionRequest request) {
        return sellers.transition(id, request);
    }

    @Operation(summary = "History", description = "Auditoría: actor, fecha, acción, estados y motivo.")
    @GetMapping("/{id}/events")
    public List<SellerEventDto> events(@PathVariable long id) {
        return sellers.events(id);
    }

    // ── Documentos ─────────────────────────────────────────────────────────

    @Operation(summary = "Documents", description = "Vigentes; con all=true también los reemplazados.")
    @GetMapping("/{id}/documents")
    public List<DocumentDto> documents(@PathVariable long id, @RequestParam(value = "all", defaultValue = "false") boolean all) {
        return documents.list(id, all);
    }

    @Operation(summary = "Upload document", description = "multipart: kind (RUT | CAMARA_COMERCIO | CEDULA_REPRESENTANTE | "
            + "CERTIFICACION_BANCARIA | OTRO) y file (PDF, PNG o JPG, máx. 10 MB). Reemplaza el vigente del mismo tipo.")
    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentDto> upload(@PathVariable long id, @RequestParam("kind") String kind,
                                              @RequestPart("file") MultipartFile file) throws IOException {
        MarketplaceRuleViolation.check(file.getSize() <= SellerDocumentService.MAX_BYTES, "El archivo supera 10 MB.");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documents.upload(id, kind, file.getOriginalFilename(), file.getBytes()));
    }

    @Operation(summary = "Review document", description = "ACCEPTED o REJECTED (nota obligatoria). Permiso VENDEDORES.APROBAR.")
    @PostMapping("/{id}/documents/{documentId}/review")
    public DocumentDto review(@PathVariable long id, @PathVariable long documentId, @RequestBody ReviewDocumentRequest request) {
        return documents.review(id, documentId, request);
    }

    @Operation(summary = "Download document", description = "Archivo privado; requiere VENDEDORES.VER y queda auditado.")
    @GetMapping("/{id}/documents/{documentId}/file")
    public ResponseEntity<InputStreamResource> download(@PathVariable long id, @PathVariable long documentId) {
        DocumentFile file = documents.download(id, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(file.content()));
    }

    // ── Planes del vendedor ────────────────────────────────────────────────

    @Operation(summary = "Plan assignments", description = "Vigencias (programadas, vigente, terminadas y canceladas).")
    @GetMapping("/{id}/plans")
    public List<AssignmentDto> plans(@PathVariable long id) {
        return assignments.list(id);
    }

    @Operation(summary = "Assign plan", description = "Sin plan vigente empieza hoy (o la fecha dada); con plan vigente se "
            + "programa al inicio del próximo ciclo. Excepciones requieren PLANES.EXCEPCIONES y motivo.")
    @PostMapping("/{id}/plans")
    public ResponseEntity<AssignmentDto> assign(@PathVariable long id, @RequestBody AssignPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assignments.assign(id, request));
    }

    @Operation(summary = "Cancel scheduled plan", description = "Solo cambios programados que no han empezado. Motivo obligatorio.")
    @PostMapping("/{id}/plans/{assignmentId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable long id, @PathVariable long assignmentId,
                                       @RequestBody CancelAssignmentRequest request) {
        assignments.cancel(id, assignmentId, request.reason());
        return ResponseEntity.noContent().build();
    }
}
