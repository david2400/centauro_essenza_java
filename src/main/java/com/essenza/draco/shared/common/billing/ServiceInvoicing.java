package com.essenza.draco.shared.common.billing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Factura electrónica de servicios que no salen de una orden (la implementa facturación). La usa el
 * marketplace para facturarle al vendedor sus comisiones y cargos del mes. Cada factura queda ligada a
 * un origen ({@code sourceType} + {@code sourceKey}): mientras tenga una factura vigente (no anulada
 * con nota crédito) no se emite otra para el mismo origen.
 */
public interface ServiceInvoicing {

    /** Línea con el impuesto incluido en {@code total}; {@code taxRateId} nulo = la tarifa por defecto. */
    record Line(String code, String description, BigDecimal total, Long taxRateId) {
    }

    record Buyer(String docType, String docNumber, String name, String email, String phone, String address, String city) {
    }

    record Request(String sourceType, String sourceKey, Buyer buyer, List<Line> lines, String note) {
    }

    /** {@code status}: PENDING, ISSUED, REJECTED o VOIDED. */
    record Result(Long invoiceId, String number, String status, BigDecimal net, BigDecimal tax, BigDecimal total, String detail,
                  boolean created) {
    }

    /** Factura vigente del origen, si la hay. */
    Optional<Result> active(String sourceType, String sourceKey);

    /** Emite (o devuelve la vigente, con {@code created = false}). */
    Result issue(Request request);

    /** Estado actual de facturas ya emitidas (para listarlas). */
    List<Result> status(List<Long> invoiceIds);
}
