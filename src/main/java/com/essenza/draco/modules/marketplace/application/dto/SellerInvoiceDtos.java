package com.essenza.draco.modules.marketplace.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Factura mensual de la plataforma al vendedor (JSON en snake_case). */
public final class SellerInvoiceDtos {

    private SellerInvoiceDtos() {
    }

    /** Concepto facturable; {@code code}: COMMISSIONS, MONTHLY_FEE, STORAGE o DISPUTES. */
    public record ConceptDto(String code, String description, BigDecimal total) {
    }

    /** {@code status}: PENDING, ISSUED, REJECTED o VOIDED (estado actual en facturación). */
    public record InvoiceRefDto(Long invoiceId, String number, String status, BigDecimal total, String detail, Instant createdAt) {
    }

    /**
     * Vendedor en el periodo. {@code collected}: lo que la tienda recaudó por su cuenta (no se le factura: lo factura el
     * vendedor al comprador). {@code missing}: datos que faltan para facturarle.
     */
    public record SellerPeriodDto(Long sellerId, String sellerName, String doc, List<ConceptDto> concepts, BigDecimal total,
                                  BigDecimal collected, InvoiceRefDto invoice, List<String> missing) {
    }

    public record PeriodPreviewDto(String period, boolean closed, BigDecimal total, int toInvoice, int invoiced,
                                   List<SellerPeriodDto> sellers) {
    }

    /** {@code sellerIds} vacío = todos los pendientes del periodo. */
    public record IssueRequest(String period, List<Long> sellerIds) {
    }

    public record IssueResultDto(String period, int issued, int alreadyInvoiced, int skipped, List<String> errors,
                                 PeriodPreviewDto preview) {
    }
}
