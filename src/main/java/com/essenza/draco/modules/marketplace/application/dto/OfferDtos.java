package com.essenza.draco.modules.marketplace.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Contratos del API de ofertas (snake_case por la configuración global). */
public final class OfferDtos {

    private OfferDtos() {
    }

    /**
     * Alta o edición. {@code sellerId} y {@code skuId} solo se toman al crear (y se validan contra
     * la empresa del token y el catálogo); al editar se ignoran. {@code lockVersion} es
     * obligatoria al editar.
     */
    public record SaveOfferRequest(
            Long sellerId,
            Long skuId,
            String currency,
            BigDecimal price,
            Integer availableQuantity,
            Integer handlingDays,
            String condition,
            String fulfillment,
            String sellerSku,
            Integer lockVersion) {
    }

    /** action = SUBMIT | APPROVE | REJECT | PAUSE | RESUME | CLOSE. */
    public record OfferTransitionRequest(String action, String reason, Integer lockVersion) {
    }

    public record OfferSummaryDto(
            long id,
            long sellerId,
            String sellerName,
            String sellerStatus,
            long skuId,
            Long productId,
            String skuCode,
            String productName,
            String currency,
            BigDecimal price,
            int availableQuantity,
            int handlingDays,
            String condition,
            String status,
            Instant publishedAt,
            Instant updatedAt) {
    }

    /** Datos vigentes del SKU en el catálogo (no la copia). */
    public record CatalogSkuDto(long skuId, Long productId, String code, String name, BigDecimal catalogPrice,
                                String catalogCurrency, boolean sellable) {
    }

    public record OfferDetailDto(
            long id,
            long sellerId,
            String sellerName,
            String sellerStatus,
            CatalogSkuDto sku,
            String currency,
            BigDecimal price,
            int availableQuantity,
            int handlingDays,
            String condition,
            String fulfillment,
            String sellerSku,
            String status,
            String statusReason,
            Instant statusChangedAt,
            Instant publishedAt,
            /* Publicada, vendedor aprobado y SKU vendible. */
            boolean visible,
            /* Diferencia % contra el precio del catálogo y si supera el umbral de alerta. */
            BigDecimal priceDeviationPercent,
            boolean priceAlert,
            List<String> allowedActions,
            List<String> editableFields,
            int lockVersion,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {
    }

    public record OfferEventDto(long id, String type, String fromStatus, String toStatus, String reason, String detail,
                                String actorId, String actorName, Instant createdAt) {
    }

    public record OfferStatsDto(long total, long draft, long pendingReview, long active, long rejected, long paused,
                                long closed) {
    }

    /** Opción para buscadores (vendedores). */
    public record SellerOptionDto(long id, String code, String name, String kind, String status) {
    }
}
