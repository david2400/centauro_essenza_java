package com.essenza.draco.modules.sales.domain.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.sales.domain.model.order.OrderRuleViolation;
import com.essenza.draco.modules.sales.domain.model.storefront.RateLimiter;
import com.essenza.draco.modules.sales.domain.model.storefront.StorefrontRules;
import com.essenza.draco.modules.sales.domain.model.storefront.StorefrontRules.Address;
import com.essenza.draco.modules.sales.domain.model.storefront.StorefrontRules.Buyer;
import com.essenza.draco.modules.sales.domain.model.storefront.StorefrontRules.CartLine;

class StorefrontRulesTest {

    @Test
    void cartMergesRepeatedLinesAndLimitsQuantities() {
        List<CartLine> cart = StorefrontRules.cart(List.of(new CartLine(1L, null, 2), new CartLine(1L, null, 3), new CartLine(1L, 9L, 1)));
        assertThat(cart).containsExactly(new CartLine(1L, null, 5), new CartLine(1L, 9L, 1));
        assertThatThrownBy(() -> StorefrontRules.cart(List.of())).isInstanceOf(OrderRuleViolation.class).hasMessageContaining("vacío");
        assertThatThrownBy(() -> StorefrontRules.cart(List.of(new CartLine(1L, null, 0)))).hasMessageContaining("mayores que cero");
        assertThatThrownBy(() -> StorefrontRules.cart(List.of(new CartLine(1L, null, 15), new CartLine(1L, null, 6))))
                .hasMessageContaining("20 unidades");
        assertThatThrownBy(() -> StorefrontRules.cart(List.of(new CartLine(null, null, 1)))).hasMessageContaining("sin referencia");
        List<CartLine> many = new ArrayList<>();
        for (long i = 1; i <= 31; i++) {
            many.add(new CartLine(i, null, 1));
        }
        assertThatThrownBy(() -> StorefrontRules.cart(many)).hasMessageContaining("30 productos");
    }

    @Test
    void buyerAndAddressAreValidatedAndNormalized() {
        Buyer ok = StorefrontRules.buyer(new Buyer("  Ana   Pérez ", "cc", "1020304050", "Ana@X.co ", "300 123 4567"));
        assertThat(ok).isEqualTo(new Buyer("Ana Pérez", "CC", "1020304050", "ana@x.co", "300 123 4567"));
        assertThatThrownBy(() -> StorefrontRules.buyer(new Buyer("A", "XX", "1;DROP", "no-mail", "12")))
                .hasMessage("Revisa: nombre, tipo de documento, número de documento, correo, celular.");
        Address address = StorefrontRules.address(new Address(null, null, "Calle 10 # 20-30", null, "Medellín", "Antioquia", " "),
                "Ana Pérez", "3001234567");
        assertThat(address.recipientName()).isEqualTo("Ana Pérez");
        assertThat(address.notes()).isNull();
        assertThatThrownBy(() -> StorefrontRules.address(new Address(null, null, "x", null, null, null, null), "A", "1"))
                .hasMessage("Revisa: dirección, ciudad.");
    }

    @Test
    void tokensAreLongRandomAndOnlyTheirHashIsStored() {
        String a = StorefrontRules.newToken();
        String b = StorefrontRules.newToken();
        assertThat(a).hasSize(43).isNotEqualTo(b);
        assertThat(StorefrontRules.wellFormed(a)).isTrue();
        assertThat(StorefrontRules.wellFormed("abc")).isFalse();
        assertThat(StorefrontRules.wellFormed(a + "'")).isFalse();
        assertThat(StorefrontRules.hash(a)).hasSize(64).isEqualTo(StorefrontRules.hash(a)).isNotEqualTo(StorefrontRules.hash(b));
    }

    @Test
    void searchPatternEscapesWildcardsAndStockIsNotDisclosed() {
        assertThat(StorefrontRules.likePattern("50%_off")).isEqualTo("%50\\%\\_off%");
        assertThat(StorefrontRules.likePattern("  ")).isNull();
        assertThat(StorefrontRules.shownAvailable(250)).isEqualTo(10);
        assertThat(StorefrontRules.shownAvailable(3)).isEqualTo(3);
        assertThat(StorefrontRules.shownAvailable(-2)).isZero();
    }

    @Test
    void rateLimiterSlidesItsWindow() {
        RateLimiter limiter = new RateLimiter(2, Duration.ofMinutes(10), 100);
        Instant t = Instant.parse("2026-11-09T10:00:00Z");
        assertThat(limiter.tryAcquire("ip", t)).isTrue();
        assertThat(limiter.tryAcquire("ip", t.plusSeconds(1))).isTrue();
        assertThat(limiter.tryAcquire("ip", t.plusSeconds(2))).isFalse();
        assertThat(limiter.tryAcquire("other", t.plusSeconds(2))).isTrue();
        assertThat(limiter.tryAcquire("ip", t.plusSeconds(601))).isTrue();
    }
}
