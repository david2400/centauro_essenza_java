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

import com.essenza.draco.modules.product_details.application.input.feature.CreateFeatureUseCase;
import com.essenza.draco.modules.product_details.application.input.feature.UpdateFeatureUseCase;
import com.essenza.draco.modules.product_details.application.input.feature.DeleteFeatureUseCase;
import com.essenza.draco.modules.product_details.application.input.feature.FindFeatureByIdUseCase;
import com.essenza.draco.modules.product_details.application.input.feature.FindFeaturesUseCase;
import com.essenza.draco.modules.product_details.application.dto.feature.FeatureDto;
import com.essenza.draco.modules.product_details.application.dto.feature.CreateFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.feature.UpdateFeatureDto;

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
@RequestMapping("/product_details/features")
@Tag(name = "Feature")
public class FeatureController {

    private final CreateFeatureUseCase createFeature;
    private final UpdateFeatureUseCase updateFeature;
    private final DeleteFeatureUseCase deleteFeature;
    private final FindFeatureByIdUseCase findFeatureById;
    private final FindFeaturesUseCase findFeatures;

//    public FeatureController(CreateFeatureUseCase createFeature,
//                             UpdateFeatureUseCase updateFeature,
//                             DeleteFeatureUseCase deleteFeature,
//                             FindFeatureByIdUseCase findFeatureById,
//                             FindFeaturesUseCase findFeatures) {
//        this.createFeature = createFeature;
//        this.updateFeature = updateFeature;
//        this.deleteFeature = deleteFeature;
//        this.findFeatureById = findFeatureById;
//        this.findFeatures = findFeatures;
//    }

    @Operation(summary = "Create feature", description = "Creates a new feature and returns it with its generated ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Feature created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = FeatureDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<FeatureDto> create(
            @RequestBody(description = "Feature to create", required = true,
                    content = @Content(schema = @Schema(implementation = CreateFeatureDto.class)))
            @org.springframework.web.bind.annotation.RequestBody CreateFeatureDto input) {
        FeatureDto created = createFeature.create(input);
        return ResponseEntity.created(URI.create("/features/" )).body(created);
    }

    @Operation(summary = "Update feature", description = "Updates an existing feature by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Feature updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = FeatureDto.class))),
            @ApiResponse(responseCode = "404", description = "Feature not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<FeatureDto> update(
            @Parameter(description = "Feature ID", required = true) @PathVariable Long id,
            @RequestBody(description = "Feature data to update", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateFeatureDto.class)))
            @org.springframework.web.bind.annotation.RequestBody UpdateFeatureDto input) {
        FeatureDto updated = updateFeature.update(id, input);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Get feature by ID", description = "Returns a feature by its ID or 404 if not found")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Feature found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = FeatureDto.class))),
            @ApiResponse(responseCode = "404", description = "Feature not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<FeatureDto> getById(@Parameter(description = "Feature ID", required = true) @PathVariable Long id) {
        Optional<FeatureDto> result = findFeatureById.findById(id);
        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @Operation(summary = "List features", description = "Returns all features")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of features",
                    content = @Content(mediaType = "application/json"))
    })
    @GetMapping
    public List<FeatureDto> getAll() {
        return findFeatures.findAll();
    }

    @Operation(summary = "Delete feature", description = "Deletes a feature by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Feature deleted", content = @Content),
            @ApiResponse(responseCode = "404", description = "Feature not found", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@Parameter(description = "Feature ID", required = true) @PathVariable Long id) {
        Boolean deleted = deleteFeature.deleteById(id);
        return Boolean.TRUE.equals(deleted) ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
