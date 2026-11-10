package com.essenza.draco.modules.marketplace.infrastructure.outbound.payouts;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.essenza.draco.modules.marketplace.application.port.PayoutDispatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Wompi Pagos a terceros (dispersión). Cabeceras {@code x-api-key}, {@code user-principal-id} e
 * {@code idempotency-key}; montos en centavos. Arranca en sandbox ({@code api.sandbox.payouts.wompi.co});
 * otra URL solo funciona con {@code essenza.payouts.wompi.allow-production=true}. Las llaves van solo en
 * variables de entorno. Los nombres de los campos de las respuestas se leen con tolerancia (validar en
 * sandbox): {@code data.payoutId|id}, listas en {@code data} o {@code data.items|data.data}.
 */
@Component
public class WompiPayoutDispatcher implements PayoutDispatcher {

    static final Duration TIMEOUT = Duration.ofSeconds(20);
    static final ObjectMapper JSON = new ObjectMapper();

    /** Transporte HTTP (reemplazable en pruebas). */
    public interface Transport {
        Reply send(String method, String url, Map<String, String> headers, String body) throws IOException, InterruptedException;
    }

    public record Reply(int status, String body) {
    }

    private final String apiKey;
    private final String principalId;
    private final String accountId;
    private final String baseUrl;
    private final String banksUrl;
    private final boolean allowProduction;
    private final String sandboxStatus;
    private final String paymentType;
    private final Transport transport;

    @org.springframework.beans.factory.annotation.Autowired
    public WompiPayoutDispatcher(@Value("${essenza.payouts.wompi.api-key:}") String apiKey,
                                 @Value("${essenza.payouts.wompi.principal-id:}") String principalId,
                                 @Value("${essenza.payouts.wompi.account-id:}") String accountId,
                                 @Value("${essenza.payouts.wompi.base-url:https://api.sandbox.payouts.wompi.co/v2}") String baseUrl,
                                 @Value("${essenza.payouts.wompi.banks-url:}") String banksUrl,
                                 @Value("${essenza.payouts.wompi.allow-production:false}") boolean allowProduction,
                                 @Value("${essenza.payouts.wompi.sandbox-status:}") String sandboxStatus,
                                 @Value("${essenza.payouts.wompi.payment-type:PROVIDERS}") String paymentType) {
        this(apiKey, principalId, accountId, baseUrl, banksUrl, allowProduction, sandboxStatus, paymentType, httpTransport());
    }

    public WompiPayoutDispatcher(String apiKey, String principalId, String accountId, String baseUrl, String banksUrl,
                                 boolean allowProduction, String sandboxStatus, String paymentType, Transport transport) {
        this.apiKey = trim(apiKey);
        this.principalId = trim(principalId);
        this.accountId = trim(accountId);
        String base = trim(baseUrl) == null ? "https://api.sandbox.payouts.wompi.co/v2" : baseUrl.trim();
        this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.banksUrl = trim(banksUrl) == null ? this.baseUrl + "/banks" : banksUrl.trim();
        this.allowProduction = allowProduction;
        this.sandboxStatus = trim(sandboxStatus) == null ? null : sandboxStatus.trim().toUpperCase(Locale.ROOT);
        String type = trim(paymentType) == null ? "PROVIDERS" : paymentType.trim().toUpperCase(Locale.ROOT);
        this.paymentType = List.of("PROVIDERS", "PAYROLL", "OTHER").contains(type) ? type : "PROVIDERS";
        this.transport = transport;
    }

    @Override
    public String provider() {
        return "WOMPI";
    }

    @Override
    public String environment() {
        return sandbox() ? "sandbox" : "production";
    }

    boolean sandbox() {
        return URI.create(baseUrl).getHost() != null && URI.create(baseUrl).getHost().contains("sandbox");
    }

    @Override
    public boolean configured() {
        return apiKey != null && principalId != null && accountId != null && baseUrl.startsWith("https://")
                && (sandbox() || allowProduction);
    }

    @Override
    public Dispatched dispatch(DispatchRequest request) {
        requireConfigured();
        String body = body(request);
        Reply reply = call("POST", baseUrl + "/payouts", body, request.idempotencyKey());
        JsonNode json = parse(reply.body());
        if (reply.status() == 409) {
            // Llave repetida (EXC_032): el lote pudo crearse en un intento anterior; se resuelve buscando por referencia.
            throw new DispatchException("El proveedor ya recibió este envío (409).", true);
        }
        if (reply.status() >= 500) {
            throw new DispatchException("El proveedor respondió " + reply.status() + ".", true);
        }
        if (reply.status() < 200 || reply.status() >= 300) {
            throw new DispatchException("El proveedor rechazó el lote (" + reply.status() + "): " + message(json), false);
        }
        JsonNode data = json.path("data");
        String id = text(data, "payoutId", "id");
        if (id == null) {
            throw new DispatchException("Respuesta sin id de lote.", true);
        }
        return new Dispatched(id, text(data, "status"));
    }

