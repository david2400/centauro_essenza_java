package com.essenza.draco.modules.sales.application.input.payment;

import java.util.List;

import com.essenza.draco.modules.sales.application.dto.payment.PaymentDto;
import com.essenza.draco.modules.sales.application.dto.payment.RegisterSplitPaymentDto;

/** Registra en una sola operación el cobro de una orden con varios medios de pago. */
public interface RegisterSplitPaymentUseCase {

    List<PaymentDto> register(RegisterSplitPaymentDto input);
}
