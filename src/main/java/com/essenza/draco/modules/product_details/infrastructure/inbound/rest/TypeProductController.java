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

import com.essenza.draco.modules.product_details.application.input.type_product.CreateTypeProductUseCase;
import com.essenza.draco.modules.product_details.application.input.type_product.UpdateTypeProductUseCase;
import com.essenza.draco.modules.product_details.application.input.type_product.DeleteTypeProductUseCase;
import com.essenza.draco.modules.product_details.application.input.type_product.FindTypeProductByIdUseCase;
import com.essenza.draco.modules.product_details.application.input.type_product.FindTypeProductsUseCase;
import com.essenza.draco.modules.product_details.application.dto.type_product.TypeProductDto;
import com.essenza.draco.modules.product_details.application.dto.type_product.CreateTypeProductDto;
import com.essenza.draco.modules.product_details.application.dto.type_product.UpdateTypeProductDto;

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
@RequestMapping("/product_details/type_products")
@Tag(name = "Type Product")
public class TypeProductController {

    private final CreateTypeProductUseCase createTypeProduct;
    private final UpdateTypeProductUseCase updateTypeProduct;
    private final DeleteTypeProductUseCase deleteTypeProduct;
    private final FindTypeProductByIdUseCase findTypeProductById;
    private final FindTypeProductsUseCase findTypeProducts;

//    public TypeProductController(CreateTypeProductUseCase createTypeProduct,
//                                 UpdateTypeProductUseCase updateTypeProduct,
//                                 DeleteTypeProductUseCase deleteTypeProduct,
//                                 FindTypeProductByIdUseCase findTypeProductById,
//                                 FindTypeProductsUseCase findTypeProducts) {
//        this.createTypeProduct = createTypeProduct;
//        this.updateTypeProduct = updateTypeProduct;
//        this.deleteTypeProduct = deleteTypeProduct;
//        this.findTypeProductById = findTypeProductById;
//        this.findTypeProducts = findTypeProducts;
//    }

    @Operation(summary = "Create type product", description = "Creates a new type product and returns it with its generated ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Type product created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TypeProductDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<TypeProductDto> create(
            @RequestBody(description = "Type product to create", required = true,
                    content = @Content(schema = @Schema(implementation = CreateTypeProductDto.class)))
            @org.springframework.web.bind.annotation.RequestBody CreateTypeProductDto input) {
        TypeProductDto created = createTypeProduct.create(input);
        return ResponseEntity.created(URI.create("/type-products/" )).body(created);
    }

    @Operation(summary = "Update type product", description = "Updates an existing type product by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Type product updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TypeProductDto.class))),
            @ApiResponse(responseCode = "404", description = "Type product not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<TypeProductDto> update(
            @Parameter(description = "Type product ID", required = true) @PathVariable Long id,
            @RequestBody(description = "Type product data to update", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateTypeProductDto.class)))
            @org.springframework.web.bind.annotation.RequestBody UpdateTypeProductDto input) {
        TypeProductDto updated = updateTypeProduct.update(id, input);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Get type product by ID", description = "Returns a type product by its ID or 404 if not found")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Type product found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TypeProductDto.class))),
            @ApiResponse(responseCode = "404", description = "Type product not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<TypeProductDto> getById(@Parameter(description = "Type product ID", required = true) @PathVariable Long id) {
        Optional<TypeProductDto> result = findTypeProductById.findById(id);
        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @Operation(summary = "List type products", description = "Returns all type products")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of type products",
                    content = @Content(mediaType = "application/json"))
    })
    @GetMapping
    public List<TypeProductDto> getAll() {
        return findTypeProducts.findAll();
    }

    @Operation(summary = "Delete type product", description = "Deletes a type product by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Type product deleted", content = @Content),
            @ApiResponse(responseCode = "404", description = "Type product not found", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@Parameter(description = "Type product ID", required = true) @PathVariable Long id) {
        Boolean deleted = deleteTypeProduct.deleteById(id);
        return Boolean.TRUE.equals(deleted) ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
