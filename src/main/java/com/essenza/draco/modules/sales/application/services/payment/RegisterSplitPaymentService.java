package com.essenza.draco.modules.sales.application.services.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.sales.application.dto.payment.PaymentDto;
import com.essenza.draco.modules.sales.application.dto.payment.RegisterPaymentDto;
import com.essenza.draco.modules.sales.application.dto.payment.RegisterSplitPaymentDto;
import com.essenza.draco.modules.sales.application.input.payment.RegisterPaymentUseCase;
import com.essenza.draco.modules.sales.application.input.payment.RegisterSplitPaymentUseCase;
import com.essenza.draco.modules.sales.domain.model.payment.OrderPaymentStatus;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentRuleViolation;

/**
 * Cobro con varios medios: cada parte se registra con las mismas reglas que un pago suelto (medio activo, no pasarela,
 * referencia, cambio, caja abierta…), pero todo en una transacción: si una parte falla no queda ninguna registrada.
 * La suma de las partes no puede superar lo que falta por cobrar.
 */
@Service
@Transactional
public class RegisterSplitPaymentService implements RegisterSplitPaymentUseCase {

    private final RegisterPaymentUseCase single;
    private final PaymentSoporte soporte;

    public RegisterSplitPaymentService(RegisterPaymentUseCase single, PaymentSoporte soporte) {
        this.single = single;
        this.soporte = soporte;
    }

    @Override
    public List<PaymentDto> register(RegisterSplitPaymentDto input) {
        PaymentSoporte.OrderView order = soporte.lockedOrder(input.orderId());
        soporte.expireStaleCheckouts(order.id());
        OrderPaymentStatus.Balance balance = soporte.balance(order);
        BigDecimal total = input.lines().stream().map(line -> line.amount().setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Valida la suma antes de registrar nada, para que el mensaje hable del total y no de una parte.
        soporte.checkCollectable(total, balance);

        List<PaymentDto> saved = new ArrayList<>();
        int index = 0;
        for (RegisterSplitPaymentDto.Line line : input.lines()) {
            index++;
            try {
                saved.add(single.register(new RegisterPaymentDto(order.id(), line.paymentMethodId(), line.amount(),
                        line.tenderedAmount(), line.reference(), input.notes(), line.pending(), input.cashSessionId())));
            } catch (PaymentRuleViolation ex) {
                throw new PaymentRuleViolation("Medio " + index + ": " + ex.getMessage());
            }
        }
        return List.copyOf(saved);
    }
}
