package com.essenza.draco.modules.sales.domain.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.sales.domain.model.storefront.ShippingRates;

class ShippingRatesTest {

    @Test
    void keysIgnoreCaseAccentsAndSpaces() {
        assertThat(ShippingRates.key("  Bogotá,  D.C. ")).isEqualTo("bogota d c");
        assertThat(ShippingRates.key("MEDELLÍN")).isEqualTo(ShippingRates.key("medellin"));
        assertThat(ShippingRates.key(null)).isEmpty();
    }

    @Test
    void ratesAreValidated() {
        assertThatThrownBy(() -> ShippingRates.check(null, null, new BigDecimal("-1"), null, null, null, true))
                .hasMessageContaining("0 o mayor");
        assertThatThrownBy(() -> ShippingRates.check(null, null, new BigDecimal("1.234"), null, null, null, true))
                .hasMessageContaining("decimales");
        assertThatThrownBy(() -> ShippingRates.check(null, null, BigDecimal.TEN, BigDecimal.ZERO, null, null, true))
                .hasMessageContaining("gratis");
        assertThatThrownBy(() -> ShippingRates.check(null, null, BigDecimal.TEN, null, 5, 2, true)).hasMessageContaining("máximos");
        var rate = ShippingRates.check(" Antioquia ", "  ", new BigDecimal("9000"), null, 1, 3, true);
        assertThat(rate.state()).isEqualTo("Antioquia");
        assertThat(rate.city()).isNull();
        assertThat(rate.cityKey()).isEmpty();
    }
}
