package com.essenza.draco.modules.sales.application.services.order;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.sales.application.dto.order.CreateFullOrderDto;
import com.essenza.draco.modules.sales.application.dto.order.OrderDto;
import com.essenza.draco.modules.sales.application.dto.order.SaveOrderDetailsDto;
import com.essenza.draco.modules.sales.application.dto.product_order.CreateProductOrderDto;
import com.essenza.draco.modules.sales.application.input.order.CreateFullOrderUseCase;
import com.essenza.draco.modules.sales.application.input.order.SaveOrderDetailsUseCase;
import com.essenza.draco.modules.sales.application.output.repository.OrderLedger;
import com.essenza.draco.modules.sales.application.output.repository.OrderLedger.LineState;
import com.essenza.draco.modules.sales.application.output.repository.OrderLedger.OrderDetails;
import com.essenza.draco.modules.sales.application.output.repository.OrderLedger.OrderHeader;
import com.essenza.draco.modules.sales.application.output.repository.OrderReferences;
import com.essenza.draco.modules.sales.application.services.ProductOrderServiceImpl;
import com.essenza.draco.modules.sales.application.services.customer.CustomerSync;
import com.essenza.draco.modules.sales.domain.model.OrderStatus;
import com.essenza.draco.modules.sales.domain.model.order.CustomerInfo;
import com.essenza.draco.modules.sales.domain.model.order.DeliveryInfo;
import com.essenza.draco.modules.sales.domain.model.order.DiscountType;
import com.essenza.draco.modules.sales.domain.model.order.OrderChannel;
import com.essenza.draco.modules.sales.domain.model.order.OrderEventType;
import com.essenza.draco.modules.sales.domain.model.order.OrderPriority;
import com.essenza.draco.modules.sales.domain.model.order.OrderRuleViolation;
import com.essenza.draco.shared.common.inventory.StockReservations;
import com.essenza.draco.shared.common.promotions.Coupons.CouponTerms;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;

/**
 * Alta completa y edición de la cabecera de la orden. Reglas por estado:
 * <ul>
 *   <li>Descuento, costo de envío y bodega: solo mientras la orden está pendiente (cambian el
 *   total o la reserva).</li>
 *   <li>Método de entrega y dirección: hasta que se despacha.</li>
 *   <li>Cliente, origen y notas: mientras no esté entregada ni cancelada.</li>
 * </ul>
 */
@Service
@Transactional
public class OrderDetailsService implements SaveOrderDetailsUseCase, CreateFullOrderUseCase {

    private final OrderLedger ledger;
    private final OrderReferences references;
    private final OrderSoporte soporte;
    private final StockReservations reservations;
    private final ProductOrderServiceImpl lines;
    private final Duration reservationTtl;
    private final CustomerSync customerSync;

    public OrderDetailsService(OrderLedger ledger, OrderReferences references, OrderSoporte soporte,
                               StockReservations reservations, ProductOrderServiceImpl lines,
                               @Value("${essenza.orders.reservation-ttl:PT24H}") Duration reservationTtl,
                               CustomerSync customerSync) {
        this.ledger = ledger;
        this.references = references;
        this.soporte = soporte;
        this.reservations = reservations;
        this.lines = lines;
        this.reservationTtl = reservationTtl;
        this.customerSync = customerSync;
    }

    @Override
    public OrderDto createFull(CreateFullOrderDto input) {
        SaveOrderDetailsDto details = input.details();
        OrderChannel channel = OrderChannel.parse(details.channel());
        if (channel == OrderChannel.POS) {
            throw new OrderRuleViolation("Las ventas de mostrador se registran desde la caja (POS).");
        }
        CustomerInfo customer = customerOf(details.customer(), CustomerInfo.EMPTY);
        customer.requireContact();
        DeliveryInfo delivery = deliveryOf(details.delivery(), DeliveryInfo.NONE);
        delivery.requireShippingAddress();

        Long id = ledger.createOrder(SoporteText.clean(details.complementaryOrder(), 255), channel.name(), null);
        OrderDetails empty = soporte.details(id);
        OrderDetails header = header(empty, details, customer, delivery, channel);
        ledger.saveDetails(id, header);
        customerSync.fromOrder(customer);
        for (CreateFullOrderDto.Line line : input.lines()) {
            lines.create(CreateProductOrderDto.builder().orderId(id).productId(line.productId()).skuId(line.skuId())
                    .quantity(line.quantity()).discount(line.discount()).build(), false);
        }
        if (details.discount() != null) {
            applyDiscount(id, empty, details.discount(), true, false);
        }
        soporte.reprice(id);
        soporte.log(id, OrderEventType.CREATED, null, OrderStatus.PENDING.name(), null, details.actor());
        return soporte.view(id);
    }

