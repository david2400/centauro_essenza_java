package com.essenza.draco.modules.sales.infrastructure.outbound.cliente;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.essenza.draco.modules.sales.application.output.storefront.ClienteHookOutbox;
import com.essenza.draco.modules.sales.application.output.storefront.ClienteHookSender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * POST firmado a centauro_cliente ({@code <CLIENTE_API_URL>/public/hooks/draco}). Firma: {@code X-Essenza-Timestamp}
 * (segundos Unix) y {@code X-Essenza-Signature: v1=hex(HMAC-SHA256(CLIENTE_HOOK_SECRET, timestamp + "." + cuerpo))}.
 * Sin URL o con un secreto de menos de 32 caracteres queda apagado.
 */
@Component
public class HttpClienteHookSender implements ClienteHookSender {

    private static final Logger log = LoggerFactory.getLogger(HttpClienteHookSender.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    static final int MIN_SECRET = 32;

    private final String endpoint;
    private final String secret;
    private final HttpClient http;
    private final Clock clock;

    public HttpClienteHookSender(@Value("${essenza.cliente-hooks.base-url:}") String baseUrl,
                                 @Value("${essenza.cliente-hooks.secret:}") String secret) {
        this.endpoint = baseUrl == null || baseUrl.isBlank() ? "" : baseUrl.strip().replaceAll("/+$", "") + "/public/hooks/draco";
        this.secret = secret == null ? "" : secret.strip();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.clock = Clock.systemUTC();
        if (!this.endpoint.isEmpty() && this.secret.length() < MIN_SECRET) {
            log.warn("CLIENTE_HOOK_SECRET falta o es muy corto (mínimo {} caracteres): no se avisa a centauro_cliente.", MIN_SECRET);
        }
    }

    @Override
    public boolean enabled() {
        return !endpoint.isEmpty() && secret.length() >= MIN_SECRET;
    }

    @Override
    public void send(ClienteHookOutbox.Hook hook) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("event", hook.event());
        payload.put("event_id", hook.eventKey());
        payload.put("purchase_number", hook.purchaseNumber());
        payload.put("order_number", hook.orderNumber());
        payload.put("tracking_number", hook.trackingNumber());
        payload.put("carrier", hook.carrierName());
        String body;
        try {
            body = JSON.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
        long timestamp = clock.instant().getEpochSecond();
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-Essenza-Timestamp", String.valueOf(timestamp))
                .header("X-Essenza-Signature", sign(secret, timestamp, body))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new IllegalStateException("centauro_cliente no respondió: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("envío interrumpido", ex);
        }
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("centauro_cliente respondió " + response.statusCode());
        }
    }

    static String sign(String secret, long timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "v1=" + HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 no disponible", ex);
        }
    }
}
