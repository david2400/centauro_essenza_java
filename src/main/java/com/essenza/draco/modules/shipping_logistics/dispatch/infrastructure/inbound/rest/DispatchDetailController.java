package com.essenza.draco.modules.shipping_logistics.dispatch.infrastructure.inbound.rest;

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

import com.essenza.draco.modules.shipping_logistics.dispatch.application.input.dispatch_detail.CreateDispatchDetailUseCase;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.input.dispatch_detail.UpdateDispatchDetailUseCase;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.input.dispatch_detail.DeleteDispatchDetailUseCase;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.input.dispatch_detail.FindDispatchDetailByIdUseCase;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.input.dispatch_detail.FindDispatchDetailsUseCase;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.dispatch_detail.DispatchDetailDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.dispatch_detail.CreateDispatchDetailDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.dispatch_detail.UpdateDispatchDetailDto;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/shipping_logistics/dispatch_details")
@Tag(name = "Dispatch Details")
public class DispatchDetailController {

    private final CreateDispatchDetailUseCase createDispatchDetail;
    private final UpdateDispatchDetailUseCase updateDispatchDetail;
    private final DeleteDispatchDetailUseCase deleteDispatchDetail;
    private final FindDispatchDetailByIdUseCase findDispatchDetailById;
    private final FindDispatchDetailsUseCase findDispatchDetails;

    public DispatchDetailController(CreateDispatchDetailUseCase createDispatchDetail,
                                    UpdateDispatchDetailUseCase updateDispatchDetail,
                                    DeleteDispatchDetailUseCase deleteDispatchDetail,
                                    FindDispatchDetailByIdUseCase findDispatchDetailById,
                                    FindDispatchDetailsUseCase findDispatchDetails) {
        this.createDispatchDetail = createDispatchDetail;
        this.updateDispatchDetail = updateDispatchDetail;
        this.deleteDispatchDetail = deleteDispatchDetail;
        this.findDispatchDetailById = findDispatchDetailById;
        this.findDispatchDetails = findDispatchDetails;
    }

    @Operation(summary = "Create dispatch detail", description = "Creates a new dispatch detail and returns it with its generated ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Dispatch detail created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = DispatchDetailDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<DispatchDetailDto> create(
            @RequestBody(description = "Dispatch detail to create", required = true,
                    content = @Content(schema = @Schema(implementation = CreateDispatchDetailDto.class)))
            @org.springframework.web.bind.annotation.RequestBody CreateDispatchDetailDto input) {
        DispatchDetailDto created = createDispatchDetail.create(input);
        return ResponseEntity.created(URI.create("/dispatch-details/" )).body(created);
    }

    @Operation(summary = "Update dispatch detail", description = "Updates an existing dispatch detail by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dispatch detail updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = DispatchDetailDto.class))),
            @ApiResponse(responseCode = "404", description = "Dispatch detail not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<DispatchDetailDto> update(
            @Parameter(description = "Dispatch detail ID", required = true) @PathVariable Long id,
            @RequestBody(description = "Dispatch detail data to update", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateDispatchDetailDto.class)))
            @org.springframework.web.bind.annotation.RequestBody UpdateDispatchDetailDto input) {
        DispatchDetailDto updated = updateDispatchDetail.update(id, input);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Get dispatch detail by ID", description = "Returns a dispatch detail by its ID or 404 if not found")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dispatch detail found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = DispatchDetailDto.class))),
            @ApiResponse(responseCode = "404", description = "Dispatch detail not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<DispatchDetailDto> getById(@Parameter(description = "Dispatch detail ID", required = true) @PathVariable Long id) {
        Optional<DispatchDetailDto> result = findDispatchDetailById.findById(id);
        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @Operation(summary = "List dispatch details", description = "Returns all dispatch details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of dispatch details",
                    content = @Content(mediaType = "application/json"))
    })
    @GetMapping
    public List<DispatchDetailDto> getAll(
            @Parameter(description = "Dispatch ID (opcional): solo los de ese registro") @RequestParam(value = "dispatchProductId", required = false) Long dispatchProductId) {
        return dispatchProductId != null ? findDispatchDetails.findByDispatchProductId(dispatchProductId) : findDispatchDetails.findAll();
    }

    @Operation(summary = "Delete dispatch detail", description = "Deletes a dispatch detail by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Dispatch detail deleted", content = @Content),
        @ApiResponse(responseCode = "404", description = "Dispatch detail not found", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@Parameter(description = "Dispatch detail ID", required = true) @PathVariable Long id) {
        boolean deleted = deleteDispatchDetail.deleteById(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
