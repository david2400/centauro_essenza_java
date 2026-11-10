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

import com.essenza.draco.modules.shipping_logistics.product_distribution.application.input.shipping_cost.CreateShippingCostUseCase;
import com.essenza.draco.modules.shipping_logistics.product_distribution.application.input.shipping_cost.UpdateShippingCostUseCase;
import com.essenza.draco.modules.shipping_logistics.product_distribution.application.input.shipping_cost.DeleteShippingCostUseCase;
import com.essenza.draco.modules.shipping_logistics.product_distribution.application.input.shipping_cost.FindShippingCostByIdUseCase;
import com.essenza.draco.modules.shipping_logistics.product_distribution.application.input.shipping_cost.FindShippingCostsUseCase;
import com.essenza.draco.modules.shipping_logistics.product_distribution.application.dto.shipping_cost.ShippingCostDto;
import com.essenza.draco.modules.shipping_logistics.product_distribution.application.dto.shipping_cost.CreateShippingCostDto;
import com.essenza.draco.modules.shipping_logistics.product_distribution.application.dto.shipping_cost.UpdateShippingCostDto;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/product_distribution/shipping_costs")
@Tag(name = "Shipping Costs")
public class ShippingCostController {

    private final CreateShippingCostUseCase createShippingCost;
    private final UpdateShippingCostUseCase updateShippingCost;
    private final DeleteShippingCostUseCase deleteShippingCost;
    private final FindShippingCostByIdUseCase findShippingCostById;
    private final FindShippingCostsUseCase findShippingCosts;

    public ShippingCostController(CreateShippingCostUseCase createShippingCost,
                                 UpdateShippingCostUseCase updateShippingCost,
                                 DeleteShippingCostUseCase deleteShippingCost,
                                 FindShippingCostByIdUseCase findShippingCostById,
                                 FindShippingCostsUseCase findShippingCosts) {
        this.createShippingCost = createShippingCost;
        this.updateShippingCost = updateShippingCost;
        this.deleteShippingCost = deleteShippingCost;
        this.findShippingCostById = findShippingCostById;
        this.findShippingCosts = findShippingCosts;
    }

    @Operation(summary = "Create shipping cost", description = "Creates a new shipping cost calculation and returns it with its generated ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Shipping cost created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ShippingCostDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<ShippingCostDto> create(
            @RequestBody(description = "Shipping cost to create", required = true,
                    content = @Content(schema = @Schema(implementation = CreateShippingCostDto.class)))
            @org.springframework.web.bind.annotation.RequestBody CreateShippingCostDto input) {
        ShippingCostDto created = createShippingCost.create(input);
        return ResponseEntity.created(URI.create("/shipping-costs/" + created.getId())).body(created);
    }

    @Operation(summary = "Update shipping cost", description = "Updates an existing shipping cost by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Shipping cost updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ShippingCostDto.class))),
            @ApiResponse(responseCode = "404", description = "Shipping cost not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<ShippingCostDto> update(
            @Parameter(description = "Shipping cost ID", required = true) @PathVariable Long id,
            @RequestBody(description = "Shipping cost data to update", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateShippingCostDto.class)))
            @org.springframework.web.bind.annotation.RequestBody UpdateShippingCostDto input) {
        ShippingCostDto updated = updateShippingCost.update(id, input);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Get shipping cost by ID", description = "Returns a shipping cost by its ID or 404 if not found")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Shipping cost found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ShippingCostDto.class))),
            @ApiResponse(responseCode = "404", description = "Shipping cost not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<ShippingCostDto> getById(@Parameter(description = "Shipping cost ID", required = true) @PathVariable Long id) {
        Optional<ShippingCostDto> result = findShippingCostById.findById(id);
        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @Operation(summary = "List shipping costs", description = "Returns all shipping costs")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of shipping costs",
                    content = @Content(mediaType = "application/json"))
    })
    @GetMapping
    public List<ShippingCostDto> getAll() {
        return findShippingCosts.findAll();
    }

    @Operation(summary = "Delete shipping cost", description = "Deletes a shipping cost by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Shipping cost deleted", content = @Content),
        @ApiResponse(responseCode = "404", description = "Shipping cost not found", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@Parameter(description = "Shipping cost ID", required = true) @PathVariable Long id) {
        boolean deleted = deleteShippingCost.deleteById(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
