package com.essenza.draco.modules.marketplace.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Estado de cuenta del vendedor con la plataforma (asientos inmutables, idempotentes). */
public interface SellerLedgerStore {

    /** @return false si la clave ya existía (el asiento no se repite) */
    boolean post(long companyId, NewEntry entry, String actorId, String actorName, Instant now);

    List<LedgerRow> entries(long companyId, long sellerId);

    /** Saldo (cargos − abonos) por vendedor de la empresa. */
    Map<Long, BigDecimal> balances(long companyId);

    /**
     * Lo que le queda abonado al vendedor por lo que la tienda cobró de la orden: ventas
     * (MARKETPLACE_SALE / CONSIGNMENT_SALE) menos cancelaciones, devoluciones y reembolsos.
     */
    BigDecimal netSaleCredits(long companyId, long orderId);

    /** Vendedores externos no cerrados de la empresa (para generar mensualidades). */
    List<Long> billableSellers(long companyId);

    record NewEntry(long sellerId, String type, BigDecimal amount, String currency, LocalDate entryDate, LocalDate dueOn,
                    BigDecimal interestMonthlyPct, String reference, Long commissionId, String feeCycle,
                    String idempotencyKey, String note) {
    }

    record LedgerRow(long id, long sellerId, String type, BigDecimal amount, String currency, LocalDate entryDate,
                     LocalDate dueOn, BigDecimal interestMonthlyPct, String reference, Long commissionId, String feeCycle,
                     String note, String createdBy, String createdByName, Instant createdAt) {
    }
}
