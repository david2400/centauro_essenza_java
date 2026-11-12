package com.essenza.draco.modules.devolution.infrastructure.inbound.rest;

import com.essenza.draco.modules.devolution.application.input.evidence.*;
import com.essenza.draco.modules.devolution.application.dto.evidence.CreateEvidenceDto;
import com.essenza.draco.modules.devolution.application.dto.evidence.EvidenceDto;
import com.essenza.draco.modules.devolution.application.dto.evidence.UpdateEvidenceDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/devolution/order_devolution_evidences")
@Tag(name = "Order Devolution Evidences")
public class EvidenceController {

    private final CreateEvidenceUseCase createEvidence;
    private final UpdateEvidenceUseCase updateEvidence;
    private final DeleteEvidenceUseCase deleteEvidence;
    private final FindEvidenceByIdUseCase findEvidenceById;
    private final FindEvidencesUseCase findEvidences;

    public EvidenceController(CreateEvidenceUseCase createEvidence,
                              UpdateEvidenceUseCase updateEvidence,
                              DeleteEvidenceUseCase deleteEvidence,
                              FindEvidenceByIdUseCase findEvidenceById,
                              FindEvidencesUseCase findEvidences) {
        this.createEvidence = createEvidence;
        this.updateEvidence = updateEvidence;
        this.deleteEvidence = deleteEvidence;
        this.findEvidenceById = findEvidenceById;
        this.findEvidences = findEvidences;
    }

    @Operation(summary = "Create order devolution evidence", description = "Creates a new order devolution evidence and returns it with its generated ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Order devolution evidence created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EvidenceDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<EvidenceDto> create(
            @RequestBody(description = "Order devolution evidence to create", required = true,
                    content = @Content(schema = @Schema(implementation = CreateEvidenceDto.class)))
            @org.springframework.web.bind.annotation.RequestBody CreateEvidenceDto input) {
        EvidenceDto created = createEvidence.create(input);
        return ResponseEntity.created(URI.create("/order-devolution-evidences/" + created.getId())).body(created);
    }

    @Operation(summary = "Update order devolution evidence", description = "Updates an existing order devolution evidence by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order devolution evidence updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EvidenceDto.class))),
            @ApiResponse(responseCode = "404", description = "Order devolution evidence not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<EvidenceDto> update(
            @Parameter(description = "Order devolution evidence ID", required = true) @PathVariable Long id,
            @RequestBody(description = "Order devolution evidence data to update", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateEvidenceDto.class)))
            @org.springframework.web.bind.annotation.RequestBody UpdateEvidenceDto input) {
        EvidenceDto updated = updateEvidence.update(id, input);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Get order devolution evidence by ID", description = "Returns an order devolution evidence by its ID or 404 if not found")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order devolution evidence found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EvidenceDto.class))),
            @ApiResponse(responseCode = "404", description = "Order devolution evidence not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<EvidenceDto> getById(@Parameter(description = "Order devolution evidence ID", required = true) @PathVariable Long id) {
        Optional<EvidenceDto> result = findEvidenceById.findById(id);
        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @Operation(summary = "List order devolution evidences", description = "Returns all order devolution evidences")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of order devolution evidences",
                    content = @Content(mediaType = "application/json"))
    })
    @GetMapping
    public List<EvidenceDto> getAll(
            @Parameter(description = "Return ID (opcional): solo los de ese registro") @RequestParam(value = "orderDevolutionId", required = false) Long orderDevolutionId) {
        return orderDevolutionId != null ? findEvidences.findByOrderDevolutionId(orderDevolutionId) : findEvidences.findAll();
    }

    @Operation(summary = "Delete order devolution evidence", description = "Deletes an order devolution evidence by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Order devolution evidence deleted", content = @Content),
            @ApiResponse(responseCode = "404", description = "Order devolution evidence not found", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@Parameter(description = "Order devolution evidence ID", required = true) @PathVariable Long id) {
        boolean deleted = deleteEvidence.deleteById(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
