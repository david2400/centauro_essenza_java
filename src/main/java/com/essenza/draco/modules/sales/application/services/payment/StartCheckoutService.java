package com.essenza.draco.modules.sales.application.services.payment;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.essenza.draco.modules.sales.application.dto.payment.CheckoutDto;
import com.essenza.draco.modules.sales.application.dto.payment.StartCheckoutDto;
import com.essenza.draco.modules.sales.application.input.payment.StartCheckoutUseCase;
import com.essenza.draco.modules.sales.application.output.gateway.PaymentGateway;
import com.essenza.draco.modules.sales.application.output.gateway.PaymentGateways;
import com.essenza.draco.modules.sales.application.output.repository.PaymentRepository;
import com.essenza.draco.modules.sales.domain.model.payment.OrderPaymentStatus;
import com.essenza.draco.modules.sales.domain.model.payment.Payment;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentMethod;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentRuleViolation;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentStatus;

/**
 * Pago en línea: crea el pago PENDIENTE con una referencia única, pide el link de pago a la
 * pasarela del medio y lo devuelve. La aprobación llega después por webhook (o con "sync").
 */
@Service
@Transactional
@RequiredArgsConstructor
public class StartCheckoutService implements StartCheckoutUseCase {

    /** Vigencia del link de pago. */
    static final Duration CHECKOUT_TTL = Duration.ofHours(1);

    private final PaymentRepository payments;
    private final PaymentGateways gateways;
    private final PaymentSoporte soporte;

    @Override
    public CheckoutDto start(StartCheckoutDto input) {
        PaymentSoporte.OrderView order = soporte.lockedOrder(input.orderId());
        soporte.rejectThirdPartyCollection(order.id(), "en línea");
        PaymentMethod method = soporte.method(input.paymentMethodId());
        if (!method.isGateway()) {
            throw new PaymentRuleViolation("\"" + method.name() + "\" no es una pasarela en línea: registra el pago directamente.");
        }
        PaymentGateway gateway = soporte.gateway(method.provider());
        soporte.expireStaleCheckouts(order.id());
        // Un nuevo intento reemplaza los links anteriores que el cliente no llegó a pagar.
        payments.findByOrderId(order.id()).stream()
                .filter(previous -> previous.getStatus() == PaymentStatus.PENDING && previous.getProvider() != null
                        && previous.getProviderReference() == null)
                .forEach(previous -> soporte.expire(previous, "Reemplazado por un nuevo intento de pago en línea."));
        OrderPaymentStatus.Balance balance = soporte.balance(order);
        BigDecimal amount = soporte.checkCollectable(input.amount() == null ? balance.collectable() : input.amount(), balance);
        method.checkAmount(amount);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(CHECKOUT_TTL);
        String returnUrl = PaymentSoporte.blankToNull(input.returnUrl()) == null ? gateways.defaultReturnUrl() : input.returnUrl().trim();
        String paymentReference = soporte.newPaymentReference(order.id());
        Payment payment = new Payment(null, order.id(), method.id(), method.channel(), method.kind(), method.provider(),
                PaymentStatus.PENDING, amount, method.feeFor(amount), null, null, BigDecimal.ZERO, null, paymentReference,
                null, null, returnUrl, PaymentSoporte.blankToNull(input.payerEmail()), null, null, null, expiresAt, now);
        Payment saved = payments.save(payment);

        PaymentGateway.CheckoutSession session = gateway.createCheckout(new PaymentGateway.CheckoutRequest(order.id(),
                paymentReference, amount, PaymentSoporte.CURRENCY, "Orden #" + order.id(), saved.getPayerEmail(),
                returnUrl, expiresAt));
        saved.attachCheckout(session.url(), session.providerReference());
        saved = payments.save(saved);
        soporte.log(saved, method.provider().name(), null, "CHECKOUT_CREATED", null, amount, null, null);
        return new CheckoutDto(soporte.toDto(saved), session.url());
    }
}
