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

import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.PlanCommandRequest;
import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.PlanDto;
import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.PlanEventDto;
import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.SavePlanRequest;
import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.SimulationDto;
import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.SimulationRequest;
import com.essenza.draco.modules.marketplace.application.port.PlanStore.PlanFilter;
import com.essenza.draco.modules.marketplace.application.service.PlanService;
import com.essenza.draco.shared.common.domain.dto.PageResponse;
import com.essenza.draco.shared.common.web.PageableFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Planes comerciales versionados y simulador. Empresa y permisos desde el token. */
@RestController
@RequestMapping("/marketplace/plans")
@Tag(name = "Marketplace · Plans", description = "Planes por comisión, mensualidad o mixtos; versiones y simulador")
public class PlanController {

    private static final Set<String> SORTABLE = Set.of("id", "code", "name", "status", "modality", "version", "validFrom",
            "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("code"), Sort.Order.desc("version"));

    private final PlanService plans;

    public PlanController(PlanService plans) {
        this.plans = plans;
    }

    @Operation(summary = "Search", description = "Paginado. q: código o nombre; filtros status y modality. Permiso PLANES.VER.")
    @GetMapping("/search")
    public PageResponse<PlanDto> search(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "modality", required = false) String modality,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sort", required = false) String sort) {
        return PageResponse.from(plans.search(new PlanFilter(q, status, modality),
                PageableFactory.of(page, size, sort, SORTABLE, DEFAULT_SORT)));
    }

    @Operation(summary = "Assignable", description = "Hasta 20 planes publicados y vigentes (buscador). Permiso PLANES.ASIGNAR.")
    @GetMapping("/assignable")
    public List<PlanDto> assignable(@RequestParam(value = "q", required = false) String q) {
        return plans.assignable(q);
    }

    @GetMapping("/{id}")
    public PlanDto get(@PathVariable long id) {
        return plans.get(id);
    }

    @GetMapping("/{id}/events")
    public List<PlanEventDto> events(@PathVariable long id) {
        return plans.events(id);
    }

    @Operation(summary = "Create", description = "Borrador v1. Permiso PLANES.GESTIONAR.")
    @PostMapping
    public ResponseEntity<PlanDto> create(@RequestBody SavePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plans.create(request));
    }

    @Operation(summary = "Update draft", description = "Solo borradores. Requiere lock_version.")
    @PutMapping("/{id}")
    public PlanDto update(@PathVariable long id, @RequestBody SavePlanRequest request) {
        return plans.update(id, request);
    }

    @Operation(summary = "Publish", description = "El borrador queda ACTIVE e inmutable; la versión activa anterior queda "
            + "SUPERSEDED sin migrar a nadie.")
    @PostMapping("/{id}/publish")
    public PlanDto publish(@PathVariable long id, @RequestBody PlanCommandRequest request) {
        return plans.publish(id, request.lockVersion());
    }

    @Operation(summary = "New version", description = "Borrador con las condiciones de esta versión para ajustar.")
    @PostMapping("/{id}/versions")
    public ResponseEntity<PlanDto> newVersion(@PathVariable long id, @RequestBody PlanCommandRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plans.newVersion(id, request.lockVersion()));
    }

    @Operation(summary = "Retire", description = "No se asigna más; las asignaciones existentes continúan.")
    @PostMapping("/{id}/retire")
    public PlanDto retire(@PathVariable long id, @RequestBody PlanCommandRequest request) {
        return plans.retire(id, request.lockVersion());
    }

    @Operation(summary = "Simulate", description = "SOLO informativo: no crea cargos, facturas ni cobros.")
    @PostMapping("/{id}/simulate")
    public SimulationDto simulate(@PathVariable long id, @RequestBody SimulationRequest request) {
        return plans.simulate(id, request);
    }
}
