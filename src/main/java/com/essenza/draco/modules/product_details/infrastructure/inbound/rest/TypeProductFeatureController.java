package com.essenza.draco.modules.product_details.infrastructure.inbound.rest;

import com.essenza.draco.modules.product_details.application.input.type_product_feature.*;
import com.essenza.draco.modules.product_details.application.dto.type_product_feature.CreateTypeProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.type_product_feature.TypeProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.type_product_feature.UpdateTypeProductFeatureDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
/**
 * @deprecated Fase 4: usar /catalog/attributes, /catalog/product_templates y
 * /catalog/products/{id}/attributes. Opera sobre las tablas antiguas (no sincronizadas); se retira en la Fase 7.
 */
@Deprecated(since = "F4", forRemoval = true)
@RestController
@RequestMapping("/product_details/type_product_features")
@Tag(name = "Type Product Features", description = "API para gestionar las relaciones entre tipos de producto y características")
public class TypeProductFeatureController {

    private final CreateTypeProductFeatureUseCase createUseCase;
    private final UpdateTypeProductFeatureUseCase updateUseCase;
    private final DeleteTypeProductFeatureUseCase deleteUseCase;
    private final FindTypeProductFeaturesUseCase findAllUseCase;
    private final FindTypeProductFeatureByIdUseCase findByIdUseCase;

//    public TypeProductFeatureController(
//            CreateTypeProductFeatureUseCase createUseCase,
//            UpdateTypeProductFeatureUseCase updateUseCase,
//            DeleteTypeProductFeatureUseCase deleteUseCase,
//            FindTypeProductFeaturesUseCase findAllUseCase,
//            FindTypeProductFeatureByIdUseCase findByIdUseCase) {
//        this.createUseCase = createUseCase;
//        this.updateUseCase = updateUseCase;
//        this.deleteUseCase = deleteUseCase;
//        this.findAllUseCase = findAllUseCase;
//        this.findByIdUseCase = findByIdUseCase;
//    }

    @PostMapping
    @Operation(summary = "Crear una nueva relación entre tipo de producto y característica")
    public ResponseEntity<TypeProductFeatureDto> create(@Valid @RequestBody CreateTypeProductFeatureDto input) {
        var result = createUseCase.create(input);
        return new ResponseEntity<>(result, HttpStatus.CREATED);
    }

    @PutMapping("/{typeProductId}")
    @Operation(summary = "Actualizar la característica asignada a un tipo de producto")
    public ResponseEntity<TypeProductFeatureDto> update(
            @PathVariable Long typeProductId,
            @Valid @RequestBody UpdateTypeProductFeatureDto input) {
        var result = updateUseCase.update(typeProductId, input);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{typeProductId}")
    @Operation(summary = "Eliminar la relación para un tipo de producto")
    public ResponseEntity<Void> delete(@PathVariable Long typeProductId) {
        boolean deleted = deleteUseCase.deleteByTypeProductId(typeProductId);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/{typeProductId}")
    @Operation(summary = "Obtener la característica de un tipo de producto específico")
    public ResponseEntity<TypeProductFeatureDto> findById(@PathVariable Long typeProductId) {
        return findByIdUseCase.findByTypeProductId(typeProductId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Obtener todas las relaciones entre tipos de producto y características")
    public ResponseEntity<List<TypeProductFeatureDto>> findAll() {
        var results = findAllUseCase.findAll();
        return ResponseEntity.ok(results);
    }
}
