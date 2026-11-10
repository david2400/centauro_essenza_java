package com.essenza.draco.modules.billing.application.output.repository;

import java.util.List;

/** Origen de las facturas de servicios (p. ej. SELLER_FEES + vendedor:mes). */
public interface InvoiceSourceStore {

    void link(long invoiceId, String sourceType, String sourceKey);

    /** Facturas del origen, de la más reciente a la más antigua. */
    List<Long> invoicesOf(String sourceType, String sourceKey);
}
