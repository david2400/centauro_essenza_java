package com.essenza.draco.modules.shipping_logistics.product_distribution.infrastructure.inbound.rest;

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

import com.essenza.draco.modules.shipping_logistics.product_distribution.application.input.delivery_estimate.CreateDeliveryEstimateUseCase;
import com.essenza.draco.modules.shipping_logistics.product_distribution.application.input.delivery_estimate.UpdateDeliveryEstimateUseCase;
import com.essenza.draco.modules.shipping_logistics.product_distribution.application.input.delivery_estimate.DeleteDeliveryEstimateUseCase;
import com.essenza.draco.modules.shipping_logistics.product_distribution.application.input.delivery_estimate.FindDeliveryEstimateByIdUseCase;
import com.essenza.draco.modules.shipping_logistics.product_distribution.application.input.delivery_estimate.FindDeliveryEstimatesUseCase;
import com.essenza.draco.modules.shipping_logistics.product_distribution.application.dto.delivery_estimate.DeliveryEstimateDto;
import com.essenza.draco.modules.shipping_logistics.product_distribution.application.dto.delivery_estimate.CreateDeliveryEstimateDto;
import com.essenza.draco.modules.shipping_logistics.product_distribution.application.dto.delivery_estimate.UpdateDeliveryEstimateDto;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/product_distribution/delivery_estimates")
@Tag(name = "Delivery Estimates")
public class DeliveryEstimateController {

    private final CreateDeliveryEstimateUseCase createDeliveryEstimate;
    private final UpdateDeliveryEstimateUseCase updateDeliveryEstimate;
    private final DeleteDeliveryEstimateUseCase deleteDeliveryEstimate;
    private final FindDeliveryEstimateByIdUseCase findDeliveryEstimateById;
    private final FindDeliveryEstimatesUseCase findDeliveryEstimates;

    public DeliveryEstimateController(CreateDeliveryEstimateUseCase createDeliveryEstimate,
                                     UpdateDeliveryEstimateUseCase updateDeliveryEstimate,
                                     DeleteDeliveryEstimateUseCase deleteDeliveryEstimate,
                                     FindDeliveryEstimateByIdUseCase findDeliveryEstimateById,
                                     FindDeliveryEstimatesUseCase findDeliveryEstimates) {
        this.createDeliveryEstimate = createDeliveryEstimate;
        this.updateDeliveryEstimate = updateDeliveryEstimate;
        this.deleteDeliveryEstimate = deleteDeliveryEstimate;
        this.findDeliveryEstimateById = findDeliveryEstimateById;
        this.findDeliveryEstimates = findDeliveryEstimates;
    }

    @Operation(summary = "Create delivery estimate", description = "Creates a new delivery estimate and returns it with its generated ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Delivery estimate created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeliveryEstimateDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<DeliveryEstimateDto> create(
            @RequestBody(description = "Delivery estimate to create", required = true,
                    content = @Content(schema = @Schema(implementation = CreateDeliveryEstimateDto.class)))
            @org.springframework.web.bind.annotation.RequestBody CreateDeliveryEstimateDto input) {
        DeliveryEstimateDto created = createDeliveryEstimate.create(input);
        return ResponseEntity.created(URI.create("/delivery-estimates/" + created.getId())).body(created);
    }

    @Operation(summary = "Update delivery estimate", description = "Updates an existing delivery estimate by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Delivery estimate updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeliveryEstimateDto.class))),
            @ApiResponse(responseCode = "404", description = "Delivery estimate not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<DeliveryEstimateDto> update(
            @Parameter(description = "Delivery estimate ID", required = true) @PathVariable Long id,
            @RequestBody(description = "Delivery estimate data to update", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateDeliveryEstimateDto.class)))
            @org.springframework.web.bind.annotation.RequestBody UpdateDeliveryEstimateDto input) {
        DeliveryEstimateDto updated = updateDeliveryEstimate.update(id, input);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Get delivery estimate by ID", description = "Returns a delivery estimate by its ID or 404 if not found")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Delivery estimate found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeliveryEstimateDto.class))),
            @ApiResponse(responseCode = "404", description = "Delivery estimate not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<DeliveryEstimateDto> getById(@Parameter(description = "Delivery estimate ID", required = true) @PathVariable Long id) {
        Optional<DeliveryEstimateDto> result = findDeliveryEstimateById.findById(id);
        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @Operation(summary = "List delivery estimates", description = "Returns all delivery estimates")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of delivery estimates",
                    content = @Content(mediaType = "application/json"))
    })
    @GetMapping
    public List<DeliveryEstimateDto> getAll() {
        return findDeliveryEstimates.findAll();
    }

    @Operation(summary = "Delete delivery estimate", description = "Deletes a delivery estimate by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Delivery estimate deleted", content = @Content),
        @ApiResponse(responseCode = "404", description = "Delivery estimate not found", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@Parameter(description = "Delivery estimate ID", required = true) @PathVariable Long id) {
        boolean deleted = deleteDeliveryEstimate.deleteById(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
