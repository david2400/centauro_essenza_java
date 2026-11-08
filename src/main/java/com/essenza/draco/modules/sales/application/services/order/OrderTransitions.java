package com.essenza.draco.modules.sales.application.services.order;

import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.essenza.draco.modules.sales.application.output.repository.OrderLedger;
import com.essenza.draco.modules.sales.application.output.repository.OrderLedger.LineState;
import com.essenza.draco.modules.sales.application.output.repository.OrderLedger.OrderDetails;
import com.essenza.draco.modules.sales.application.output.repository.OrderLedger.OrderHeader;
import com.essenza.draco.modules.sales.application.output.repository.OrderRepository;
import com.essenza.draco.modules.sales.domain.model.OrderStatus;
import com.essenza.draco.modules.sales.domain.model.order.Fulfillment;
import com.essenza.draco.modules.sales.domain.model.order.Fulfillment.PickLine;
import com.essenza.draco.modules.sales.domain.model.order.OrderEventType;
import com.essenza.draco.modules.sales.domain.model.order.OrderRuleViolation;
import com.essenza.draco.shared.common.inventory.StockReservations;
import com.essenza.draco.shared.common.marketplace.OfferStock;
import com.essenza.draco.shared.common.marketplace.SellerCommissions;
import com.essenza.draco.shared.common.sales.OrderPaidEvent;
import com.essenza.draco.shared.exceptions.ConflictException;

/**
 * Cambio de estado de una orden con todas sus consecuencias: stock (reserva, descuento o
 * devolución), preparación exigida para quedar lista o despacharse, uso del cupón, fechas
 * de pago/despacho/entrega/cancelación, evento de pago y línea de tiempo.
 */
@Component
public class OrderTransitions {

    private final OrderLedger ledger;
    private final OrderRepository orders;
    private final StockReservations reservations;
    private final ApplicationEventPublisher publisher;
    private final OrderSoporte soporte;
    private final OfferStock offers;
    private final SellerCommissions commissions;

    public OrderTransitions(OrderLedger ledger, OrderRepository orders, StockReservations reservations,
                            ApplicationEventPublisher publisher, OrderSoporte soporte, OfferStock offers,
                            SellerCommissions commissions) {
        this.ledger = ledger;
        this.orders = orders;
        this.reservations = reservations;
        this.publisher = publisher;
        this.soporte = soporte;
        this.offers = offers;
        this.commissions = commissions;
    }

    /** Estado de la orden bloqueada (para validar antes de actuar). */
    public OrderHeader lock(Long orderId) {
        return ledger.lockOrder(orderId).orElseThrow(() -> OrderSoporte.notFound(orderId));
    }

    /**
     * @param type    STATUS para cambios genéricos; SHIPPED o DELIVERED desde despacho y entrega
     * @param message motivo o nota que queda en la línea de tiempo
     * @return true si el estado cambió
     */
    public boolean apply(Long orderId, OrderStatus target, String reason, OrderEventType type, String message, String actor) {
        OrderHeader header = lock(orderId);
        Optional<OrderStatus> current = OrderStatus.tryParse(header.state());
        if (current.isPresent() && current.get() == target) {
            return false;
        }
        if (current.isPresent() && !current.get().canTransitionTo(target)) {
            throw new ConflictException("No se puede pasar una orden de " + current.get() + " a " + target + ".");
        }
        if (target == OrderStatus.CANCELLED && current.isPresent() && current.get().stockCommitted()
                && orders.hasReturns(orderId)) {
            // Cancelar repondría todo el stock otra vez: lo devuelto ya se gestiona en la devolución.
            throw new ConflictException("La orden " + orderId + " tiene devoluciones: gestiona el reintegro desde "
                    + "Devoluciones en lugar de cancelarla.");
        }
        OrderDetails details = soporte.details(orderId);
        if (target == OrderStatus.SHIPPED && details.delivery().isPickup()) {
            throw new OrderRuleViolation("La orden se retira en tienda: márcala como lista y luego como entregada.");
        }
        if (target == OrderStatus.DELIVERED && current.orElse(null) == OrderStatus.READY && !details.delivery().isPickup()) {
            throw new OrderRuleViolation("Los envíos se despachan antes de marcarse como entregados.");
        }
        if (target == OrderStatus.READY || target == OrderStatus.SHIPPED) {
            List<LineState> lines = ledger.lineStates(orderId);
            if (!lines.isEmpty()) {
                Fulfillment.requireAllPicked(lines.stream().map(OrderTransitions::pickLine).toList());
            }
        }
        if (header.stockManaged() && current.isPresent()) {
            applyStock(orderId, current.get(), target);
        }
        if (current.isPresent()) {
            // Marketplace (V19): las líneas de ofertas reservan la cantidad de la oferta, no la bodega.
            applyOfferStock(orderId, current.get(), target);
        }
        ledger.setState(orderId, target.name(), target == OrderStatus.CANCELLED ? OrderSoporte.blankToNull(reason) : null);
        // Marketplace (fase 4): la comisión del vendedor externo sigue el estado de su subpedido.
        ledger.marketplaceLink(orderId).filter(OrderLedger.MarketplaceLink::thirdParty).ifPresent(link ->
                commissions.orderChanged(saleChange(orderId, link, details, current.map(Enum::name).orElse(header.state()),
                        target)));
        if (target == OrderStatus.PAID) {
            soporte.redeemCoupon(details.couponId(), 1);
            // Facturación escucha tras el commit (factura automática si está activada).
            publisher.publishEvent(new OrderPaidEvent(orderId));
        } else if (target == OrderStatus.CANCELLED && current.map(OrderStatus::stockCommitted).orElse(false)) {
            soporte.redeemCoupon(details.couponId(), -1);
        }
        String note = message != null ? message : reason;
        soporte.log(orderId, type == null ? OrderEventType.STATUS : type, current.map(Enum::name).orElse(header.state()),
                target.name(), note, actor);
        return true;
    }

