package com.essenza.draco.modules.marketplace.infrastructure.outbound.payouts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.marketplace.application.port.PayoutDispatcher.DispatchException;
import com.essenza.draco.modules.marketplace.application.port.PayoutDispatcher.DispatchItem;
import com.essenza.draco.modules.marketplace.application.port.PayoutDispatcher.DispatchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Adaptador de Wompi Pagos a terceros con un transporte simulado (no sale a la red). */
class WompiPayoutDispatcherTest {

    record Call(String method, String url, Map<String, String> headers, String body) {
    }

    static final String SANDBOX = "https://api.sandbox.payouts.wompi.co/v2";

    final List<Call> calls = new ArrayList<>();
    WompiPayoutDispatcher.Reply next = new WompiPayoutDispatcher.Reply(201, "{\"data\":{\"payoutId\":\"po-1\",\"status\":\"PENDING\"}}");
    IOException failure;

    WompiPayoutDispatcher wompi(String base, boolean allowProduction, String sandboxStatus) {
        return new WompiPayoutDispatcher("key-1", "principal-1", "acc-1", base, null, allowProduction, sandboxStatus, null,
                (method, url, headers, body) -> {
                    calls.add(new Call(method, url, headers, body));
                    if (failure != null) {
                        throw failure;
                    }
                    return next;
                });
    }

    static DispatchRequest request() {
        return new DispatchRequest("LIQ-000007", "PAYOUT-7-LIQ-000007", List.of(
                new DispatchItem("LIQ-000007-1", 9_000_000L, "Moda Andina SAS", "pagos@moda.co", "NIT", "900123456", null, "bank-uuid",
                        "AHORROS", "12345678901"),
                new DispatchItem("LIQ-000007-2", 8_990_000L, "Ana Pérez", "ana@x.co", "CC", "1020304050", "3001234567", null, null, null)));
    }

    @Test
    void sendsPayoutWithHeadersAndCents() throws Exception {
        WompiPayoutDispatcher w = wompi(SANDBOX, false, "approved");
        assertThat(w.configured()).isTrue();
        assertThat(w.environment()).isEqualTo("sandbox");
        var sent = w.dispatch(request());
        assertThat(sent.payoutId()).isEqualTo("po-1");
        Call call = calls.get(0);
        assertThat(call.method()).isEqualTo("POST");
        assertThat(call.url()).isEqualTo(SANDBOX + "/payouts");
        assertThat(call.headers()).containsEntry("x-api-key", "key-1").containsEntry("user-principal-id", "principal-1")
                .containsEntry("idempotency-key", "PAYOUT-7-LIQ-000007");
        JsonNode body = new ObjectMapper().readTree(call.body());
        assertThat(body.path("reference").asText()).isEqualTo("LIQ-000007");
        assertThat(body.path("accountId").asText()).isEqualTo("acc-1");
        assertThat(body.path("paymentType").asText()).isEqualTo("PROVIDERS");
        assertThat(body.path("transactionStatus").asText()).isEqualTo("APPROVED");
        JsonNode account = body.path("transactions").get(0);
        assertThat(account.path("amount").asLong()).isEqualTo(9_000_000L);
        assertThat(account.path("bankId").asText()).isEqualTo("bank-uuid");
        assertThat(account.path("accountType").asText()).isEqualTo("AHORROS");
        assertThat(account.has("key")).isFalse();
        JsonNode key = body.path("transactions").get(1);
        assertThat(key.path("key").asText()).isEqualTo("3001234567");
        assertThat(key.has("bankId")).isFalse();
        assertThat(key.has("accountNumber")).isFalse();
    }

