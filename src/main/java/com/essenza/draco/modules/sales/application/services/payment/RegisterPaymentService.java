package com.essenza.draco.modules.sales.application.services.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.essenza.draco.modules.sales.application.output.repository.CashSessionRepository;
import com.essenza.draco.shared.exceptions.NotFoundException;

import com.essenza.draco.modules.sales.application.dto.payment.PaymentDto;
import com.essenza.draco.modules.sales.application.dto.payment.RegisterPaymentDto;
import com.essenza.draco.modules.sales.application.input.payment.RegisterPaymentUseCase;
import com.essenza.draco.modules.sales.application.output.repository.PaymentRepository;
import com.essenza.draco.modules.sales.domain.model.payment.OrderPaymentStatus;
import com.essenza.draco.modules.sales.domain.model.payment.Payment;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentMethod;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentRuleViolation;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentStatus;

/**
 * Pago cobrado por el personal: efectivo (con cambio), datáfono, transferencia, billetera o
 * contraentrega. Queda aprobado si el tipo lo permite y no se pidió dejarlo pendiente; al cubrir
 * el total la orden pasa a PAGADA.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class RegisterPaymentService implements RegisterPaymentUseCase {

    private final PaymentRepository payments;
    private final PaymentSoporte soporte;
    private final CashSessionRepository cashSessions;

    @Override
    public PaymentDto register(RegisterPaymentDto input) {
        PaymentSoporte.OrderView order = soporte.lockedOrder(input.orderId());
        PaymentMethod method = soporte.method(input.paymentMethodId());
        if (method.isGateway()) {
            throw new PaymentRuleViolation("\"" + method.name() + "\" es una pasarela en línea: inicia el pago con el checkout.");
        }
        if (input.cashSessionId() != null) {
            soporte.rejectThirdPartyCollection(order.id(), "en caja");
            // Cobro en caja: el turno debe estar abierto (se bloquea para serializar con el cierre).
            cashSessions.lockById(input.cashSessionId())
                    .orElseThrow(() -> new NotFoundException("No se encontró el turno de caja " + input.cashSessionId() + "."))
                    .requireOpen();
            java.util.Set<Long> allowed = cashSessions.allowedMethodIds(input.cashSessionId());
            if (allowed != null && !allowed.isEmpty() && !allowed.contains(method.id())) {
                throw new PaymentRuleViolation("\"" + method.name() + "\" no está habilitado en esta caja.");
            }
        }
        soporte.expireStaleCheckouts(order.id());
        OrderPaymentStatus.Balance balance = soporte.balance(order);
        BigDecimal amount = soporte.checkCollectable(input.amount(), balance);
        method.checkAmount(amount);

        String reference = PaymentSoporte.blankToNull(input.reference());
        if (method.requiresReference() && reference == null) {
            throw new PaymentRuleViolation("\"" + method.name() + "\" exige la referencia del pago (voucher, comprobante…).");
        }
        BigDecimal tendered = input.tenderedAmount() == null ? null : input.tenderedAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal change = null;
        if (tendered != null) {
            if (tendered.compareTo(amount) < 0) {
                throw new PaymentRuleViolation("El valor recibido (" + tendered.toPlainString() + ") es menor que el pago.");
            }
            if (!method.allowsChange() && tendered.compareTo(amount) != 0) {
                throw new PaymentRuleViolation("\"" + method.name() + "\" no admite cambio.");
            }
            change = tendered.subtract(amount);
        }

        boolean approved = method.kind().settlesOnRegister() && !Boolean.TRUE.equals(input.pending());
        Instant now = Instant.now();
        Payment payment = new Payment(null, order.id(), method.id(), method.channel(), method.kind(), null,
                approved ? PaymentStatus.APPROVED : PaymentStatus.PENDING, amount, method.feeFor(amount), tendered, change,
                BigDecimal.ZERO, reference, soporte.newPaymentReference(order.id()), null, null, null, null,
                PaymentSoporte.blankToNull(input.notes()), null, approved ? now : null, null, now);
        payment.assignCashSession(input.cashSessionId());
        Payment saved = payments.save(payment);
        soporte.log(saved, PaymentSoporte.SOURCE_PANEL, null, "REGISTERED", null, amount,
                change != null && change.signum() > 0 ? "Cambio: " + change.toPlainString() : null, null);
        if (approved) {
            soporte.settleOrder(order.id());
        }
        return soporte.toDto(saved);
    }
}
