package com.essenza.draco.modules.sales.application.dto.dispatch;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Tablero de despachos (contrato JSON en snake_case). */
public final class DispatchDtos {

    private DispatchDtos() {
    }

    /**
     * {@code urgency}: NONE, ON_TIME, DUE_TODAY u OVERDUE. {@code consignment}: subpedido externo
     * que despacha la tienda desde la mercancía del vendedor en consignación.
     */
    public record DispatchRowDto(Long orderId, String orderNumber, String state, String deliveryMethod, Long purchaseId,
                                 String purchaseNumber, Long sellerId, String sellerName, boolean thirdParty,
                                 String customerName, String shipCity, BigDecimal total, Instant paidAt, LocalDate shipBy,
                                 String urgency, long daysLate, Instant shippedAt, Instant deliveredAt, Long carrierId,
                                 String carrierName, String trackingNumber, boolean consignment) {
    }

    /** {@code onTimeRate}: porcentaje (0–100) de despachos a tiempo en 30 días; null si no hubo. */
    public record DispatchStatsDto(long toShip, long overdue, long dueToday, long inTransit, long delivered30,
                                   long shipped30, long onTime30, BigDecimal onTimeRate, long withoutShipBy,
                                   List<SellerLoadDto> bySeller) {
    }

    public record SellerLoadDto(Long sellerId, String sellerName, boolean thirdParty, long toShip, long overdue) {
    }

    public record BackfillResultDto(int updated) {
    }
}
