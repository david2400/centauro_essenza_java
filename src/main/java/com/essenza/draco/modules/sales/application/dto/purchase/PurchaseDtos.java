package com.essenza.draco.modules.sales.application.dto.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.essenza.draco.modules.sales.application.dto.order.SaveOrderDetailsDto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Compras multivendedor (contrato JSON en snake_case). */
public final class PurchaseDtos {

    private PurchaseDtos() {
    }

    /**
     * Compra con productos de varios vendedores. Se crea un subpedido por vendedor: los productos
     * propios con el precio del catálogo y los de vendedores externos con el precio de su oferta.
     * El descuento y el envío van en el subpedido propio.
     */
    public record CreatePurchaseRequest(
            @NotNull @Valid SaveOrderDetailsDto details,
            @NotEmpty @Size(max = 100) @Valid List<Line> lines) {

        @Schema(name = "PurchaseLineInput")
        public record Line(
                @NotNull Long skuId,
                @Schema(description = "Oferta de un vendedor externo; vacío = venta propia") Long offerId,
                @NotNull @Positive Integer quantity) {
        }
    }

    public record PurchaseSummaryDto(Long id, String number, String status, boolean partiallyCancelled, String channel,
                                     String currency, BigDecimal total, int subOrders, int sellers, String customerName,
                                     String customerDocNumber, Instant createdAt) {
    }

    public record SubOrderDto(Long orderId, String orderNumber, String state, BigDecimal total, Long sellerId,
                              String sellerName, boolean external, int items, int units, Instant paidAt) {
    }

    public record PurchaseDetailDto(Long id, String number, String status, boolean partiallyCancelled, String channel,
                                    String currency, BigDecimal total, BigDecimal pendingTotal, String customerName,
                                    String customerDocType, String customerDocNumber, String customerEmail,
                                    String customerPhone, String createdBy, String createdByName, Instant createdAt,
                                    List<SubOrderDto> subOrders) {
    }

    /** Vendedores que pueden vender un SKU: la tienda propia (precio de catálogo) y las ofertas activas. */
    public record SkuOptionsDto(Long skuId, String code, String name, boolean ownSellable, BigDecimal ownPrice,
                                String currency, List<OfferOptionDto> offers) {
    }

    public record OfferOptionDto(Long offerId, Long sellerId, String sellerName, BigDecimal price, int availableQuantity,
                                 int handlingDays, String condition) {
    }

    public record OrderPurchaseDto(Long purchaseId, String purchaseNumber, Long sellerId, String sellerName,
                                   boolean external, int subOrders) {
    }
}
