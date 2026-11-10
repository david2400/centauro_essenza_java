package com.essenza.draco.modules.sales.application.output.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.essenza.draco.modules.sales.domain.model.LineTax;
import com.essenza.draco.modules.sales.domain.model.order.CustomerInfo;
import com.essenza.draco.modules.sales.domain.model.order.DeliveryInfo;
import com.essenza.draco.modules.sales.domain.model.order.DiscountType;
import com.essenza.draco.modules.sales.domain.model.order.OrderNumber;
import com.essenza.draco.modules.sales.domain.model.order.OrderPriority;

/** Persistencia de órdenes y líneas para los flujos con stock (Fase 5). */
public interface OrderLedger {

    record OrderHeader(Long id, String state, boolean stockManaged, Instant createdAt) {
    }

    /** Línea con el impuesto congelado ({@code tax} nulo en líneas anteriores a V9 sin recalcular). */
    record OrderLine(Long id, Long orderId, Long productId, Long skuId, String skuCode, String productName,
                     BigDecimal unitPrice, int quantity, BigDecimal discount, BigDecimal subtotal, BigDecimal total,
                     LineTax tax) {

        public OrderLine(Long id, Long orderId, Long productId, Long skuId, String skuCode, String productName,
                         BigDecimal unitPrice, int quantity, BigDecimal discount, BigDecimal subtotal, BigDecimal total) {
            this(id, orderId, productId, skuId, skuCode, productName, unitPrice, quantity, discount, subtotal, total, null);
        }
    }

    /** Carga la orden bloqueándola (serializa cambios de líneas y de estado). */
    Optional<OrderHeader> lockOrder(Long orderId);

    /** Crea una orden PENDING, con total 0, que gestiona stock. */
    default Long createOrder(String complementaryOrder) {
        return createOrder(complementaryOrder, "BACKOFFICE", null);
    }

    /** Crea una orden PENDING en un canal (BACKOFFICE, POS, ONLINE) y, si aplica, ligada a un turno de caja. */
    Long createOrder(String complementaryOrder, String channel, Long cashSessionId);

    void updateComplementary(Long orderId, String complementaryOrder);

    void setState(Long orderId, String state, String cancelReason);

    /** Suma {@code amount} al valor devuelto de la orden (devoluciones reembolsadas). */
    void addReturnedAmount(Long orderId, BigDecimal amount);

    /**
     * Bodega de la que sale una venta de mostrador: la de la caja o la de su punto de venta.
     * Vacío para órdenes que no son de caja (reparto normal).
     */
    Optional<Long> saleWarehouse(Long orderId);

    /** total, impuesto y base = suma de las líneas vigentes. */
    void refreshTotal(Long orderId);

    Optional<OrderLine> findLine(Long lineId);

    List<OrderLine> lines(Long orderId);

    OrderLine saveLine(OrderLine line);

    void deleteLine(Long lineId);

    /** Órdenes con stock gestionado, PENDING y creadas antes de {@code createdBefore}. */
    List<Long> expiredPendingOrders(Instant createdBefore);

    // ── V16: orden empresarial ─────────────────────────────────────────────────

    /** Cabecera editable de la orden (cliente, entrega, origen, descuento y notas). */
    record OrderDetails(Long id, String state, String orderNumber, String channel, Long cashSessionId,
                        String complementaryOrder, CustomerInfo customer, DeliveryInfo delivery, Long storeId,
                        Long warehouseId, String assignedTo, OrderPriority priority, DiscountType discountType,
                        BigDecimal discountValue, Long couponId, String couponCode, String internalNotes,
                        String customerNotes, Long carrierId, String trackingNumber) {
    }

    /** Línea con lo necesario para precios y preparación. */
    record LineState(Long id, Long productId, Long skuId, String label, int quantity, BigDecimal subtotal,
                     BigDecimal discount, BigDecimal orderDiscount, BigDecimal total, int picked, LineTax tax) {
    }

    /** Precio final de una línea tras repartir el descuento global. */
    record LinePrice(BigDecimal orderDiscount, BigDecimal total, LineTax tax) {
    }

    Optional<OrderDetails> details(Long orderId);

    /** Guarda cliente, entrega, origen, notas y canal (no toca estado, líneas ni descuento). */
    void saveDetails(Long orderId, OrderDetails details);

    void saveCustomer(Long orderId, CustomerInfo customer);

    void saveDiscount(Long orderId, DiscountType type, BigDecimal value, Long couponId, String couponCode);

    List<LineState> lineStates(Long orderId);

    /** Aplica el reparto del descuento a las líneas y guarda el descuento total y el del envío. */
    void applyPricing(Long orderId, Map<Long, LinePrice> lines, BigDecimal discountAmount, BigDecimal shippingDiscount);

    void setPicked(Long lineId, int picked);

    void setShipment(Long orderId, Long carrierId, String trackingNumber);

    /** Numeración bloqueada (para tomar el siguiente número sin repetir). */
    OrderNumber lockNumbering();

    void saveNumbering(OrderNumber numbering);

    boolean orderNumberExists(String orderNumber);

    // ── V19: compras multivendedor ─────────────────────────────────────────────

    /**
     * Compra y vendedor de la orden. {@code thirdParty} = subpedido de un vendedor externo: vende
     * su oferta (sin bodega propia), la cobra y la factura ese vendedor.
     */
    record MarketplaceLink(Long purchaseId, Long sellerId, boolean thirdParty) {
    }

    /**
     * Subpedido de vendedor externo cuyas líneas salen todas de consignación (oferta con despacho
     * de la plataforma): lo cobra la tienda y lo liquida al vendedor.
     */
    default boolean consignmentSale(Long orderId) {
        return false;
    }

    /**
     * La tienda cobra el subpedido (en línea o en caja) y luego le liquida al vendedor. Siempre en
     * consignación; para los demás subpedidos externos, según {@code essenza.marketplace.collection}
     * (STORE, por defecto, o SELLER: el comprador le paga al vendedor por fuera).
     */
    default boolean storeCollects(Long orderId) {
        return consignmentSale(orderId);
    }

    /** Vacío si la orden no pertenece a una compra multivendedor. */
    Optional<MarketplaceLink> marketplaceLink(Long orderId);
}
