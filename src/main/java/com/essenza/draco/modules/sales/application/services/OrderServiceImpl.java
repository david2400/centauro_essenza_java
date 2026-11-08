package com.essenza.draco.modules.sales.application.services;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.sales.application.dto.order.ChangeOrderStatusDto;
import com.essenza.draco.modules.sales.application.dto.order.CreateOrderDto;
import com.essenza.draco.modules.sales.application.dto.order.OrderDto;
import com.essenza.draco.modules.sales.application.dto.order.OrderReservationDto;
import com.essenza.draco.modules.sales.application.dto.order.UpdateOrderDto;
import com.essenza.draco.modules.sales.application.input.order.ChangeOrderStatusUseCase;
import com.essenza.draco.modules.sales.application.input.order.CreateOrderUseCase;
import com.essenza.draco.modules.sales.application.input.order.DeleteOrderUseCase;
import com.essenza.draco.modules.sales.application.input.order.ExpireOrderReservationsUseCase;
import com.essenza.draco.modules.sales.application.input.order.FindOrderByIdUseCase;
import com.essenza.draco.modules.sales.application.input.order.FindOrderReservationsUseCase;
import com.essenza.draco.modules.sales.application.input.order.FindOrdersUseCase;
import com.essenza.draco.modules.sales.application.input.order.UpdateOrderUseCase;
import com.essenza.draco.modules.sales.application.output.repository.OrderLedger;
import com.essenza.draco.modules.sales.application.output.repository.OrderLedger.OrderHeader;
import com.essenza.draco.modules.sales.application.output.repository.OrderRepository;
import com.essenza.draco.modules.sales.application.services.order.OrderSoporte;
import com.essenza.draco.modules.sales.application.services.order.OrderTransitions;
import com.essenza.draco.modules.sales.domain.model.OrderStatus;
import com.essenza.draco.modules.sales.domain.model.order.OrderEventType;
import com.essenza.draco.shared.common.inventory.StockReservations;
import com.essenza.draco.shared.common.marketplace.OfferStock;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;

/**
 * Órdenes con stock (Fase 5): se crean PENDING; las líneas reservan stock; al pagar
 * se descuenta; al cancelar se libera la reserva o se devuelve lo descontado.
 * Las órdenes anteriores a la Fase 5 ({@code stock_managed = false}) no tocan el stock.
 */
