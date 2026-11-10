package com.essenza.draco.modules.sales.infrastructure.outbound.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.sales.application.output.gateway.PaymentGateway;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentStatus;

class GatewaySignaturesTest {

    static PaymentsSettings settings(String wompiPub, String integrity, String events, String mpToken, String mpSecret) {
        return new PaymentsSettings("https://api.tienda.co/", "https://tienda.co/ok", true, "sbx-secret", wompiPub,
                "prv_test_x", integrity, events, "", "https://checkout.wompi.co/p/", mpToken, mpSecret,
                "https://api.mercadopago.com");
    }

    @Test
    void integridadDeWompiCoincideConElEjemploDeLaDocumentacion() {
        WompiGateway wompi = new WompiGateway(settings("pub_prod_x", "prod_integrity_Z5mMke9x0k8gpErbDqwrJXMqsI6SFli6", "e", "", ""),
                new GatewayHttp());
        assertThat(wompi.integrity("sk8-438k4-xmxm392-sn2m", 2490000, "COP", null))
                .isEqualTo("37c8407747e595535433ef8f6a811d853cd943046624a0ec04662b17bbf33bf5");
        assertThat(wompi.environment()).isEqualTo("production");
        assertThat(wompi.apiUrl()).isEqualTo(WompiGateway.PRODUCTION_API);
    }

    @Test
    void lasLlavesDeProduccionDeWompiNoSeUsanSinPermisoExplicito() {
        PaymentsSettings prod = settings("pub_prod_x", "prod_integrity_x", "prod_events_x", "", "");
        WompiGateway wompi = new WompiGateway(prod, new GatewayHttp());
        assertThat(wompi.configured()).isFalse();
        prod.setWompiAllowProduction(true);
        assertThat(wompi.configured()).isTrue();
        assertThat(new WompiGateway(settings("pub_test_x", "i", "e", "", ""), new GatewayHttp()).configured()).isTrue();
    }

    @Test
    void elCheckoutDeWompiLlevaMontoEnCentavosYFirma() {
        WompiGateway wompi = new WompiGateway(settings("pub_test_abc", "test_integrity_s", "test_events_s", "", ""), new GatewayHttp());
        var session = wompi.createCheckout(new PaymentGateway.CheckoutRequest(7L, "PAY-7-AAAA2222", new BigDecimal("95000"), "COP",
                "Orden #7", "a@b.co", "https://tienda.co/ok", null));
        String url = URLDecoder.decode(session.url(), StandardCharsets.UTF_8);
        assertThat(url).startsWith("https://checkout.wompi.co/p/?public-key=pub_test_abc")
                .contains("amount-in-cents=9500000").contains("reference=PAY-7-AAAA2222")
                .contains("signature:integrity=" + wompi.integrity("PAY-7-AAAA2222", 9500000, "COP", null))
                .contains("redirect-url=https://tienda.co/ok");
        assertThat(wompi.apiUrl()).isEqualTo(WompiGateway.SANDBOX_API);
    }

