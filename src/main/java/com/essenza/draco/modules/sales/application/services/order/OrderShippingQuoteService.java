package com.essenza.draco.modules.sales.application.services.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.essenza.draco.modules.sales.application.output.repository.OrderShipmentLines;
import com.essenza.draco.modules.sales.domain.model.order.OrderRuleViolation;
import com.essenza.draco.shared.common.logistics.ShippingQuotes;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.MenuAccess;
import com.essenza.draco.shared.security.Menus;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Opciones de envío para una orden del panel: el destino (ids del catálogo), las líneas (peso y medidas del catálogo),
 * la bodega que despacha y el subtotal. No guarda nada: al elegir una opción, la orden guarda transportadora, valor
 * y fecha prometida como hasta ahora, así un cambio posterior del tarifario no la altera.
 */
@Service
public class OrderShippingQuoteService {

    static final int MAX_LINES = 200;

    private final ShippingQuotes quotes;
    private final OrderShipmentLines orderLines;
    private final ActorProvider actors;
    private final MenuAccess menus;

    /** Se cotiza desde la orden, el despacho o el tarifario: basta con tener asignado uno de esos menús. */
    static final String[] MENUS = {Menus.ORDERS, Menus.DISPATCHES, Menus.SHIPPING_COSTS};

    public OrderShippingQuoteService(ShippingQuotes quotes, OrderShipmentLines orderLines, ActorProvider actors, MenuAccess menus) {
        this.quotes = quotes;
        this.orderLines = orderLines;
        this.actors = actors;
        this.menus = menus;
    }

    public record Line(Long skuId, Long productId, Integer quantity) {
    }

    /**
     * {@code declaredValue} vacío = el subtotal (base del seguro). Sin líneas y con {@code orderId}, se usan las líneas
     * guardadas de esa orden.
     */
    public record Request(Long orderId, Long warehouseId, Long countryId, Long stateId, Long cityId, String destinationLabel, List<Line> lines,
                          BigDecimal subtotal, BigDecimal declaredValue, Long carrierId) {
    }

    public ShippingQuotes.QuoteResult quote(Request in) {
        RequestActor actor = actors.current();
        menus.requireAny(MENUS);
        if (in == null || in.countryId() == null || in.stateId() == null || in.cityId() == null) {
            throw new OrderRuleViolation("Para cotizar el envío elige país, departamento y ciudad de destino.");
        }
        List<Line> lines = in.lines() == null ? List.of() : in.lines();
        if (lines.isEmpty() && in.orderId() != null) {
            lines = orderLines.lines(in.orderId()).stream().map(l -> new Line(l.skuId(), l.productId(), l.quantity())).toList();
        }
        if (lines.size() > MAX_LINES) {
            throw new OrderRuleViolation("Demasiadas líneas para cotizar (máximo " + MAX_LINES + ").");
        }
        BigDecimal subtotal = in.subtotal() == null || in.subtotal().signum() < 0 ? BigDecimal.ZERO : in.subtotal();
        List<ShippingQuotes.Item> items = lines.stream().filter(Objects::nonNull)
                .filter(l -> l.quantity() != null && l.quantity() > 0 && (l.skuId() != null || l.productId() != null))
                .map(l -> new ShippingQuotes.Item(l.skuId(), l.productId(), l.quantity())).toList();
        return quotes.quote(actor.companyId(), new ShippingQuotes.QuoteRequest(in.warehouseId(), in.countryId(), in.stateId(),
                in.cityId(), in.destinationLabel(), items, in.declaredValue() == null ? subtotal : in.declaredValue(), subtotal, null,
                in.carrierId()));
    }
}