    private void applyStock(Long id, OrderStatus from, OrderStatus to) {
        if (to == OrderStatus.PAID) {
            if (ledger.lines(id).isEmpty()) {
                throw new ConflictException("La orden " + id + " no tiene líneas: agrega productos antes de marcarla como pagada.");
            }
            reservations.commitOrder(id);
        } else if (to == OrderStatus.CANCELLED) {
            if (from == OrderStatus.PENDING) {
                reservations.releaseOrder(id, false);
            } else if (from.stockCommitted()) {
                reservations.restockOrder(id);
            }
        }
    }

    /**
     * Importes de la venta sin impuestos (los precios de la tienda los incluyen): base gravable de
     * las líneas, descuentos llevados a la misma proporción, envío e impuestos cobrados.
     */
    private SellerCommissions.SaleChange saleChange(Long orderId, OrderLedger.MarketplaceLink link, OrderDetails details,
                                                    String from, OrderStatus target) {
        java.math.BigDecimal zero = java.math.BigDecimal.ZERO;
        java.math.BigDecimal taxes = zero;
        java.math.BigDecimal netExcl = zero;
        java.math.BigDecimal netIncl = zero;
        java.math.BigDecimal discountIncl = zero;
        for (LineState line : ledger.lineStates(orderId)) {
            java.math.BigDecimal total = OrderSoporte.zero(line.total());
            netIncl = netIncl.add(total);
            discountIncl = discountIncl.add(OrderSoporte.zero(line.discount())).add(OrderSoporte.zero(line.orderDiscount()));
            if (line.tax() != null && line.tax().base() != null) {
                netExcl = netExcl.add(line.tax().base());
                taxes = taxes.add(OrderSoporte.zero(line.tax().amount()));
            } else {
                netExcl = netExcl.add(total);
            }
        }
        java.math.BigDecimal discountExcl = netIncl.signum() > 0
                ? discountIncl.multiply(netExcl).divide(netIncl, 2, java.math.RoundingMode.HALF_UP) : zero;
        java.math.BigDecimal shipping = details.delivery() == null ? zero : OrderSoporte.zero(details.delivery().shippingAmount());
        return new SellerCommissions.SaleChange(orderId, link.purchaseId(), link.sellerId(), details.orderNumber(),
                target.name(), from, "COP", netExcl.add(discountExcl), discountExcl, shipping, taxes, java.time.Instant.now());
    }

    private void applyOfferStock(Long id, OrderStatus from, OrderStatus to) {
        if (to == OrderStatus.PAID) {
            offers.commitOrder(id);
        } else if (to == OrderStatus.CANCELLED) {
            if (from == OrderStatus.PENDING) {
                offers.releaseOrder(id, false);
            } else if (from.stockCommitted()) {
                offers.restockOrder(id);
            }
        }
    }

    static PickLine pickLine(LineState line) {
        return new PickLine(line.id(), line.label(), line.quantity(), line.picked());
    }
}
