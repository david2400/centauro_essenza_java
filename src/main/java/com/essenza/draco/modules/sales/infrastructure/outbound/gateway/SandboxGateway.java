package com.essenza.draco.modules.sales.infrastructure.outbound.gateway;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import com.essenza.draco.modules.sales.application.output.gateway.PaymentGateway;
import com.essenza.draco.modules.sales.domain.model.payment.GatewayProvider;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentStatus;

/**
 * Pasarela de pruebas: el "link de pago" es una página del propio backend donde se elige
 * aprobar o rechazar; la decisión entra como un webhook firmado (HMAC-SHA256 con el secreto del
 * sandbox), así el flujo es el mismo que con una pasarela real. Desactívala en producción con
 * {@code essenza.payments.sandbox.enabled=false}.
 */
@Component
public class SandboxGateway implements PaymentGateway {

    static final String SIGNATURE_HEADER = "x-sandbox-signature";

    private final PaymentsSettings settings;
    private final GatewayHttp http;

    public SandboxGateway(PaymentsSettings settings, GatewayHttp http) {
        this.settings = settings;
        this.http = http;
    }

    @Override
    public GatewayProvider provider() {
        return GatewayProvider.SANDBOX;
    }

    @Override
    public boolean configured() {
        return settings.isSandboxEnabled();
    }

    @Override
    public ConnectionTest testConnection() {
        return configured() ? new ConnectionTest(true, true, "Simulador activo: los pagos se aprueban o rechazan sin dinero real.", null)
                : new ConnectionTest(true, false, "El simulador está apagado (PAYMENTS_SANDBOX_ENABLED=false).", null);
    }

    @Override
    public String environment() {
        return "sandbox";
    }

    @Override
    public CheckoutSession createCheckout(CheckoutRequest request) {
        String url = settings.getPublicBaseUrl() + "/api/shop/sales/payments/sandbox/" + request.paymentReference()
                + "?amount=" + request.amount().toPlainString()
                + "&order=" + request.orderId()
                + (request.returnUrl() == null ? "" : "&return=" + URLEncoder.encode(request.returnUrl(), StandardCharsets.UTF_8));
        return new CheckoutSession(url, null);
    }

    @Override
    public Optional<GatewayStatus> fetchStatus(String paymentReference, String providerReference) {
        return Optional.empty(); // el simulador no guarda estado propio: el pago cambia solo por su webhook
    }

    @Override
    public WebhookEvent parseWebhook(Map<String, String> headers, Map<String, String> query, String body) {
        if (!settings.isSandboxEnabled()) {
            throw new InvalidWebhookException("El simulador de pagos está desactivado.");
        }
        String received = header(headers, SIGNATURE_HEADER);
        if (!Signatures.sameHex(sign(body), received)) {
            throw new InvalidWebhookException("Firma del simulador inválida.");
        }
        try {
            JsonNode node = http.json().readTree(body);
            String status = node.path("status").asText();
            PaymentStatus target = "APPROVED".equalsIgnoreCase(status) ? PaymentStatus.APPROVED : PaymentStatus.DECLINED;
            String reference = node.path("reference").asText(null);
            String transaction = node.path("transaction_id").asText(null);
            BigDecimal amount = node.hasNonNull("amount") ? new BigDecimal(node.get("amount").asText()) : null;
            return new WebhookEvent(transaction + ":" + target, reference, transaction, target, amount,
                    target == PaymentStatus.APPROVED ? "Aprobado en el simulador" : "Rechazado en el simulador", false);
        } catch (Exception ex) {
            throw new InvalidWebhookException("Notificación del simulador mal formada: " + ex.getMessage());
        }
    }

    /** Firma un evento del simulador (la usa la página de pago de pruebas). */
    public String sign(String body) {
        return Signatures.hmacSha256Hex(settings.getSandboxSecret(), body == null ? "" : body);
    }

    static String header(Map<String, String> headers, String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
    }
}
