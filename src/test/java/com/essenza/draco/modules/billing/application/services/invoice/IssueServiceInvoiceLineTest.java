package com.essenza.draco.modules.billing.application.services.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.billing.domain.model.InvoiceLine;
import com.essenza.draco.shared.common.billing.ServiceInvoicing;
import com.essenza.draco.shared.common.billing.TaxRates;

class IssueServiceInvoiceLineTest {

    static final TaxRates RATES = new TaxRates() {
        @Override
        public TaxRateView effective(Long id) {
            return id == null ? new TaxRateView(1L, "IVA_19", "IVA 19 %", "IVA", new BigDecimal("19.000"))
                    : new TaxRateView(9L, "EXCLUIDO", "Excluido", "EXCLUDED", BigDecimal.ZERO);
        }

        @Override
        public boolean isAssignable(Long id) {
            return true;
        }
    };

    @Test
    void taxIsIncludedInTheConceptValue() {
        var service = new IssueServiceInvoiceService(null, null, null, null, RATES);
        InvoiceLine line = service.line(1, new ServiceInvoicing.Line("COMMISSIONS", "Comisiones 2026-10", new BigDecimal("119000"), null));
        assertThat(line.taxBase()).isEqualByComparingTo("100000");
        assertThat(line.taxAmount()).isEqualByComparingTo("19000");
        assertThat(line.total()).isEqualByComparingTo("119000");
        assertThat(line.taxCode()).isEqualTo("IVA_19");
        InvoiceLine odd = service.line(2, new ServiceInvoicing.Line("STORAGE", "Almacenamiento", new BigDecimal("10000"), null));
        assertThat(odd.taxBase().add(odd.taxAmount())).isEqualByComparingTo("10000");
        assertThat(odd.taxBase()).isEqualByComparingTo("8403.36");
        InvoiceLine excluded = service.line(3, new ServiceInvoicing.Line("X", "Excluido", new BigDecimal("5000"), 9L));
        assertThat(excluded.taxAmount()).isZero();
        assertThat(excluded.taxBase()).isEqualByComparingTo("5000");
    }
}
