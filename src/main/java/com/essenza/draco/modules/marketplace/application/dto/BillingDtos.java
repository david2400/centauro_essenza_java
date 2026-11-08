package com.essenza.draco.modules.marketplace.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Comisiones y estado de cuenta del vendedor (contrato JSON en snake_case). */
public final class BillingDtos {

    private BillingDtos() {
    }

    public record CommissionDto(Long id, Long sellerId, String sellerName, Long orderId, Long purchaseId, String orderNumber,
                                String planCode, String currency, BigDecimal base, BigDecimal percent,
                                BigDecimal percentPart, BigDecimal fixedPart, BigDecimal amount, BigDecimal reversedAmount,
                                BigDecimal net, String accrualEvent, LocalDate paidOn, LocalDate deliveredOn,
                                LocalDate accrueOn, String status, String reviewReason, Instant createdAt) {
    }

    public record StatusTotalDto(String status, long count, BigDecimal amount) {
    }

    public record BillingSummaryDto(List<StatusTotalDto> commissions, BigDecimal receivable, long sellersWithBalance) {
    }

    public record LedgerEntryDto(Long id, String type, BigDecimal amount, String currency, LocalDate entryDate,
                                 LocalDate dueOn, BigDecimal open, long daysOverdue, String reference, Long commissionId,
                                 String feeCycle, String note, String createdByName, Instant createdAt) {
    }

    public record SellerAccountDto(Long sellerId, String sellerName, String currency, LocalDate asOf, BigDecimal charges,
                                   BigDecimal credits, BigDecimal balance, BigDecimal overdue, BigDecimal lateInterestEstimate,
                                   BigDecimal unappliedCredit, BigDecimal pendingCommissions, List<LedgerEntryDto> entries) {
    }

    /**
     * @param key clave del cliente para no registrar dos veces el mismo pago (doble clic, reintento)
     */
    public record SellerPaymentRequest(BigDecimal amount, LocalDate date, String reference, String note, String key) {
    }

    /** Ajuste manual: positivo = el vendedor debe más; negativo = abono. */
    public record AdjustmentRequest(BigDecimal amount, LocalDate date, String reason, String key) {
    }

    /** {@code storageCreated}/{@code storageTotal}: cargos de almacenamiento de la consignación (ciclos cerrados). */
    public record FeeRunDto(String cycle, int created, int alreadyCharged, int withoutFee, BigDecimal total,
                            List<String> notChargeableYet, int storageCreated, BigDecimal storageTotal) {
    }

    public record AccrualRunDto(int accrued) {
    }
}
