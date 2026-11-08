package com.essenza.draco.modules.sales.domain.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.sales.domain.model.customer.CustomerProfile;
import com.essenza.draco.modules.sales.domain.model.order.CustomerKind;
import com.essenza.draco.modules.sales.domain.model.order.OrderRuleViolation;

class CustomerProfileTest {

    static final Instant NOW = Instant.parse("2026-11-07T15:00:00Z");
    static final Instant BEFORE = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void normalizesDocumentAndContact() {
        CustomerProfile p = CustomerProfile.of(null, "nit", "900.123 456-7", "Moda SAS", "VENTAS@Moda.CO", "300 123 4567",
                null, null, null, true, null, false, "  cliente mayorista ", NOW);
        assertThat(p.info().docType()).isEqualTo("NIT");
        assertThat(p.info().docNumber()).isEqualTo("900123456-7");
        assertThat(p.info().kind()).isEqualTo(CustomerKind.COMPANY);
        assertThat(p.info().email()).isEqualTo("ventas@moda.co");
        assertThat(p.notes()).isEqualTo("cliente mayorista");
        assertThat(p.docKey()).isEqualTo("NIT:900123456-7");
    }

    @Test
    void consentDateIsKeptWhileConsentStays() {
        assertThat(CustomerProfile.of(null, "CC", "1020", "Ana", null, "3001234567", null, null, null, true, null, false, null, NOW)
                .consentAt()).isEqualTo(NOW);
        assertThat(CustomerProfile.of(null, "CC", "1020", "Ana", null, "3001234567", null, null, null, true, BEFORE, true, null, NOW)
                .consentAt()).isEqualTo(BEFORE);
        CustomerProfile revoked = CustomerProfile.of(null, "CC", "1020", "Ana", null, null, null, null, null, false, BEFORE, true,
                null, NOW);
        assertThat(revoked.marketingConsent()).isFalse();
        assertThat(revoked.consentAt()).isNull();
    }

    @Test
    void documentAndNameAreRequired() {
        assertThatThrownBy(() -> CustomerProfile.of(null, "CC", null, "Ana", null, null, null, null, null, false, null, false,
                null, NOW)).isInstanceOf(OrderRuleViolation.class).hasMessageContaining("documento");
        assertThatThrownBy(() -> CustomerProfile.of(null, "CC", "1020", " ", null, null, null, null, null, false, null, false,
                null, NOW)).hasMessageContaining("nombre");
        assertThatThrownBy(() -> CustomerProfile.of(null, "XX", "1020", "Ana", null, null, null, null, null, false, null, false,
                null, NOW)).hasMessageContaining("Tipo de documento");
        assertThatThrownBy(() -> CustomerProfile.of(null, "CC", "1020", "Ana", "no-es-correo", null, null, null, null, false,
                null, false, null, NOW)).hasMessageContaining("correo");
    }
}
