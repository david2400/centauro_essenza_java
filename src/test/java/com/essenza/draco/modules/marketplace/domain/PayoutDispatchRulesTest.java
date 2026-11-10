package com.essenza.draco.modules.marketplace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.marketplace.domain.payout.PayoutDispatchRules;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutDispatchRules.Outcome;

class PayoutDispatchRulesTest {

    @Test
    void amountsGoInWholeCents() {
        assertThat(PayoutDispatchRules.cents(new BigDecimal("90000"))).isEqualTo(9_000_000L);
        assertThat(PayoutDispatchRules.cents(new BigDecimal("1250000.50"))).isEqualTo(125_000_050L);
        assertThatThrownBy(() -> PayoutDispatchRules.cents(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PayoutDispatchRules.cents(new BigDecimal("1.005"))).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void referencesAndKeysAreProviderSafe() {
        assertThat(PayoutDispatchRules.reference("LIQ-000123", 45)).isEqualTo("LIQ-000123-45");
        assertThat(PayoutDispatchRules.reference("LIQ 1/2", 7)).isEqualTo("LIQ-1-2-7");
        assertThat(PayoutDispatchRules.reference("X".repeat(60), 9)).hasSize(40).endsWith("-9");
        assertThat(PayoutDispatchRules.idempotencyKey("LIQ-000123", 123)).isEqualTo("PAYOUT-123-LIQ-000123");
        assertThat(PayoutDispatchRules.idempotencyKey("Z".repeat(80), 1)).hasSize(64).matches("[A-Za-z0-9-]+");
    }

    @Test
    void accountTypesAndOutcomes() {
        assertThat(PayoutDispatchRules.accountType("CHECKING")).isEqualTo("CORRIENTE");
        assertThat(PayoutDispatchRules.accountType("SAVINGS")).isEqualTo("AHORROS");
        assertThat(PayoutDispatchRules.accountType("DIGITAL_WALLET")).isEqualTo("AHORROS");
        assertThatThrownBy(() -> PayoutDispatchRules.accountType("BRE_B_KEY")).isInstanceOf(IllegalArgumentException.class);
        assertThat(PayoutDispatchRules.outcome("APPROVED")).isEqualTo(Outcome.PAID);
        assertThat(PayoutDispatchRules.outcome(" failed ")).isEqualTo(Outcome.FAILED);
        assertThat(PayoutDispatchRules.outcome("REJECTED")).isEqualTo(Outcome.FAILED);
        assertThat(PayoutDispatchRules.outcome("CANCELLED")).isEqualTo(Outcome.FAILED);
        assertThat(PayoutDispatchRules.outcome("PROCESSING")).isEqualTo(Outcome.PENDING);
        assertThat(PayoutDispatchRules.outcome(null)).isEqualTo(Outcome.PENDING);
    }

    @Test
    void problemsListWhatIsMissing() {
        assertThat(PayoutDispatchRules.problems("SAVINGS", "12345678901", "uuid-1", "a@b.co", "Moda", "NIT")).isEmpty();
        assertThat(PayoutDispatchRules.problems("SAVINGS", "123", null, " ", "Moda", "RUT"))
                .containsExactly("correo del vendedor", "id de la entidad en el proveedor (catálogo de bancos)",
                        "tipo de documento del titular admitido (CC, CE, NIT, PP o TI)");
        // Llave Bre-B: no necesita entidad ni tipo de documento.
        assertThat(PayoutDispatchRules.problems("BRE_B_KEY", "@moda", null, "a@b.co", "Moda", null)).isEmpty();
    }
}