    @Test
    void eventoDeWompiFirmadoSeAceptaYAlteradoSeRechaza() {
        WompiGateway wompi = new WompiGateway(settings("pub_test_abc", "i", "test_events_s", "", ""), new GatewayHttp());
        String checksum = Signatures.sha256Hex("1234-1610641025-49201" + "APPROVED" + "4490000" + "1530291411" + "test_events_s");
        String body = """
                {"event":"transaction.updated","data":{"transaction":{"id":"1234-1610641025-49201","amount_in_cents":4490000,
                "reference":"PAY-1-XYZW2345","customer_email":"a@b.co","currency":"COP","payment_method_type":"NEQUI",
                "status":"APPROVED"}},"environment":"test","signature":{"properties":["transaction.id","transaction.status",
                "transaction.amount_in_cents"],"checksum":"%s"},"timestamp":1530291411,"sent_at":"2018-07-20T16:45:05.000Z"}
                """.formatted(checksum.toUpperCase());
        var event = wompi.parseWebhook(Map.of(), Map.of(), body);
        assertThat(event.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(event.paymentReference()).isEqualTo("PAY-1-XYZW2345");
        assertThat(event.amount()).isEqualByComparingTo("44900");
        assertThat(event.providerReference()).isEqualTo("1234-1610641025-49201");

        assertThatThrownBy(() -> wompi.parseWebhook(Map.of(), Map.of(), body.replace("4490000,", "100,")))
                .isInstanceOf(PaymentGateway.InvalidWebhookException.class);
    }

    @Test
    void firmaDeMercadoPago() {
        MercadoPagoGateway mp = new MercadoPagoGateway(settings("", "", "", "TEST-123", "mp-secret"), new GatewayHttp());
        String manifest = "id:123456;request-id:req-1;ts:1704908010;";
        String v1 = Signatures.hmacSha256Hex("mp-secret", manifest);
        assertThat(mp.validSignature("ts=1704908010,v1=" + v1, "req-1", "123456")).isTrue();
        assertThat(mp.validSignature("ts=1704908011,v1=" + v1, "req-1", "123456")).isFalse();

        var event = mp.parseWebhook(Map.of("x-signature", "ts=1704908010,v1=" + v1, "x-request-id", "req-1"),
                Map.of("data.id", "123456", "type", "payment"), "{\"type\":\"payment\",\"data\":{\"id\":\"123456\"}}");
        assertThat(event.needsFetch()).isTrue();
        assertThat(event.providerReference()).isEqualTo("123456");
        assertThat(mp.environment()).isEqualTo("test");
        assertThatThrownBy(() -> mp.parseWebhook(Map.of("x-signature", "ts=1,v1=00", "x-request-id", "req-1"),
                Map.of("data.id", "123456", "type", "payment"), "{}")).isInstanceOf(PaymentGateway.InvalidWebhookException.class);
    }

    @Test
    void estadosDeMercadoPago() throws Exception {
        var json = new GatewayHttp().json();
        var status = MercadoPagoGateway.toStatus(json.readTree(
                "{\"id\":99,\"status\":\"approved\",\"external_reference\":\"PAY-3-ABCD2345\",\"transaction_amount\":15000.5}"));
        assertThat(status.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(status.paymentReference()).isEqualTo("PAY-3-ABCD2345");
        assertThat(status.providerReference()).isEqualTo("99");
        assertThat(MercadoPagoGateway.toStatus(json.readTree("{\"status\":\"in_process\"}")).status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(MercadoPagoGateway.toStatus(json.readTree("{\"status\":\"rejected\"}")).status()).isEqualTo(PaymentStatus.DECLINED);
    }

    @Test
    void simuladorFirmaYVerifica() {
        SandboxGateway sandbox = new SandboxGateway(settings("", "", "", "", ""), new GatewayHttp());
        var session = sandbox.createCheckout(new PaymentGateway.CheckoutRequest(5L, "PAY-5-QWER2345", new BigDecimal("1000"), "COP",
                "Orden #5", null, "https://tienda.co/ok", Instant.now()));
        assertThat(session.url()).startsWith("https://api.tienda.co/api/shop/sales/payments/sandbox/PAY-5-QWER2345?amount=1000");
        String body = "{\"reference\":\"PAY-5-QWER2345\",\"status\":\"APPROVED\",\"transaction_id\":\"SBX-1\",\"amount\":\"1000\"}";
        var event = sandbox.parseWebhook(Map.of("X-Sandbox-Signature", sandbox.sign(body)), Map.of(), body);
        assertThat(event.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThatThrownBy(() -> sandbox.parseWebhook(Map.of("x-sandbox-signature", "bad"), Map.of(), body))
                .isInstanceOf(PaymentGateway.InvalidWebhookException.class);
    }
}
