package com.essenza.draco.modules.marketplace.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Reclamos y disputas (JSON en snake_case). */
public final class DisputeDtos {

    private DisputeDtos() {
    }

    /** {@code reason}: NOT_RECEIVED, DEFECTIVE, NOT_AS_DESCRIBED, WRONG_ITEM u OTHER. */
    public record OpenDisputeRequest(Long orderId, String reason, String description, BigDecimal claimedAmount) {
    }

    public record RespondRequest(String response, Integer lockVersion) {
    }

    /** {@code resolution}: REFUND, REPLACEMENT o REJECTED. */
    public record ResolveRequest(String resolution, BigDecimal amount, BigDecimal sellerCharge, String note, Long devolutionId,
                                 Integer lockVersion) {
    }

    public record CancelRequest(String reason, Integer lockVersion) {
    }

    public record DisputeSummaryDto(Long id, String number, Long orderId, String orderNumber, Long purchaseId, Long sellerId,
                                    String sellerName, String customerName, String reason, String status,
                                    BigDecimal claimedAmount, LocalDate respondBy, boolean overdue, String resolution,
                                    BigDecimal resolutionAmount, Instant createdAt) {
    }

    public record DisputeEventDto(String action, String fromStatus, String toStatus, String note, String actorName,
                                  Instant createdAt) {
    }

    /** {@code actions}: lo que el usuario puede hacer ahora (RESPOND, RESOLVE, CANCEL). */
    public record DisputeDetailDto(DisputeSummaryDto summary, String description, String sellerResponse, Instant respondedAt,
                                   BigDecimal sellerCharge, String resolutionNote, Long devolutionId, Instant resolvedAt,
                                   String resolvedBy, BigDecimal orderTotal, String orderState, int lockVersion,
                                   String createdByName, List<String> actions, List<DisputeEventDto> events) {
    }

    public record DisputeStatsDto(long open, long overdue, long awaitingDecision, long resolvedBuyer30, long resolvedSeller30,
                                  BigDecimal charged30) {
    }

    /** Reclamo de una orden (el abierto o el último), para la ficha de la orden. */
    public record OrderDisputeDto(Long id, String number, String status) {
    }
}
