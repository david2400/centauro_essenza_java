package com.essenza.draco.modules.marketplace.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Factura mensual de la plataforma a cada vendedor: conceptos del periodo y facturas emitidas (JDBC). */
public interface SellerInvoiceStore {

    /** Vendedores externos de la empresa con sus datos tributarios. */
    Map<Long, SellerParty> sellers(long companyId);

    /**
     * Totales del periodo por vendedor: comisiones (COMMISSION − COMMISSION_REVERSAL) y cargos por reclamo por fecha
     * del asiento; mensualidad y almacenamiento por ciclo ({@code fee_cycle}); lo recaudado por su cuenta (informativo).
     */
    Map<Long, PeriodTotals> totals(long companyId, LocalDate from, LocalDate to, String cycle);

    /** Facturas emitidas del periodo (más recientes primero). */
    Map<Long, List<IssuedRef>> issued(long companyId, String period);

    void link(long companyId, long sellerId, String period, long invoiceId, String number, BigDecimal total, String actorId,
              String actorName, Instant now);

    record SellerParty(long id, String name, String docType, String docNumber, String email, String phone, String address,
                       String city) {
    }

    record PeriodTotals(BigDecimal commissions, int commissionCount, BigDecimal monthlyFee, BigDecimal storage, BigDecimal disputes,
                        BigDecimal collected) {
    }

    record IssuedRef(long invoiceId, String number, BigDecimal total, Instant createdAt) {
    }
}
