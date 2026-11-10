package com.essenza.draco.modules.sales.infrastructure.inbound.rest;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.CategoryDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.CheckoutRequest;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.CheckoutResultDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.OrderViewDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.PageDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.PayRequest;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ProductCardDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ProductDetailDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ShippingQuoteDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.StoreInfoDto;
import com.essenza.draco.modules.sales.application.services.storefront.StorefrontService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * API pública de la tienda en línea (sin sesión): agregar {@code /storefront/**} a las rutas públicas. Comprador
 * invitado; la empresa sale de la configuración, nunca de la petición. Apagada por defecto (404).
 */
@RestController
@RequestMapping("/storefront")
@Tag(name = "Storefront", description = "Tienda pública: catálogo multivendedor, compra de invitado y seguimiento del pedido")
public class StorefrontController {

    private final StorefrontService store;
    private final boolean trustForwarded;

    public StorefrontController(StorefrontService store,
                                @Value("${essenza.storefront.trust-forwarded-for:false}") boolean trustForwarded) {
        this.store = store;
        this.trustForwarded = trustForwarded;
    }

    @Operation(summary = "Store info", description = "Nombre, moneda, medios de pago en línea, tipos de documento y envío.")
    @GetMapping("/info")
    public ResponseEntity<StoreInfoDto> info() {
        return cached(store.info());
    }

    @Operation(summary = "Categories", description = "Categorías con productos a la venta.")
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDto>> categories() {
        return cached(store.categories());
    }

    @Operation(summary = "Products", description = "Productos a la venta. q: texto; category: id; sort: NEWEST o NAME.")
    @GetMapping("/products")
    public ResponseEntity<PageDto<ProductCardDto>> products(@RequestParam(value = "q", required = false) String q,
                                                            @RequestParam(value = "category", required = false) Long category,
                                                            @RequestParam(value = "sort", required = false) String sort,
                                                            @RequestParam(value = "page", required = false) Integer page,
                                                            @RequestParam(value = "size", required = false) Integer size) {
        return cached(store.search(q, category, sort, page, size));
    }

    @Operation(summary = "Product", description = "Ficha por slug (o id): SKU y quién los vende (tienda y vendedores).")
    @GetMapping("/products/{slug}")
    public ResponseEntity<ProductDetailDto> product(@PathVariable String slug) {
        return cached(store.product(slug));
    }

    @Operation(summary = "Shipping quote", description = "Costo de envío para un departamento y ciudad y un subtotal "
            + "(available=false: no se envía a ese lugar).")
    @GetMapping("/shipping")
    public ResponseEntity<ShippingQuoteDto> shipping(@RequestParam(value = "state", required = false) String state,
                                                     @RequestParam(value = "city", required = false) String city,
                                                     @RequestParam(value = "subtotal", required = false) java.math.BigDecimal subtotal) {
        return cached(store.shipping(state, city, subtotal));
    }

    @Operation(summary = "Checkout", description = "Compra de invitado: crea la compra y el link de pago. Responde el secreto del "
            + "enlace de seguimiento (solo esta vez).")
    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResultDto> checkout(@RequestBody CheckoutRequest request, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(store.checkout(request, client(http)));
    }

    @Operation(summary = "Order", description = "Estado del pedido por su enlace privado.")
    @GetMapping("/orders/{token}")
    public ResponseEntity<OrderViewDto> order(@PathVariable String token, HttpServletRequest http) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(store.order(token, client(http)));
    }

    @Operation(summary = "Pay", description = "Nuevo link de pago por el saldo del pedido.")
    @PostMapping("/orders/{token}/pay")
    public ResponseEntity<OrderViewDto> pay(@PathVariable String token, @RequestBody PayRequest request, HttpServletRequest http) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(store.pay(token, request == null ? null : request.paymentMethodId(), client(http)));
    }

    /** IP del comprador para el límite de intentos (X-Forwarded-For solo detrás de un proxy de confianza). */
    String client(HttpServletRequest http) {
        if (trustForwarded) {
            String forwarded = http.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String first = forwarded.split(",")[0].trim();
                if (first.length() <= 45) {
                    return first;
                }
            }
        }
        return http.getRemoteAddr();
    }

    private static <T> ResponseEntity<T> cached(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(30, java.util.concurrent.TimeUnit.SECONDS).cachePublic()).body(body);
    }
}
