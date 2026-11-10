package com.essenza.draco.modules.sales.infrastructure.outbound.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

/**
 * Configuración de pagos (application.properties / variables de entorno). Las credenciales de
 * las pasarelas nunca se exponen al cliente.
 */
@Getter
@Component
public class PaymentsSettings {

    /** URL pública del backend (webhooks y simulador). */
    private final String publicBaseUrl;
    /** A dónde vuelve el cliente tras pagar si el checkout no trae otra. */
    private final String defaultReturnUrl;

    private final boolean sandboxEnabled;
    private final String sandboxSecret;

    private final String wompiPublicKey;
    private final String wompiPrivateKey;
    private final String wompiIntegritySecret;
    private final String wompiEventsSecret;
    private final String wompiApiUrl;
    private final String wompiCheckoutUrl;
    /**
     * Llaves de producción (pub_prod_) solo si se habilita explícitamente: evita cobros reales por
     * configurar por error las llaves de producción en un ambiente de pruebas.
     */
    @Value("${essenza.payments.wompi.allow-production:false}")
    private boolean wompiAllowProduction;

    private final String mercadoPagoAccessToken;
    private final String mercadoPagoWebhookSecret;
    private final String mercadoPagoApiUrl;

    public PaymentsSettings(
            @Value("${essenza.payments.public-base-url:http://localhost:8083}") String publicBaseUrl,
            @Value("${essenza.payments.default-return-url:http://localhost:3000/checkout/resultado}") String defaultReturnUrl,
            @Value("${essenza.payments.sandbox.enabled:true}") boolean sandboxEnabled,
            @Value("${essenza.payments.sandbox.secret:essenza-sandbox}") String sandboxSecret,
            @Value("${essenza.payments.wompi.public-key:}") String wompiPublicKey,
            @Value("${essenza.payments.wompi.private-key:}") String wompiPrivateKey,
            @Value("${essenza.payments.wompi.integrity-secret:}") String wompiIntegritySecret,
            @Value("${essenza.payments.wompi.events-secret:}") String wompiEventsSecret,
            @Value("${essenza.payments.wompi.api-url:}") String wompiApiUrl,
            @Value("${essenza.payments.wompi.checkout-url:https://checkout.wompi.co/p/}") String wompiCheckoutUrl,
            @Value("${essenza.payments.mercadopago.access-token:}") String mercadoPagoAccessToken,
            @Value("${essenza.payments.mercadopago.webhook-secret:}") String mercadoPagoWebhookSecret,
            @Value("${essenza.payments.mercadopago.api-url:https://api.mercadopago.com}") String mercadoPagoApiUrl) {
        this.publicBaseUrl = stripSlash(publicBaseUrl);
        this.defaultReturnUrl = defaultReturnUrl;
        this.sandboxEnabled = sandboxEnabled;
        this.sandboxSecret = sandboxSecret;
        this.wompiPublicKey = trim(wompiPublicKey);
        this.wompiPrivateKey = trim(wompiPrivateKey);
        this.wompiIntegritySecret = trim(wompiIntegritySecret);
        this.wompiEventsSecret = trim(wompiEventsSecret);
        this.wompiApiUrl = trim(wompiApiUrl);
        this.wompiCheckoutUrl = wompiCheckoutUrl;
        this.mercadoPagoAccessToken = trim(mercadoPagoAccessToken);
        this.mercadoPagoWebhookSecret = trim(mercadoPagoWebhookSecret);
        this.mercadoPagoApiUrl = stripSlash(mercadoPagoApiUrl);
    }

    /** Para pruebas (en la app la fija {@code essenza.payments.wompi.allow-production}). */
    void setWompiAllowProduction(boolean allow) {
        this.wompiAllowProduction = allow;
    }

        private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stripSlash(String value) {
        String v = trim(value);
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }
}