@Service
@Transactional
public class OrderServiceImpl implements CreateOrderUseCase,
        UpdateOrderUseCase,
        DeleteOrderUseCase,
        FindOrderByIdUseCase,
        FindOrdersUseCase,
        ChangeOrderStatusUseCase,
        ExpireOrderReservationsUseCase,
        FindOrderReservationsUseCase {

    static final String EXPIRED_REASON = "Reserva vencida: la orden no se pagó a tiempo.";

    private final OrderRepository orderRepository;
    private final OrderLedger ledger;
    private final StockReservations reservations;
    private final Duration reservationTtl;
    private final OrderTransitions transitions;
    private final OrderSoporte soporte;
    private final OfferStock offers;

    public OrderServiceImpl(OrderRepository orderRepository, OrderLedger ledger, StockReservations reservations,
                            @Value("${essenza.orders.reservation-ttl:PT24H}") Duration reservationTtl,
                            OrderTransitions transitions, OrderSoporte soporte, OfferStock offers) {
        this.orderRepository = orderRepository;
        this.ledger = ledger;
        this.reservations = reservations;
        this.reservationTtl = reservationTtl;
        this.transitions = transitions;
        this.soporte = soporte;
        this.offers = offers;
    }

    @Override
    public OrderDto create(CreateOrderDto input) {
        if (input.getState() != null && !input.getState().isBlank()
                && OrderStatus.tryParse(input.getState()).orElse(null) != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Las órdenes se crean pendientes (PENDING); cambia el estado cuando tenga líneas.");
        }
        Long id = ledger.createOrder(input.getComplementaryOrder());
        soporte.log(id, OrderEventType.CREATED, null, OrderStatus.PENDING.name(), null, null);
        return view(id);
    }

    @Override
    public OrderDto update(Long id, UpdateOrderDto input) {
        OrderHeader header = header(id);
        ledger.updateComplementary(id, input.getComplementaryOrder());
        soporte.log(id, OrderEventType.EDITED, null, null, "reference", null);
        if (input.getState() != null && !input.getState().isBlank() && !input.getState().equalsIgnoreCase(header.state())) {
            return changeStatus(id, new ChangeOrderStatusDto(input.getState(), null));
        }
        return view(id);
    }

    @Override
    public OrderDto changeStatus(Long id, ChangeOrderStatusDto input) {
        transitions.apply(id, OrderStatus.parse(input.state()), input.reason(), OrderEventType.STATUS, null, null);
        return view(id);
    }

    @Override
    public boolean deleteById(Long id) {
        if (orderRepository.findById(id).isEmpty()) {
            throw new NotFoundException("No se encontró la orden " + id + ".");
        }
        if (orderRepository.hasDependents(id)) {
            throw new ConflictException("No se puede eliminar la orden " + id
                    + ": tiene despachos o devoluciones asociados.");
        }
        OrderHeader header = header(id);
        if (ledger.marketplaceLink(id).isPresent()) {
            throw new ConflictException("La orden " + id + " es un subpedido de una compra multivendedor: cancélala en "
                    + "lugar de eliminarla.");
        }
        if (header.stockManaged()) {
            OrderStatus status = OrderStatus.tryParse(header.state()).orElse(OrderStatus.PENDING);
            if (status == OrderStatus.PENDING) {
                reservations.releaseOrder(id, false);
            } else if (status != OrderStatus.CANCELLED) {
                throw new ConflictException("Solo se pueden eliminar órdenes pendientes o canceladas: la orden " + id
                        + " está " + status + ". Cancélala primero.");
            }
        }
        return orderRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderDto> findById(Long id) {
        return soporte.find(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDto> findAll() {
        return orderRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderReservationDto> reservations(Long orderId) {
        if (orderRepository.findById(orderId).isEmpty()) {
            throw new NotFoundException("No se encontró la orden " + orderId + ".");
        }
        return reservations.reservations(orderId).stream()
                .map(line -> new OrderReservationDto(line.id(), line.lineId(), line.skuId(), line.warehouseId(),
                        line.quantity(), line.status(), line.expiresAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findExpired() {
        return ledger.expiredPendingOrders(Instant.now().minus(reservationTtl));
    }

    @Override
    public boolean cancelExpired(Long orderId) {
        Optional<OrderHeader> header = ledger.lockOrder(orderId);
        if (header.isEmpty() || !header.get().stockManaged()
                || OrderStatus.tryParse(header.get().state()).orElse(null) != OrderStatus.PENDING) {
            return false;
        }
        reservations.releaseOrder(orderId, true);
        offers.releaseOrder(orderId, true);
        ledger.setState(orderId, OrderStatus.CANCELLED.name(), EXPIRED_REASON);
        soporte.log(orderId, OrderEventType.STATUS, OrderStatus.PENDING.name(), OrderStatus.CANCELLED.name(), EXPIRED_REASON,
                null);
        return true;
    }

    private OrderHeader header(Long id) {
        return ledger.lockOrder(id).orElseThrow(() -> new NotFoundException("No se encontró la orden " + id + "."));
    }

    private OrderDto view(Long id) {
        return soporte.view(id);
    }

    static OrderDto withNextStates(OrderDto dto) {
        dto.setNextStates(OrderStatus.tryParse(dto.getState())
                .map(status -> status.next().stream().map(Enum::name).toList())
                .orElseGet(() -> java.util.Arrays.stream(OrderStatus.values()).map(Enum::name).toList()));
        return dto;
    }
}
