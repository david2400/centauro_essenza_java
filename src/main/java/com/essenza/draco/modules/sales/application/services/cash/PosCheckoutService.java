package com.essenza.draco.modules.sales.application.services.cash;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.sales.application.dto.cash.PosCheckoutDto;
import com.essenza.draco.modules.sales.application.dto.cash.PosCheckoutResultDto;
import com.essenza.draco.modules.sales.application.dto.order.OrderDto;
import com.essenza.draco.modules.sales.application.dto.payment.PaymentDto;
import com.essenza.draco.modules.sales.application.dto.payment.RegisterPaymentDto;
import com.essenza.draco.modules.sales.application.dto.product_order.CreateProductOrderDto;
import com.essenza.draco.modules.sales.application.input.cash.PosCheckoutUseCase;
import com.essenza.draco.modules.sales.application.input.order.FindOrderByIdUseCase;
import com.essenza.draco.modules.sales.application.input.payment.RegisterPaymentUseCase;
import com.essenza.draco.modules.sales.application.input.product_order.CreateProductOrderUseCase;
import com.essenza.draco.modules.sales.application.output.repository.OrderLedger;
import com.essenza.draco.modules.sales.domain.model.cash.CashRuleViolation;
import com.essenza.draco.modules.sales.domain.model.cash.CashSession;
import com.essenza.draco.shared.exceptions.NotFoundException;

/**
 * Venta de mostrador en una transacción: orden POS ligada al turno, líneas por SKU (reservan
 * stock con el precio vigente) y pagos del turno. Si los pagos cubren el total la orden queda
 * pagada (descuenta stock y, si está activa, se factura sola). Cualquier error deshace todo.
 */
@Service
@Transactional
public class PosCheckoutService implements PosCheckoutUseCase {

    private final CashSoporte soporte;
    private final OrderLedger ledger;
    private final CreateProductOrderUseCase createLine;
    private final RegisterPaymentUseCase registerPayment;
    private final FindOrderByIdUseCase findOrder;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final com.essenza.draco.modules.sales.application.output.repository.OrderEventLog orderEvents;
    private final com.essenza.draco.modules.sales.application.services.customer.CustomerSync customerSync;

    public PosCheckoutService(CashSoporte soporte, OrderLedger ledger, CreateProductOrderUseCase createLine,
                              RegisterPaymentUseCase registerPayment, FindOrderByIdUseCase findOrder,
                              org.springframework.context.ApplicationEventPublisher events,
                              com.essenza.draco.modules.sales.application.output.repository.OrderEventLog orderEvents,
                              com.essenza.draco.modules.sales.application.services.customer.CustomerSync customerSync) {
        this.soporte = soporte;
        this.ledger = ledger;
        this.createLine = createLine;
        this.registerPayment = registerPayment;
        this.findOrder = findOrder;
        this.events = events;
        this.orderEvents = orderEvents;
        this.customerSync = customerSync;
    }

    @Override
    public PosCheckoutResultDto checkout(PosCheckoutDto input) {
        CashSession session = soporte.session(input.cashSessionId());
        session.requireOpen();
        if (input.items() == null || input.items().isEmpty()) {
            throw new CashRuleViolation("Agrega al menos un producto a la venta.");
        }
        if (input.payments() == null || input.payments().isEmpty()) {
            throw new CashRuleViolation("Registra al menos un pago.");
        }
        com.essenza.draco.modules.sales.domain.model.cash.CashRegister register = soporte.register(session.registerId());
        com.essenza.draco.modules.sales.domain.model.cash.RegisterSettings rules = register.settings();
        for (PosCheckoutDto.PaymentLine line : input.payments()) {
            if (!rules.allows(line.paymentMethodId())) {
                throw new CashRuleViolation("El medio de pago " + line.paymentMethodId() + " no está habilitado en la caja "
                        + register.name() + ".");
            }
        }
        Long orderId = ledger.createOrder(input.reference(), "POS", session.id());
        PosCheckoutDto.Buyer posBuyer = input.buyer();
        if (posBuyer != null && posBuyer.docNumber() != null && !posBuyer.docNumber().isBlank()) {
            try {
                var buyerInfo = com.essenza.draco.modules.sales.domain.model.order.CustomerInfo.of(null, null,
                        posBuyer.docType(), posBuyer.docNumber(), posBuyer.name(), posBuyer.email(), posBuyer.phone(), null, null);
                ledger.saveCustomer(orderId, buyerInfo);
                customerSync.fromOrder(buyerInfo);
            } catch (com.essenza.draco.modules.sales.domain.model.order.OrderRuleViolation ignored) {
                // Datos del comprador incompletos: la venta sigue; la factura usa lo que llegó en el evento.
            }
        }
        orderEvents.append(orderId, com.essenza.draco.modules.sales.domain.model.order.OrderEventType.CREATED, null, "PENDING",
                null, null);
        for (PosCheckoutDto.Item item : input.items()) {
            var created = createLine.create(CreateProductOrderDto.builder()
                    .orderId(orderId)
                    .productId(item.productId())
                    .skuId(item.skuId())
                    .quantity(item.quantity())
                    .discount(item.discount())
                    .build());
            rules.checkDiscount(created.getProductName() == null ? "Línea " + created.getId() : created.getProductName(),
                    created.getSubtotal(), created.getDiscount());
        }
        OrderDto order = order(orderId);
        BigDecimal paid = input.payments().stream().map(PosCheckoutDto.PaymentLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (paid.compareTo(order.getTotal()) != 0) {
            throw new CashRuleViolation("Los pagos (" + paid.toPlainString() + ") no cuadran con el total de la venta ("
                    + order.getTotal().toPlainString() + ").");
        }
        List<PaymentDto> payments = new ArrayList<>();
        BigDecimal change = BigDecimal.ZERO;
        for (PosCheckoutDto.PaymentLine line : input.payments()) {
            PaymentDto payment = registerPayment.register(new RegisterPaymentDto(orderId, line.paymentMethodId(), line.amount(),
                    line.tenderedAmount(), line.reference(), null, false, session.id()));
            payments.add(payment);
            if (payment.changeAmount() != null) {
                change = change.add(payment.changeAmount());
            }
        }
        order = order(orderId);
        PosCheckoutDto.Buyer buyer = input.buyer();
        if ("PAID".equals(order.getState())) {
            // Facturación escucha tras el commit: factura si la caja (o la configuración) lo pide.
            events.publishEvent(new com.essenza.draco.shared.common.sales.PosSaleCompletedEvent(orderId, register.id(),
                    rules.autoInvoice(), buyer == null ? null : buyer.docType(), buyer == null ? null : buyer.docNumber(),
                    buyer == null ? null : buyer.name(), buyer == null ? null : buyer.email(),
                    buyer == null ? null : buyer.phone()));
        }
        return new PosCheckoutResultDto(orderId, order.getState(), order.getTotal(), order.getTaxAmount(), order.getNetAmount(),
                change, payments, rules.autoInvoice());
    }

    private OrderDto order(Long id) {
        return findOrder.findById(id).orElseThrow(() -> new NotFoundException("No se encontró la orden " + id + "."));
    }
}
