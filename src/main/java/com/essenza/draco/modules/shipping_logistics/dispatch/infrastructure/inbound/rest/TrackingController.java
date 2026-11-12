package com.essenza.draco.modules.shipping_logistics.dispatch.infrastructure.inbound.rest;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.essenza.draco.modules.shipping_logistics.dispatch.application.input.tracking.CreateTrackingUseCase;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.input.tracking.UpdateTrackingUseCase;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.input.tracking.DeleteTrackingUseCase;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.input.tracking.FindTrackingByIdUseCase;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.input.tracking.FindTrackingsUseCase;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.tracking.TrackingDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.tracking.CreateTrackingDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.tracking.UpdateTrackingDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/shipping_logistics/trackings")
@Tag(name = "Tracking")
public class TrackingController {

    private final CreateTrackingUseCase createTracking;
    private final UpdateTrackingUseCase updateTracking;
    private final DeleteTrackingUseCase deleteTracking;
    private final FindTrackingByIdUseCase findTrackingById;
    private final FindTrackingsUseCase findTrackings;

    public TrackingController(CreateTrackingUseCase createTracking,
                              UpdateTrackingUseCase updateTracking,
                              DeleteTrackingUseCase deleteTracking,
                              FindTrackingByIdUseCase findTrackingById,
                              FindTrackingsUseCase findTrackings) {
        this.createTracking = createTracking;
        this.updateTracking = updateTracking;
        this.deleteTracking = deleteTracking;
        this.findTrackingById = findTrackingById;
        this.findTrackings = findTrackings;
    }

    @Operation(summary = "Create tracking", description = "Creates a new tracking and returns it with its generated ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Tracking created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TrackingDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<TrackingDto> create(
            @RequestBody(description = "Tracking to create", required = true,
                    content = @Content(schema = @Schema(implementation = CreateTrackingDto.class)))
            @org.springframework.web.bind.annotation.RequestBody CreateTrackingDto input) {
        TrackingDto created = createTracking.create(input);
        return ResponseEntity.created(URI.create("/trackings/" )).body(created);
    }

    @Operation(summary = "Update tracking", description = "Updates an existing tracking by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tracking updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TrackingDto.class))),
            @ApiResponse(responseCode = "404", description = "Tracking not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<TrackingDto> update(
            @Parameter(description = "Tracking ID", required = true) @PathVariable Long id,
            @RequestBody(description = "Tracking data to update", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateTrackingDto.class)))
            @org.springframework.web.bind.annotation.RequestBody UpdateTrackingDto input) {
        TrackingDto updated = updateTracking.update(id, input);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Get tracking by ID", description = "Returns a tracking by its ID or 404 if not found")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tracking found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TrackingDto.class))),
            @ApiResponse(responseCode = "404", description = "Tracking not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<TrackingDto> getById(@Parameter(description = "Tracking ID", required = true) @PathVariable Long id) {
        Optional<TrackingDto> result = findTrackingById.findById(id);
        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @Operation(summary = "List trackings", description = "Returns all trackings")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of trackings",
                    content = @Content(mediaType = "application/json"))
    })
    @GetMapping
    public List<TrackingDto> getAll(
            @Parameter(description = "Dispatch ID (opcional): solo los de ese registro") @RequestParam(value = "dispatchProductId", required = false) Long dispatchProductId) {
        return dispatchProductId != null ? findTrackings.findByDispatchProductId(dispatchProductId) : findTrackings.findAll();
    }

    @Operation(summary = "Delete tracking", description = "Deletes a tracking by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Tracking deleted", content = @Content),
            @ApiResponse(responseCode = "404", description = "Tracking not found", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@Parameter(description = "Tracking ID", required = true) @PathVariable Long id) {
        boolean deleted = deleteTracking.deleteById(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
