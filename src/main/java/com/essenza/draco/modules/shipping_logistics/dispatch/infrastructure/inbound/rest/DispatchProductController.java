package com.essenza.draco.modules.shipping_logistics.dispatch.infrastructure.inbound.rest;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.essenza.draco.modules.shipping_logistics.dispatch.application.input.dispatch_product.CreateDispatchProductUseCase;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.input.dispatch_product.UpdateDispatchProductUseCase;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.input.dispatch_product.DeleteDispatchProductUseCase;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.input.dispatch_product.FindDispatchProductByIdUseCase;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.input.dispatch_product.FindDispatchProductsUseCase;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.dispatch_product.DispatchProductDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.dispatch_product.CreateDispatchProductDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.dispatch_product.UpdateDispatchProductDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/dispatch/dispatch_products")
@Tag(name = "Dispatch Product")
public class DispatchProductController {

    private final CreateDispatchProductUseCase createDispatchProduct;
    private final UpdateDispatchProductUseCase updateDispatchProduct;
    private final DeleteDispatchProductUseCase deleteDispatchProduct;
    private final FindDispatchProductByIdUseCase findDispatchProductById;
    private final FindDispatchProductsUseCase findDispatchProducts;

    public DispatchProductController(CreateDispatchProductUseCase createDispatchProduct,
                                     UpdateDispatchProductUseCase updateDispatchProduct,
                                     DeleteDispatchProductUseCase deleteDispatchProduct,
                                     FindDispatchProductByIdUseCase findDispatchProductById,
                                     FindDispatchProductsUseCase findDispatchProducts) {
        this.createDispatchProduct = createDispatchProduct;
        this.updateDispatchProduct = updateDispatchProduct;
        this.deleteDispatchProduct = deleteDispatchProduct;
        this.findDispatchProductById = findDispatchProductById;
        this.findDispatchProducts = findDispatchProducts;
    }

    @Operation(summary = "Create dispatch product", description = "Creates a new dispatch product and returns it with its generated ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Dispatch product created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = DispatchProductDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<DispatchProductDto> create(
            @RequestBody(description = "Dispatch product to create", required = true,
                    content = @Content(schema = @Schema(implementation = CreateDispatchProductDto.class)))
            @org.springframework.web.bind.annotation.RequestBody CreateDispatchProductDto input) {
        DispatchProductDto created = createDispatchProduct.create(input);
        return ResponseEntity.created(URI.create("/dispatch-products/" )).body(created);
    }

    @Operation(summary = "Update dispatch product", description = "Updates an existing dispatch product by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dispatch product updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = DispatchProductDto.class))),
            @ApiResponse(responseCode = "404", description = "Dispatch product not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<DispatchProductDto> update(
            @Parameter(description = "Dispatch product ID", required = true) @PathVariable Long id,
            @RequestBody(description = "Dispatch product data to update", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateDispatchProductDto.class)))
            @org.springframework.web.bind.annotation.RequestBody UpdateDispatchProductDto input) {
        DispatchProductDto updated = updateDispatchProduct.update(id, input);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Get dispatch product by ID", description = "Returns a dispatch product by its ID or 404 if not found")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dispatch product found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = DispatchProductDto.class))),
            @ApiResponse(responseCode = "404", description = "Dispatch product not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<DispatchProductDto> getById(@Parameter(description = "Dispatch product ID", required = true) @PathVariable Long id) {
        Optional<DispatchProductDto> result = findDispatchProductById.findById(id);
        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @Operation(summary = "List dispatch products", description = "Returns all dispatch products")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of dispatch products",
                    content = @Content(mediaType = "application/json"))
    })
    @GetMapping
    public List<DispatchProductDto> getAll() {
        return findDispatchProducts.findAll();
    }

    @Operation(summary = "Delete dispatch product", description = "Deletes a dispatch product by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Dispatch product deleted", content = @Content),
            @ApiResponse(responseCode = "404", description = "Dispatch product not found", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@Parameter(description = "Dispatch product ID", required = true) @PathVariable Long id) {
        boolean deleted = deleteDispatchProduct.deleteById(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
