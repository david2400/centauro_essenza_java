package com.essenza.draco.modules.sales.application.output.gateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.essenza.draco.modules.sales.domain.model.payment.GatewayProvider;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentStatus;

/**
 * Pasarela de pagos en línea (puerto de salida). Cada proveedor (sandbox, Wompi, Mercado Pago)
 * implementa la creación del link de pago, la verificación de sus notificaciones y la consulta
 * de estado.
 */
public interface PaymentGateway {

    GatewayProvider provider();

    /** Credenciales completas (el sandbox siempre lo está si está habilitado). */
    boolean configured();

    /** test | production | sandbox. */
    String environment();

    CheckoutSession createCheckout(CheckoutRequest request);

    /** Estado actual en la pasarela, por referencia propia o por id de la pasarela. */
    Optional<GatewayStatus> fetchStatus(String paymentReference, String providerReference);

    /**
     * Verifica la firma y lee una notificación.
     *
     * @throws InvalidWebhookException si la firma no es válida
     */
    WebhookEvent parseWebhook(Map<String, String> headers, Map<String, String> query, String body);

    /** La pasarela permite reembolsar desde el API. */
    default boolean supportsRefund() {
        return false;
    }

    /**
     * Prueba de conexión de solo lectura (no crea cobros): verifica que las llaves respondan. Las pasarelas que no la
     * implementan responden "no disponible".
     */
    default ConnectionTest testConnection() {
        return new ConnectionTest(false, false, "Esta pasarela no tiene prueba de conexión.", null);
    }

    /** {@code supported}: la pasarela permite probar; {@code merchant}: nombre del comercio si la pasarela lo informa. */
    record ConnectionTest(boolean supported, boolean ok, String message, String merchant) {
    }

    /** Reembolso en la pasarela; solo si {@link #supportsRefund()}. */
    default void refund(String providerReference, BigDecimal amount, String idempotencyKey) {
        throw new UnsupportedOperationException("La pasarela " + provider() + " no permite reembolsos por API.");
    }

    record CheckoutRequest(Long orderId, String paymentReference, BigDecimal amount, String currency,
                           String description, String payerEmail, String returnUrl, Instant expiresAt) {
    }

    record CheckoutSession(String url, String providerReference) {
    }

    record GatewayStatus(String paymentReference, String providerReference, PaymentStatus status,
                         BigDecimal amount, String detail) {
    }

    /**
     * Notificación verificada. Si {@code needsFetch} la pasarela solo informa el id y hay que
     * consultar el estado con {@link #fetchStatus}.
     */
    record WebhookEvent(String eventKey, String paymentReference, String providerReference, PaymentStatus status,
                        BigDecimal amount, String detail, boolean needsFetch) {
    }

    /** Firma inválida o notificación mal formada. */
    class InvalidWebhookException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public InvalidWebhookException(String message) {
            super(message);
        }
    }

    /** Error al comunicarse con la pasarela. */
    class GatewayException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public GatewayException(String message, Throwable cause) {
            super(message, cause);
        }

        public GatewayException(String message) {
            super(message);
        }
    }
}
