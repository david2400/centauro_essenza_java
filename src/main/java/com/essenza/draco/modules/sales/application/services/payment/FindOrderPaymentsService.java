package com.essenza.draco.modules.sales.application.services.payment;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.essenza.draco.modules.sales.application.dto.payment.OrderPaymentsDto;
import com.essenza.draco.modules.sales.application.input.payment.FindOrderPaymentsUseCase;
import com.essenza.draco.modules.sales.application.output.repository.PaymentRepository;
import com.essenza.draco.modules.sales.domain.model.payment.OrderPaymentStatus;
import com.essenza.draco.modules.sales.domain.model.payment.Payment;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FindOrderPaymentsService implements FindOrderPaymentsUseCase {

    private final PaymentRepository payments;
    private final PaymentSoporte soporte;

    @Override
    public OrderPaymentsDto forOrder(Long orderId) {
        PaymentSoporte.OrderView order = soporte.order(orderId);
        List<Payment> list = payments.findByOrderId(orderId);
        OrderPaymentStatus.Balance balance = OrderPaymentStatus.of(order.total(), order.returned(), list);
        return new OrderPaymentsDto(order.id(), order.state().name(), balance.total(), balance.settled(),
                balance.pending(), balance.balance(), balance.collectable(), balance.status().name(), soporte.toDtos(list),
                order.returned(), soporte.sellerCollects(order.id()));
    }
}
