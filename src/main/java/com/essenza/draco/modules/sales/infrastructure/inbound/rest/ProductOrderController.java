package com.essenza.draco.modules.sales.infrastructure.inbound.rest;

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

import com.essenza.draco.modules.sales.application.input.product_order.CreateProductOrderUseCase;
import com.essenza.draco.modules.sales.application.input.product_order.UpdateProductOrderUseCase;
import com.essenza.draco.modules.sales.application.input.product_order.DeleteProductOrderUseCase;
import com.essenza.draco.modules.sales.application.input.product_order.FindProductOrderByIdUseCase;
import com.essenza.draco.modules.sales.application.input.product_order.FindProductOrdersUseCase;
import com.essenza.draco.modules.sales.application.dto.product_order.ProductOrderDto;
import com.essenza.draco.modules.sales.application.dto.product_order.CreateProductOrderDto;
import com.essenza.draco.modules.sales.application.dto.product_order.UpdateProductOrderDto;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/sales/product_orders")
@Tag(name = "Product Order")
public class ProductOrderController {

    private final CreateProductOrderUseCase createProductOrder;
    private final UpdateProductOrderUseCase updateProductOrder;
    private final DeleteProductOrderUseCase deleteProductOrder;
    private final FindProductOrderByIdUseCase findProductOrderById;
    private final FindProductOrdersUseCase findProductOrders;

    public ProductOrderController(CreateProductOrderUseCase createProductOrder,
                                  UpdateProductOrderUseCase updateProductOrder,
                                  DeleteProductOrderUseCase deleteProductOrder,
                                  FindProductOrderByIdUseCase findProductOrderById,
                                  FindProductOrdersUseCase findProductOrders) {
        this.createProductOrder = createProductOrder;
        this.updateProductOrder = updateProductOrder;
        this.deleteProductOrder = deleteProductOrder;
        this.findProductOrderById = findProductOrderById;
        this.findProductOrders = findProductOrders;
    }

    @Operation(summary = "Create product order", description = "Creates a new product order and returns it with its generated ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Product order created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductOrderDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<ProductOrderDto> create(
            @RequestBody(description = "Product order to create", required = true,
                    content = @Content(schema = @Schema(implementation = CreateProductOrderDto.class)))
            @org.springframework.web.bind.annotation.RequestBody CreateProductOrderDto input) {
        ProductOrderDto created = createProductOrder.create(input);
        return ResponseEntity.created(URI.create("/product-orders/" )).body(created);
    }

    @Operation(summary = "Update product order", description = "Updates an existing product order by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product order updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductOrderDto.class))),
            @ApiResponse(responseCode = "404", description = "Product order not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<ProductOrderDto> update(
            @Parameter(description = "Product order ID", required = true) @PathVariable Long id,
            @RequestBody(description = "Product order data to update", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateProductOrderDto.class)))
            @org.springframework.web.bind.annotation.RequestBody UpdateProductOrderDto input) {
        ProductOrderDto updated = updateProductOrder.update(id, input);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Get product order by ID", description = "Returns a product order by its ID or 404 if not found")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product order found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductOrderDto.class))),
            @ApiResponse(responseCode = "404", description = "Product order not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProductOrderDto> getById(@Parameter(description = "Product order ID", required = true) @PathVariable Long id) {
        Optional<ProductOrderDto> result = findProductOrderById.findById(id);
        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @Operation(summary = "List product orders", description = "Returns all product orders")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of product orders",
                    content = @Content(mediaType = "application/json"))
    })
    @GetMapping
    public List<ProductOrderDto> getAll(
            @Parameter(description = "Order ID (opcional): solo los de ese registro") @RequestParam(value = "orderId", required = false) Long orderId) {
        return orderId != null ? findProductOrders.findByOrderId(orderId) : findProductOrders.findAll();
    }

    @Operation(summary = "Delete product order", description = "Deletes a product order by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Product order deleted", content = @Content),
            @ApiResponse(responseCode = "404", description = "Product order not found", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@Parameter(description = "Product order ID", required = true) @PathVariable Long id) {
        boolean deleted = deleteProductOrder.deleteById(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