    @Override
    public OrderDto saveDetails(Long orderId, SaveOrderDetailsDto input) {
        OrderHeader lock = ledger.lockOrder(orderId).orElseThrow(() -> OrderSoporte.notFound(orderId));
        OrderStatus status = OrderStatus.tryParse(lock.state()).orElse(OrderStatus.PENDING);
        if (status == OrderStatus.DELIVERED || status == OrderStatus.CANCELLED) {
            throw new ConflictException("La orden está " + status + ": ya no se puede editar.");
        }
        OrderDetails current = soporte.details(orderId);
        boolean pending = status == OrderStatus.PENDING;
        boolean shipped = status == OrderStatus.SHIPPED;

        OrderChannel channel = input.channel() == null ? OrderChannel.parse(current.channel()) : OrderChannel.parse(input.channel());
        if (!Objects.equals(channel.name(), current.channel())
                && (OrderChannel.POS.name().equals(current.channel()) || channel == OrderChannel.POS)) {
            throw new OrderRuleViolation("El canal de las ventas de mostrador (POS) no se cambia.");
        }
        CustomerInfo customer = input.customer() == null ? current.customer() : customerOf(input.customer(), current.customer());
        DeliveryInfo delivery = input.delivery() == null ? current.delivery() : deliveryOf(input.delivery(), current.delivery());
        if (!delivery.equals(current.delivery())) {
            if (shipped) {
                throw new ConflictException("La orden ya se despachó: la entrega no se puede cambiar.");
            }
            if (!pending && delivery.shippingAmount().compareTo(current.delivery().shippingAmount()) != 0) {
                throw new ConflictException("El costo de envío solo se cambia mientras la orden está pendiente (cambia el total cobrado).");
            }
        }
        OrderDetails next = header(current, input, customer, delivery, channel);
        if (!Objects.equals(next.warehouseId(), current.warehouseId())) {
            if (!pending) {
                throw new ConflictException("La bodega de despacho solo se cambia mientras la orden está pendiente.");
            }
        }

        List<String> changed = new ArrayList<>();
        if (!customer.equals(current.customer())) {
            changed.add("customer");
        }
        if (!delivery.equals(current.delivery())) {
            changed.add("delivery");
        }
        if (!Objects.equals(next.storeId(), current.storeId()) || !Objects.equals(next.warehouseId(), current.warehouseId())
                || !Objects.equals(next.assignedTo(), current.assignedTo()) || next.priority() != current.priority()
                || !Objects.equals(next.channel(), current.channel())) {
            changed.add("origin");
        }
        if (!Objects.equals(next.internalNotes(), current.internalNotes())
                || !Objects.equals(next.customerNotes(), current.customerNotes())) {
            changed.add("notes");
        }
        if (!Objects.equals(next.complementaryOrder(), current.complementaryOrder())) {
            changed.add("reference");
        }
        ledger.saveDetails(orderId, next);
        if (changed.contains("customer")) {
            customerSync.fromOrder(customer);
        }
        if (!Objects.equals(next.warehouseId(), current.warehouseId()) && lock.stockManaged()) {
            moveReservations(orderId, lock, next.warehouseId());
        }
        boolean discountChanged = input.discount() != null && applyDiscount(orderId, current, input.discount(), pending, true);
        if (pending && (discountChanged || changed.contains("delivery"))) {
            soporte.reprice(orderId);
        }
        if (!changed.isEmpty()) {
            soporte.log(orderId, OrderEventType.EDITED, null, null, String.join(",", changed), input.actor());
        }
        return soporte.view(orderId);
    }

    /** Guarda el descuento si cambió (true) y deja constancia en la línea de tiempo. */
    private boolean applyDiscount(Long orderId, OrderDetails current, SaveOrderDetailsDto.Discount input, boolean pending,
                                  boolean audit) {
        DiscountType type = DiscountType.parse(input.type());
        BigDecimal value = null;
        Long couponId = null;
        String couponCode = null;
        String message;
        switch (type) {
            case NONE -> message = "NONE";
            case PERCENT, AMOUNT -> {
                value = input.value();
                if (value == null || value.signum() <= 0) {
                    throw new OrderRuleViolation("Indica el valor del descuento (mayor que 0).");
                }
                value = value.setScale(2, java.math.RoundingMode.HALF_UP);
                message = type.name() + ":" + value.toPlainString();
            }
            case COUPON -> {
                CouponTerms coupon = soporte.couponByCode(input.couponCode())
                        .orElseThrow(() -> new NotFoundException("No existe el cupón " + SoporteText.clean(input.couponCode(), 50) + "."));
                couponId = coupon.id();
                couponCode = coupon.code();
                message = "COUPON:" + coupon.code();
            }
            default -> throw new IllegalStateException();
        }
        boolean same = type == current.discountType() && Objects.equals(couponId, current.couponId())
                && (value == null ? current.discountValue() == null
                : current.discountValue() != null && value.compareTo(current.discountValue()) == 0);
        if (same) {
            return false;
        }
        if (!pending) {
            throw new ConflictException("El descuento solo se cambia mientras la orden está pendiente (cambia el total cobrado).");
        }
        ledger.saveDiscount(orderId, type, value, couponId, couponCode);
        if (audit) {
            soporte.log(orderId, OrderEventType.DISCOUNT, null, null, message, null);
        }
        return true;
    }