    @Test
    void productionNeedsExplicitPermission() {
        WompiPayoutDispatcher blocked = wompi("https://api.payouts.wompi.co/v2", false, "APPROVED");
        assertThat(blocked.environment()).isEqualTo("production");
        assertThat(blocked.configured()).isFalse();
        assertThatThrownBy(() -> blocked.dispatch(request())).isInstanceOf(DispatchException.class);
        assertThat(calls).isEmpty();
        WompiPayoutDispatcher allowed = wompi("https://api.payouts.wompi.co/v2", true, "APPROVED");
        assertThat(allowed.configured()).isTrue();
        allowed.dispatch(request());
        // En producción nunca se envía el estado simulado.
        assertThat(calls.get(0).body()).doesNotContain("transactionStatus");
        assertThat(new WompiPayoutDispatcher("", "p", "a", SANDBOX, null, false, null, null, (m, u, h, b) -> null).configured()).isFalse();
        assertThat(new WompiPayoutDispatcher("k", "p", "a", "http://api.sandbox.payouts.wompi.co/v2", null, false, null, null,
                (m, u, h, b) -> null).configured()).isFalse();
    }

    @Test
    void classifiesFailures() {
        WompiPayoutDispatcher w = wompi(SANDBOX, false, null);
        next = new WompiPayoutDispatcher.Reply(400, "{\"error\":{\"message\":\"bankId inválido\"}}");
        assertThatThrownBy(() -> w.dispatch(request())).isInstanceOfSatisfying(DispatchException.class, ex -> {
            assertThat(ex.uncertain()).isFalse();
            assertThat(ex.getMessage()).contains("400").contains("bankId inválido");
        });
        next = new WompiPayoutDispatcher.Reply(409, "{\"code\":\"EXC_032\"}");
        assertThatThrownBy(() -> w.dispatch(request())).isInstanceOfSatisfying(DispatchException.class, ex -> assertThat(ex.uncertain()).isTrue());
        next = new WompiPayoutDispatcher.Reply(502, "");
        assertThatThrownBy(() -> w.dispatch(request())).isInstanceOfSatisfying(DispatchException.class, ex -> assertThat(ex.uncertain()).isTrue());
        failure = new HttpTimeoutException("timeout");
        assertThatThrownBy(() -> w.dispatch(request())).isInstanceOfSatisfying(DispatchException.class, ex -> assertThat(ex.uncertain()).isTrue());
        // La llave nunca aparece en los mensajes.
        assertThat(calls).allMatch(c -> c.headers().get("x-api-key").equals("key-1"));
    }

    @Test
    void readsListsTolerantly() {
        WompiPayoutDispatcher w = wompi(SANDBOX, false, null);
        next = new WompiPayoutDispatcher.Reply(200, "{\"data\":{\"items\":[{\"id\":\"po-9\",\"reference\":\"LIQ-000007\"}]}}");
        assertThat(w.findByReference("LIQ-000007")).contains("po-9");
        assertThat(calls.get(0).url()).isEqualTo(SANDBOX + "/payouts?reference=LIQ-000007");
        next = new WompiPayoutDispatcher.Reply(200, "{\"data\":[]}");
        assertThat(w.findByReference("LIQ-000007")).isEmpty();
        next = new WompiPayoutDispatcher.Reply(200, "{\"data\":[{\"id\":\"tx-1\",\"reference\":\"LIQ-000007-1\",\"status\":\"APPROVED\"},"
                + "{\"id\":\"tx-2\",\"reference\":\"LIQ-000007-2\",\"status\":\"FAILED\",\"statusMessage\":\"Llave inexistente\"}]}");
        var txs = w.transactions("po-9");
        assertThat(txs).hasSize(2);
        assertThat(txs.get(1).detail()).isEqualTo("Llave inexistente");
        assertThat(calls.get(2).url()).isEqualTo(SANDBOX + "/payouts/po-9/transactions");
        next = new WompiPayoutDispatcher.Reply(200, "{\"data\":[{\"id\":\"b-1\",\"name\":\"BANCOLOMBIA\",\"code\":\"1007\"},{\"name\":\"sin id\"}]}");
        assertThat(w.banks()).singleElement().satisfies(b -> {
            assertThat(b.id()).isEqualTo("b-1");
            assertThat(b.code()).isEqualTo("1007");
        });
        next = new WompiPayoutDispatcher.Reply(500, "{}");
        assertThatThrownBy(() -> w.transactions("po-9")).isInstanceOf(DispatchException.class);
    }
}
