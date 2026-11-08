package com.essenza.draco.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RouteRulesTest {

    @Test
    void readAndWriteByArea() {
        assertThat(RouteRules.capabilityFor("GET", "/sales/orders/5")).contains("VENTAS.VER");
        assertThat(RouteRules.capabilityFor("POST", "/sales/orders/5/status")).contains("VENTAS.GESTIONAR");
        assertThat(RouteRules.capabilityFor("DELETE", "/sales/product_orders/9")).contains("VENTAS.GESTIONAR");
        assertThat(RouteRules.capabilityFor("GET", "/sales/cash_sessions")).contains("CAJA.VER");
        assertThat(RouteRules.capabilityFor("POST", "/sales/pos/checkout")).contains("CAJA.GESTIONAR");
        assertThat(RouteRules.capabilityFor("PUT", "/sales/stores/2")).contains("CAJA.GESTIONAR");
        assertThat(RouteRules.capabilityFor("GET", "/devolution/order_devolutions/")).contains("POSTVENTA.VER");
        assertThat(RouteRules.capabilityFor("POST", "/billing/invoices")).contains("FACTURACION.GESTIONAR");
        assertThat(RouteRules.capabilityFor("POST", "/graphql")).contains("VENTAS.GESTIONAR");
    }

    @Test
    void gatewayCallbacksAndOtherModulesAreNotCoveredHere() {
        assertThat(RouteRules.capabilityFor("POST", "/sales/payments/webhooks/wompi")).isEmpty();
        assertThat(RouteRules.capabilityFor("GET", "/sales/payments/sandbox/ABC")).isEmpty();
        assertThat(RouteRules.capabilityFor("OPTIONS", "/sales/orders")).isEmpty();
        assertThat(RouteRules.capabilityFor("GET", "/catalog/products")).isEmpty();
        assertThat(RouteRules.capabilityFor("GET", "/salesman")).isEmpty();
        // El prefijo debe coincidir por segmento: /sales/paymentsX no es /sales/payments/webhooks.
        assertThat(RouteRules.capabilityFor("POST", "/sales/payments/webhooksX")).contains("VENTAS.GESTIONAR");
    }
}
