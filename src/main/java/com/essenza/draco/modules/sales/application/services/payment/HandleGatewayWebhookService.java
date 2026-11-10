package com.essenza.draco.modules.sales.application.services.payment;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.essenza.draco.modules.sales.application.input.payment.HandleGatewayWebhookUseCase;
import com.essenza.draco.modules.sales.application.output.gateway.PaymentGateway;
import com.essenza.draco.modules.sales.application.output.gateway.PaymentGateways;
import com.essenza.draco.modules.sales.application.output.repository.PaymentEventLog;
import com.essenza.draco.modules.sales.application.output.repository.PaymentRepository;
import com.essenza.draco.modules.sales.domain.model.payment.GatewayProvider;
import com.essenza.draco.modules.sales.domain.model.payment.Payment;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentRuleViolation;

/**
 * Webhooks de las pasarelas: verifica la firma (la pasarela), descarta notificaciones repetidas
 * (bitácora con clave única) y aplica el estado al pago. Si la pasarela solo informa el id
 * (Mercado Pago) consulta el estado en su API.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class HandleGatewayWebhookService implements HandleGatewayWebhookUseCase {

    private final PaymentRepository payments;
    private final PaymentGateways gateways;
    private final PaymentEventLog events;
    private final PaymentSoporte soporte;
    private final CheckoutGroupSoporte checkoutGroups;

    @Override
    public boolean handle(String provider, Map<String, String> headers, Map<String, String> query, String body) {
        GatewayProvider gatewayProvider = GatewayProvider.parse(provider);
        PaymentGateway gateway = gateways.find(gatewayProvider)
                .orElseThrow(() -> new PaymentRuleViolation("La pasarela " + provider + " no está disponible."));
        PaymentGateway.WebhookEvent event = gateway.parseWebhook(headers, query, body);
        if (event.needsFetch()) {
            Optional<PaymentGateway.GatewayStatus> status = gateway.fetchStatus(event.paymentReference(), event.providerReference());
            if (status.isEmpty()) {
                log.warn("Webhook {} sin pago consultable: {}", gatewayProvider, event.providerReference());
                return false;
            }
            PaymentGateway.GatewayStatus s = status.get();
            event = new PaymentGateway.WebhookEvent(event.eventKey() + ":" + s.status(), s.paymentReference(),
                    s.providerReference(), s.status(), s.amount(), s.detail(), false);
        }
        String source = gatewayProvider.name();
        if (event.eventKey() != null && events.exists(source, event.eventKey())) {
            return false;
        }
        Optional<Payment> payment = event.paymentReference() == null ? Optional.empty()
                : payments.lockByPaymentReference(event.paymentReference());
        if (payment.isEmpty() && event.paymentReference() != null) {
            // Link de pago de una compra multivendedor: la referencia es la del grupo.
            Optional<com.essenza.draco.modules.sales.application.output.repository.CheckoutGroupStore.Group> group =
                    checkoutGroups.store().byReference(event.paymentReference());
            if (group.isPresent()) {
                return checkoutGroups.apply(group.get(), event.status(), event.providerReference(), event.amount(), event.detail(),
                        source, event.eventKey(), truncate(body));
            }
        }
        if (payment.isEmpty() && event.providerReference() != null) {
            payment = payments.lockByProvider(source, event.providerReference());
        }
        if (payment.isEmpty()) {
            log.warn("Webhook {} para un pago desconocido: ref={} provider_ref={}", gatewayProvider,
                    event.paymentReference(), event.providerReference());
            // Sin clave: si la pasarela reintenta (p. ej. el pago aún no se había guardado) se vuelve a procesar.
            soporte.log(null, source, null, "UNMATCHED", null, event.amount(),
                    "Referencia " + event.paymentReference() + " · " + event.eventKey(), truncate(body));
            return false;
        }
        Optional<com.essenza.draco.modules.sales.application.output.repository.CheckoutGroupStore.Group> group =
                payment.get().getId() == null ? Optional.empty() : checkoutGroups.store().byPayment(payment.get().getId());
        if (group.isPresent()) {
            return checkoutGroups.apply(group.get(), event.status(), event.providerReference(), event.amount(), event.detail(),
                    source, event.eventKey(), truncate(body));
        }
        return soporte.applyGatewayStatus(payment.get(), event.status(), event.providerReference(), event.amount(),
                event.detail(), source, event.eventKey(), truncate(body));
    }

    private static String truncate(String body) {
        return body == null || body.length() <= 20_000 ? body : body.substring(0, 20_000);
    }
}