    private void moveReservations(Long orderId, OrderHeader header, Long warehouseId) {
        Instant expires = (header.createdAt() != null ? header.createdAt() : Instant.now()).plus(reservationTtl);
        for (LineState line : ledger.lineStates(orderId)) {
            if (line.skuId() == null) {
                continue;
            }
            reservations.reserveLine(orderId, line.id(), line.productId(), line.skuId(), 0, expires);
            reservations.reserveLine(orderId, line.id(), line.productId(), line.skuId(), line.quantity(), expires, warehouseId);
        }
    }

    private OrderDetails header(OrderDetails current, SaveOrderDetailsDto input, CustomerInfo customer, DeliveryInfo delivery,
                                OrderChannel channel) {
        // Tienda y bodega se reemplazan tal como llegan (null = sin tienda / reparto normal).
        Long storeId = input.storeId();
        if (storeId != null && !Objects.equals(storeId, current.storeId()) && references.store(storeId).isEmpty()) {
            throw new OrderRuleViolation("La tienda " + input.storeId() + " no existe o está inactiva.");
        }
        if (delivery.isPickup() && !Objects.equals(delivery.pickupStoreId(), current.delivery().pickupStoreId())
                && references.store(delivery.pickupStoreId()).isEmpty()) {
            throw new OrderRuleViolation("La tienda de retiro no existe o está inactiva.");
        }
        if (delivery.carrierId() != null && !Objects.equals(delivery.carrierId(), current.delivery().carrierId())
                && !references.carrierActive(delivery.carrierId())) {
            throw new OrderRuleViolation("La transportadora " + delivery.carrierId() + " no existe o está inactiva.");
        }
        Long warehouseId = input.warehouseId();
        if (warehouseId != null && !Objects.equals(warehouseId, current.warehouseId())
                && !references.warehouseActive(input.warehouseId())) {
            throw new OrderRuleViolation("La bodega " + input.warehouseId() + " no existe o está inactiva.");
        }
        return new OrderDetails(current.id(), current.state(), current.orderNumber(), channel.name(), current.cashSessionId(),
                input.complementaryOrder() != null ? SoporteText.clean(input.complementaryOrder(), 255) : current.complementaryOrder(),
                customer, delivery, storeId, warehouseId,
                input.assignedTo() != null ? SoporteText.clean(input.assignedTo(), 120) : current.assignedTo(),
                input.priority() != null ? OrderPriority.parse(input.priority()) : current.priority(),
                current.discountType(), current.discountValue(), current.couponId(), current.couponCode(),
                input.internalNotes() != null ? SoporteText.note(input.internalNotes(), 2000) : current.internalNotes(),
                input.customerNotes() != null ? SoporteText.note(input.customerNotes(), 2000) : current.customerNotes(),
                delivery.carrierId(), current.trackingNumber());
    }

    private static CustomerInfo customerOf(SaveOrderDetailsDto.Customer c, CustomerInfo fallback) {
        if (c == null) {
            return fallback;
        }
        return CustomerInfo.of(c.customerId(), c.kind(), c.docType(), c.docNumber(), c.name(), c.email(), c.phone(),
                c.billingAddress(), c.billingCity());
    }

    private static DeliveryInfo deliveryOf(SaveOrderDetailsDto.Delivery d, DeliveryInfo fallback) {
        if (d == null) {
            return fallback;
        }
        return DeliveryInfo.of(d.method(), d.pickupStoreId(), d.recipientName(), d.recipientPhone(), d.address(),
                d.addressDetail(), d.countryId(), d.stateId(), d.cityId(), d.country(), d.state(), d.city(), d.postalCode(),
                d.notes(), d.carrierId(), d.shippingAmount(), d.promisedDate());
    }
}
