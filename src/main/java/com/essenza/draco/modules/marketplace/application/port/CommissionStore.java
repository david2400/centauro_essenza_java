package com.essenza.draco.modules.marketplace.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.essenza.draco.modules.marketplace.domain.billing.CommissionRules.Status;

/** Comisiones por venta y sus reversos. Las lecturas de pantalla filtran por empresa. */
public interface CommissionStore {

    /** Empresa, tipo y nombre del vendedor (ventas solo conoce el id). */
    Optional<SellerRef> seller(long sellerId);

    Optional<CommissionRow> byOrder(long orderId, boolean forUpdate);

    Optional<CommissionRow> find(long companyId, long commissionId, boolean forUpdate);

    /** Bloquea la comisión por id (trabajo de devengo, sin empresa de usuario). */
    Optional<CommissionRow> lockById(long commissionId);

    long insert(CommissionRow commission, Instant now);

    /** Guarda el cálculo completo (recálculo de una comisión en revisión). */
    void recalculated(CommissionRow commission, Instant now);

    /** Guarda estado, fechas y lo reversado. */
    void update(CommissionRow commission, Instant now);

    /** Pendientes exigibles a la fecha (ids, para bloquearlas una a una). */
    List<Long> dueIds(LocalDate today, int limit);

    Page<CommissionRow> search(long companyId, CommissionFilter filter, Pageable pageable);

    /** Cantidad e importe neto (amount − reversado) por estado. */
    Map<String, Totals> totalsByStatus(long companyId, Long sellerId);

    /** @return false si el origen ya se aplicó (idempotencia) */
    boolean insertAdjustment(long companyId, long commissionId, String kind, String sourceKey, BigDecimal refundedBase,
                             BigDecimal amount, boolean posted, String note, Instant now);

    record SellerRef(long id, long companyId, String name, String kind, String status) {
    }

    record CommissionFilter(String q, Long sellerId, String status) {
    }

    record Totals(long count, BigDecimal amount) {
    }

    record CommissionRow(
            Long id,
            long companyId,
            long sellerId,
            String sellerName,
            long orderId,
            Long purchaseId,
            String orderNumber,
            Long assignmentId,
            String planCode,
            String currency,
            BigDecimal itemsSubtotal,
            BigDecimal discount,
            BigDecimal shipping,
            BigDecimal taxes,
            BigDecimal base,
            BigDecimal percent,
            BigDecimal percentPart,
            BigDecimal fixedPart,
            BigDecimal amount,
            BigDecimal reversedAmount,
            String accrualEvent,
            int returnWindowDays,
            LocalDate paidOn,
            LocalDate deliveredOn,
            LocalDate accrueOn,
            Status status,
            String reviewReason,
            Instant accruedAt,
            Instant voidedAt,
            Instant createdAt) {

        public BigDecimal net() {
            return amount.subtract(reversedAmount);
        }

        public CommissionRow with(LocalDate deliveredOn, LocalDate accrueOn, Status status, BigDecimal reversedAmount,
                                  Instant accruedAt, Instant voidedAt) {
            return new CommissionRow(id, companyId, sellerId, sellerName, orderId, purchaseId, orderNumber, assignmentId,
                    planCode, currency, itemsSubtotal, discount, shipping, taxes, base, percent, percentPart, fixedPart, amount,
                    reversedAmount, accrualEvent, returnWindowDays, paidOn, deliveredOn, accrueOn, status, reviewReason,
                    accruedAt, voidedAt, createdAt);
        }
    }
}