    String body(DispatchRequest request) {
        ObjectNode root = JSON.createObjectNode();
        root.put("reference", request.reference());
        root.put("accountId", accountId);
        root.put("paymentType", paymentType);
        if (sandbox() && sandboxStatus != null) {
            root.put("transactionStatus", sandboxStatus);
        }
        ArrayNode txs = root.putArray("transactions");
        for (DispatchItem item : request.items()) {
            ObjectNode tx = txs.addObject();
            tx.put("amount", item.amountInCents());
            tx.put("name", clip(item.name(), 100));
            tx.put("email", item.email());
            tx.put("reference", item.reference());
            if (item.legalIdType() != null && item.legalId() != null) {
                tx.put("legalIdType", item.legalIdType());
                tx.put("legalId", item.legalId());
            }
            if (item.key() != null) {
                tx.put("key", item.key());
            } else {
                tx.put("bankId", item.bankId());
                tx.put("accountType", item.accountType());
                tx.put("accountNumber", item.accountNumber());
            }
        }
        try {
            return JSON.writeValueAsString(root);
        } catch (IOException ex) {
            throw new DispatchException("No se pudo armar el envío.", false);
        }
    }

    @Override
    public Optional<String> findByReference(String reference) {
        requireConfigured();
        Reply reply = call("GET", baseUrl + "/payouts?reference=" + URLEncoder.encode(reference, StandardCharsets.UTF_8), null, null);
        if (reply.status() == 404) {
            return Optional.empty();
        }
        ensureOk(reply, "consultar el lote");
        for (JsonNode node : list(parse(reply.body()))) {
            String ref = text(node, "reference");
            if (ref == null || ref.equals(reference)) {
                String id = text(node, "payoutId", "id");
                if (id != null) {
                    return Optional.of(id);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<TxStatus> transactions(String payoutId) {
        requireConfigured();
        Reply reply = call("GET", baseUrl + "/payouts/" + URLEncoder.encode(payoutId, StandardCharsets.UTF_8) + "/transactions", null, null);
        ensureOk(reply, "consultar los pagos del lote");
        List<TxStatus> result = new ArrayList<>();
        for (JsonNode node : list(parse(reply.body()))) {
            result.add(new TxStatus(text(node, "reference"), text(node, "id", "transactionId"), text(node, "status"),
                    clip(text(node, "statusMessage", "failureReason", "reason", "message"), 300)));
        }
        return result;
    }

    @Override
    public List<ProviderBank> banks() {
        requireConfigured();
        Reply reply = call("GET", banksUrl, null, null);
        ensureOk(reply, "consultar las entidades");
        List<ProviderBank> result = new ArrayList<>();
        for (JsonNode node : list(parse(reply.body()))) {
            String id = text(node, "id", "bankId");
            if (id != null) {
                result.add(new ProviderBank(id, text(node, "name", "bankName", "description"), text(node, "code", "bankCode", "achCode")));
            }
        }
        return result;
    }

    // ── Soporte ─────────────────────────────────────────────────────────────

    private Reply call(String method, String url, String body, String idempotencyKey) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-api-key", apiKey);
        headers.put("user-principal-id", principalId);
        headers.put("Accept", "application/json");
        if (body != null) {
            headers.put("Content-Type", "application/json");
        }
        if (idempotencyKey != null) {
            headers.put("idempotency-key", idempotencyKey);
        }
        try {
            return transport.send(method, url, headers, body);
        } catch (IOException ex) {
            throw new DispatchException("Sin respuesta del proveedor (" + ex.getClass().getSimpleName() + ").", true);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DispatchException("Comunicación con el proveedor interrumpida.", true);
        }
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new DispatchException("Wompi Pagos a terceros no está configurado o el ambiente de producción no está habilitado.", false);
        }
    }

    private static void ensureOk(Reply reply, String what) {
        if (reply.status() < 200 || reply.status() >= 300) {
            throw new DispatchException("No se pudo " + what + " (" + reply.status() + "): " + message(parse(reply.body())),
                    reply.status() >= 500);
        }
    }

    /** Lista en {@code data}, {@code data.items}, {@code data.data} o la raíz. */
    static List<JsonNode> list(JsonNode root) {
        JsonNode data = root.path("data");
        JsonNode array = data.isArray() ? data
                : data.path("items").isArray() ? data.path("items")
                : data.path("data").isArray() ? data.path("data")
                : data.path("transactions").isArray() ? data.path("transactions")
                : root.isArray() ? root : null;
        List<JsonNode> result = new ArrayList<>();
        if (array != null) {
            array.forEach(result::add);
        } else if (data.isObject() && !data.isEmpty()) {
            result.add(data);
        }
        return result;
    }

    static JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return JSON.createObjectNode();
        }
        try {
            return JSON.readTree(body);
        } catch (IOException ex) {
            return JSON.createObjectNode();
        }
    }

    static String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isValueNode() && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private static String message(JsonNode json) {
        String text = text(json, "message");
        if (text == null) {
            text = text(json.path("error"), "message", "reason", "type");
        }
        if (text == null && json.path("error").isValueNode()) {
            text = json.path("error").asText();
        }
        return text == null ? "sin detalle" : clip(text, 200);
    }

    private static String clip(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Transport httpTransport() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        return (method, url, headers, body) -> {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Reply(response.statusCode(), response.body());
        };
    }
}
