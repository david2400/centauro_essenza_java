package com.essenza.draco.modules.sales.infrastructure.outbound.gateway;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import com.essenza.draco.modules.sales.application.output.gateway.PaymentGateway;
import com.essenza.draco.modules.sales.domain.model.payment.GatewayProvider;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentStatus;

/**
 * Wompi (Bancolombia): Web Checkout por redirección con firma de integridad, eventos
 * {@code transaction.updated} firmados con el secreto de eventos y consulta de transacciones.
 *
 * <ul>
 *   <li>Integridad: SHA256(referencia + monto_en_centavos + moneda [+ expiración] + secreto_integridad).</li>
 *   <li>Eventos: SHA256(valores de signature.properties + timestamp + secreto_eventos) =
 *       signature.checksum (también en el header X-Event-Checksum).</li>
 *   <li>Llaves pub_test_/prv_test_ usan el API de sandbox; pub_prod_ el de producción, y solo se aceptan con
 *       {@code essenza.payments.wompi.allow-production=true} (WOMPI_ALLOW_PRODUCTION).</li>
 * </ul>
 */
@Component
public class WompiGateway implements PaymentGateway {

    static final String SANDBOX_API = "https://sandbox.wompi.co/v1";
    static final String PRODUCTION_API = "https://production.wompi.co/v1";

    private final PaymentsSettings settings;
    private final GatewayHttp http;

    public WompiGateway(PaymentsSettings settings, GatewayHttp http) {
        this.settings = settings;
        this.http = http;
    }

    @Override
    public GatewayProvider provider() {
        return GatewayProvider.WOMPI;
    }

    @Override
    public boolean configured() {
        return !settings.getWompiPublicKey().isEmpty() && !settings.getWompiIntegritySecret().isEmpty()
                && !settings.getWompiEventsSecret().isEmpty()
                && (!"production".equals(environment()) || settings.isWompiAllowProduction());
    }

    @Override
    public String environment() {
        return settings.getWompiPublicKey().startsWith("pub_prod_") ? "production" : "test";
    }

    String apiUrl() {
        if (!settings.getWompiApiUrl().isEmpty()) {
            return settings.getWompiApiUrl();
        }
        return "production".equals(environment()) ? PRODUCTION_API : SANDBOX_API;
    }

    static long cents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    /** Firma de integridad del checkout. */
    String integrity(String reference, long amountInCents, String currency, String expiration) {
        return Signatures.sha256Hex(reference + amountInCents + currency + (expiration == null ? "" : expiration)
                + settings.getWompiIntegritySecret());
    }

    @Override
    public CheckoutSession createCheckout(CheckoutRequest request) {
        long amountInCents = cents(request.amount());
        String expiration = request.expiresAt() == null ? null
                : request.expiresAt().truncatedTo(ChronoUnit.SECONDS).toString();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("public-key", settings.getWompiPublicKey());
        params.put("currency", request.currency());
        params.put("amount-in-cents", String.valueOf(amountInCents));
        params.put("reference", request.paymentReference());
        params.put("signature:integrity", integrity(request.paymentReference(), amountInCents, request.currency(), expiration));
        if (expiration != null) {
            params.put("expiration-time", expiration);
        }
        if (request.returnUrl() != null) {
            params.put("redirect-url", request.returnUrl());
        }
        if (request.payerEmail() != null) {
            params.put("customer-data:email", request.payerEmail());
        }
        String query = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        String base = settings.getWompiCheckoutUrl();
        return new CheckoutSession(base + (base.contains("?") ? "&" : "?") + query, null);
    }

