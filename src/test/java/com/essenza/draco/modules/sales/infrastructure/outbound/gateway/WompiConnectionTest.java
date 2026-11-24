package com.essenza.draco.modules.sales.infrastructure.outbound.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.sales.application.output.gateway.PaymentGateway.ConnectionTest;
import com.essenza.draco.modules.sales.application.output.gateway.PaymentGateway.GatewayException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Prueba de conexión con Wompi: solo consultas GET, con respuestas simuladas (no sale a internet). */
class WompiConnectionTest {

    static final ObjectMapper JSON = new ObjectMapper();

    /** HTTP falso: responde según la URL y guarda lo pedido. */
    static class FakeHttp extends GatewayHttp {
        final List<String> urls = new ArrayList<>();
        final List<Map<String, String>> headers = new ArrayList<>();
        final Function<String, Response> answer;

        FakeHttp(Function<String, Response> answer) {
            super(null);
            this.answer = answer;
        }

        @Override
        Response get(String url, Map<String, String> h) {
            urls.add(url);
            headers.add(h);
            return answer.apply(url);
        }

        @Override
        Response post(String url, Object body, Map<String, String> h) {
            throw new AssertionError("La prueba de conexión no debe hacer POST: " + url);
        }
    }

    static GatewayHttp.Response json(int status, String body) {
        try {
            return new GatewayHttp.Response(status, JSON.readTree(body));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static PaymentsSettings settings(String pub, String prv, String integrity, String events) {
        return new PaymentsSettings("https://api.tienda.co/", "https://tienda.co/ok", true, "sbx-secret", pub, prv, integrity,
                events, "", "https://checkout.wompi.co/p/", "", "", "https://api.mercadopago.com");
    }

    @Test
    void llavesValidasDevuelvenElNombreDelComercio() {
        FakeHttp http = new FakeHttp(url -> url.contains("/merchants/") ? json(200, "{\"data\":{\"name\":\"Essenza pruebas\"}}")
                : json(200, "{\"data\":[]}"));
        ConnectionTest test = new WompiGateway(settings("pub_test_abc", "prv_test_x", "test_integrity_s", "test_events_s"), http)
                .testConnection();
        assertThat(test.supported()).isTrue();
        assertThat(test.ok()).isTrue();
        assertThat(test.merchant()).isEqualTo("Essenza pruebas");
        assertThat(test.message()).contains("llave privada válida");
        assertThat(http.urls.get(0)).isEqualTo(WompiGateway.SANDBOX_API + "/merchants/pub_test_abc");
        assertThat(http.headers.get(1)).containsEntry("Authorization", "Bearer prv_test_x");
        assertThat(test.message()).doesNotContain("prv_test_x", "test_integrity_s", "test_events_s");
    }

    @Test
    void llavePublicaDesconocida() {
        FakeHttp http = new FakeHttp(url -> json(404, "{}"));
        ConnectionTest test = new WompiGateway(settings("pub_test_nope", "prv_test_x", "i", "e"), http).testConnection();
        assertThat(test.ok()).isFalse();
        assertThat(test.message()).contains("no reconoce la llave pública");
        assertThat(http.urls).hasSize(1);
    }

    @Test
    void llavePrivadaRechazadaYSecretosFaltantes() {
        FakeHttp http = new FakeHttp(url -> url.contains("/merchants/") ? json(200, "{\"data\":{\"name\":\"X\"}}") : json(401, "{}"));
        ConnectionTest test = new WompiGateway(settings("pub_test_abc", "prv_test_bad", "", ""), http).testConnection();
        assertThat(test.ok()).isFalse();
        assertThat(test.message()).contains("rechazó la llave privada (401)", "WOMPI_INTEGRITY_SECRET");
    }

    @Test
    void sinLlaveONoAlcanzableNoRevientan() {
        FakeHttp none = new FakeHttp(url -> {
            throw new AssertionError("no debe llamar");
        });
        assertThat(new WompiGateway(settings("", "", "", ""), none).testConnection().message()).contains("WOMPI_PUBLIC_KEY");
        FakeHttp down = new FakeHttp(url -> {
            throw new GatewayException("No se pudo comunicar con la pasarela: timeout");
        });
        ConnectionTest test = new WompiGateway(settings("pub_test_abc", "", "i", "e"), down).testConnection();
        assertThat(test.ok()).isFalse();
        assertThat(test.message()).contains("No se pudo conectar con Wompi");
    }

    @Test
    void llaveDeProduccionBloqueadaNoSeConsulta() {
        FakeHttp http = new FakeHttp(url -> {
            throw new AssertionError("no debe llamar");
        });
        ConnectionTest test = new WompiGateway(settings("pub_prod_abc", "prv_prod_x", "prod_integrity_x", "prod_events_x"), http)
                .testConnection();
        assertThat(test.ok()).isFalse();
        assertThat(test.message()).contains("WOMPI_ALLOW_PRODUCTION=false");
    }
}
