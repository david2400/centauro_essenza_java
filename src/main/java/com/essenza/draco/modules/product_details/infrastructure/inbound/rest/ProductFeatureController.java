package com.essenza.draco.modules.product_details.infrastructure.inbound.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.essenza.draco.modules.product_details.application.input.product_feature.CreateProductFeatureUseCase;
import com.essenza.draco.modules.product_details.application.input.product_feature.UpdateProductFeatureUseCase;
import com.essenza.draco.modules.product_details.application.input.product_feature.DeleteProductFeatureUseCase;
import com.essenza.draco.modules.product_details.application.input.product_feature.FindProductFeatureByIdUseCase;
import com.essenza.draco.modules.product_details.application.input.product_feature.FindProductFeaturesUseCase;
import com.essenza.draco.modules.product_details.application.dto.product_feature.ProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.product_feature.CreateProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.product_feature.UpdateProductFeatureDto;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
/**
 * @deprecated Fase 4: usar /catalog/attributes, /catalog/product_templates y
 * /catalog/products/{id}/attributes. Opera sobre las tablas antiguas (no sincronizadas); se retira en la Fase 7.
 */
@Deprecated(since = "F4", forRemoval = true)
@RestController
@RequestMapping("/product_details/product_features")
@Tag(name = "Product Feature")
public class ProductFeatureController {

    private final CreateProductFeatureUseCase createProductFeature;
    private final UpdateProductFeatureUseCase updateProductFeature;
    private final DeleteProductFeatureUseCase deleteProductFeature;
    private final FindProductFeatureByIdUseCase findProductFeatureById;
    private final FindProductFeaturesUseCase findProductFeatures;

//    public ProductFeatureController(CreateProductFeatureUseCase createProductFeature,
//                                    UpdateProductFeatureUseCase updateProductFeature,
//                                    DeleteProductFeatureUseCase deleteProductFeature,
//                                    FindProductFeatureByIdUseCase findProductFeatureById,
//                                    FindProductFeaturesUseCase findProductFeatures) {
//        this.createProductFeature = createProductFeature;
//        this.updateProductFeature = updateProductFeature;
//        this.deleteProductFeature = deleteProductFeature;
//        this.findProductFeatureById = findProductFeatureById;
//        this.findProductFeatures = findProductFeatures;
//    }

    @Operation(summary = "Create product feature", description = "Creates a new product-feature link and returns it")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Product feature created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductFeatureDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<ProductFeatureDto> create(
            @RequestBody(description = "Product feature to create", required = true,
                    content = @Content(schema = @Schema(implementation = CreateProductFeatureDto.class)))
            @org.springframework.web.bind.annotation.RequestBody CreateProductFeatureDto input) {
        ProductFeatureDto created = createProductFeature.create(input);
        return ResponseEntity.created(URI.create("/product-features/" + created.getProductId() + "/" + created.getFeatureId())).body(created);
    }

    @Operation(summary = "Update product feature", description = "Updates a product-feature link by productId and featureId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product feature updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductFeatureDto.class))),
            @ApiResponse(responseCode = "404", description = "Product feature not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content)
    })
    @PutMapping("/{productId}/{featureId}")
    public ResponseEntity<ProductFeatureDto> update(
            @Parameter(description = "Product ID", required = true) @PathVariable Long productId,
            @Parameter(description = "Feature ID", required = true) @PathVariable Long featureId,
            @RequestBody(description = "Product feature data to update", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateProductFeatureDto.class)))
            @org.springframework.web.bind.annotation.RequestBody UpdateProductFeatureDto input) {
        ProductFeatureDto updated = updateProductFeature.update(productId, featureId, input);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Get product feature by IDs", description = "Returns a product-feature link by productId and featureId or 404 if not found")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product feature found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductFeatureDto.class))),
            @ApiResponse(responseCode = "404", description = "Product feature not found", content = @Content)
    })
    @GetMapping("/{productId}/{featureId}")
    public ResponseEntity<ProductFeatureDto> getById(
            @Parameter(description = "Product ID", required = true) @PathVariable Long productId,
            @Parameter(description = "Feature ID", required = true) @PathVariable Long featureId) {
        Optional<ProductFeatureDto> result = findProductFeatureById.findById(productId, featureId);
        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @Operation(summary = "List product features", description = "Returns all product-feature links")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of product features",
                    content = @Content(mediaType = "application/json"))
    })
    @GetMapping
    public List<ProductFeatureDto> getAll() {
        return findProductFeatures.findAll();
    }

    @Operation(summary = "Delete product feature", description = "Deletes a product-feature link by productId and featureId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Product feature deleted", content = @Content),
            @ApiResponse(responseCode = "404", description = "Product feature not found", content = @Content)
    })
    @DeleteMapping("/{productId}/{featureId}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Product ID", required = true) @PathVariable Long productId,
            @Parameter(description = "Feature ID", required = true) @PathVariable Long featureId) {
        Boolean deleted = deleteProductFeature.deleteById(productId, featureId);
        return Boolean.TRUE.equals(deleted) ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