    @Override
    public Optional<GatewayStatus> fetchStatus(String paymentReference, String providerReference) {
        GatewayHttp.Response response;
        JsonNode transaction;
        if (providerReference != null && !providerReference.isBlank()) {
            response = http.get(apiUrl() + "/transactions/" + URLEncoder.encode(providerReference, StandardCharsets.UTF_8), Map.of());
            if (response.status() == 404) {
                return Optional.empty();
            }
            ensureOk(response, "consultar la transacción");
            transaction = response.body().path("data");
        } else {
            if (settings.getWompiPrivateKey().isEmpty()) {
                return Optional.empty(); // sin llave privada solo se puede consultar por id de transacción
            }
            response = http.get(apiUrl() + "/transactions?reference=" + URLEncoder.encode(paymentReference, StandardCharsets.UTF_8),
                    Map.of("Authorization", "Bearer " + settings.getWompiPrivateKey()));
            ensureOk(response, "buscar la transacción");
            JsonNode data = response.body().path("data");
            if (!data.isArray() || data.isEmpty()) {
                return Optional.empty();
            }
            transaction = data.get(0);
        }
        if (transaction.isMissingNode() || transaction.isNull()) {
            return Optional.empty();
        }
        return Optional.of(toStatus(transaction));
    }

    @Override
    public WebhookEvent parseWebhook(Map<String, String> headers, Map<String, String> query, String body) {
        if (!configured()) {
            throw new InvalidWebhookException("Wompi no está configurado.");
        }
        JsonNode event;
        try {
            event = http.json().readTree(body);
        } catch (Exception ex) {
            throw new InvalidWebhookException("Evento de Wompi mal formado.");
        }
        JsonNode data = event.path("data");
        JsonNode properties = event.path("signature").path("properties");
        String timestamp = event.path("timestamp").asText("");
        if (!properties.isArray() || properties.isEmpty() || timestamp.isEmpty()) {
            throw new InvalidWebhookException("Evento de Wompi sin firma.");
        }
        StringBuilder chain = new StringBuilder();
        for (JsonNode property : properties) {
            JsonNode value = data;
            for (String part : property.asText().split("\\.")) {
                value = value.path(part);
            }
            chain.append(value.isMissingNode() || value.isNull() ? "" : value.asText());
        }
        chain.append(timestamp).append(settings.getWompiEventsSecret());
        String received = SandboxGateway.header(headers, "x-event-checksum");
        if (received == null) {
            received = event.path("signature").path("checksum").asText(null);
        }
        if (!Signatures.sameHex(Signatures.sha256Hex(chain.toString()), received)) {
            throw new InvalidWebhookException("Firma del evento de Wompi inválida.");
        }
        if (!"transaction.updated".equals(event.path("event").asText())) {
            // Otros eventos (p. ej. nequi_token.updated) no cambian pagos.
            return new WebhookEvent(event.path("event").asText() + ":" + timestamp, null, null, null, null, null, false);
        }
        JsonNode transaction = data.path("transaction");
        GatewayStatus status = toStatus(transaction);
        return new WebhookEvent(status.providerReference() + ":" + transaction.path("status").asText() + ":" + timestamp,
                status.paymentReference(), status.providerReference(), status.status(), status.amount(), status.detail(), false);
    }

    static GatewayStatus toStatus(JsonNode transaction) {
        String raw = transaction.path("status").asText("PENDING");
        PaymentStatus status = switch (raw) {
            case "APPROVED" -> PaymentStatus.APPROVED;
            case "DECLINED", "ERROR" -> PaymentStatus.DECLINED;
            case "VOIDED" -> PaymentStatus.VOIDED;
            default -> PaymentStatus.PENDING;
        };
        BigDecimal amount = transaction.hasNonNull("amount_in_cents")
                ? BigDecimal.valueOf(transaction.get("amount_in_cents").asLong()).movePointLeft(2) : null;
        String method = transaction.path("payment_method_type").asText("");
        String message = transaction.path("status_message").asText("");
        String detail = ("Wompi " + raw + (method.isEmpty() ? "" : " · " + method) + (message.isEmpty() ? "" : " · " + message)).trim();
        return new GatewayStatus(transaction.path("reference").asText(null), transaction.path("id").asText(null), status,
                amount, detail);
    }

    private static void ensureOk(GatewayHttp.Response response, String action) {
        if (!response.ok()) {
            throw new GatewayException("Wompi respondió " + response.status() + " al " + action + ".");
        }
    }

    /** Fecha de expiración en el formato que firma Wompi (ISO 8601 UTC, sin fracciones). */
    static String expiration(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
