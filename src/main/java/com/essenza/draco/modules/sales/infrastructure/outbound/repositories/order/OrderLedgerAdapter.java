package com.essenza.draco.modules.sales.infrastructure.outbound.repositories.order;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.sales.application.output.repository.OrderLedger;
import com.essenza.draco.modules.sales.domain.model.LineTax;
import com.essenza.draco.modules.sales.domain.model.order.CustomerInfo;
import com.essenza.draco.modules.sales.domain.model.order.CustomerKind;
import com.essenza.draco.modules.sales.domain.model.order.DeliveryInfo;
import com.essenza.draco.modules.sales.domain.model.order.DeliveryMethod;
import com.essenza.draco.modules.sales.domain.model.order.DiscountType;
import com.essenza.draco.modules.sales.domain.model.order.OrderNumber;
import com.essenza.draco.modules.sales.domain.model.order.OrderPriority;
import com.essenza.draco.modules.sales.infrastructure.outbound.persistence.mysql.shop.OrderEntity;
import com.essenza.draco.modules.sales.infrastructure.outbound.persistence.mysql.shop.ProductOrderEntity;
import com.essenza.draco.modules.sales.infrastructure.outbound.repositories.product_order.JpaProductOrderRepository;
import com.essenza.draco.shared.exceptions.NotFoundException;

import jakarta.persistence.EntityManager;

@Repository
public class OrderLedgerAdapter implements OrderLedger {

    private final JpaOrderRepository orders;
    private final JpaProductOrderRepository lines;
    private final EntityManager em;

    /** STORE: la tienda cobra los subpedidos externos y le liquida al vendedor; SELLER: los cobra el vendedor. */
    @org.springframework.beans.factory.annotation.Value("${essenza.marketplace.collection:STORE}")
    private String collection = "STORE";

    public OrderLedgerAdapter(JpaOrderRepository orders, JpaProductOrderRepository lines, EntityManager em) {
        this.orders = orders;
        this.lines = lines;
        this.em = em;
    }

    @Override
    public Optional<OrderHeader> lockOrder(Long orderId) {
        return orders.findForUpdate(orderId).map(order -> new OrderHeader(order.getId(), order.getState(),
                Boolean.TRUE.equals(order.getStockManaged()), order.getCreatedAt()));
    }

    @Override
    public Long createOrder(String complementaryOrder, String channel, Long cashSessionId) {
        OrderEntity order = new OrderEntity();
        order.setChannel(channel == null || channel.isBlank() ? "BACKOFFICE" : channel);
        order.setCashSessionId(cashSessionId);
        order.setTaxAmount(BigDecimal.ZERO.setScale(2));
        order.setNetAmount(BigDecimal.ZERO.setScale(2));
        order.setComplementaryOrder(complementaryOrder == null || complementaryOrder.isBlank() ? null : complementaryOrder.trim());
        order.setState("PENDING");
        order.setTotal(BigDecimal.ZERO.setScale(2));
        order.setStockManaged(true);
        order.setOrderNumber(takeNumber());
        if ("POS".equals(order.getChannel())) {
            order.setDeliveryMethod("PICKUP");
        }
        if (cashSessionId != null) {
            // Venta de caja: la tienda de la caja es el origen y el lugar de entrega.
            em.createNativeQuery("""
                            SELECT r.store_id FROM cash_sessions s
                              JOIN cash_registers r ON r.id_cash_register = s.cash_register_id
                             WHERE s.id_cash_session = :id""")
                    .setParameter("id", cashSessionId).getResultStream().findFirst()
                    .filter(java.util.Objects::nonNull)
                    .map(v -> ((Number) v).longValue())
                    .ifPresent(storeId -> {
                        order.setStoreId((Long) storeId);
                        order.setPickupStoreId((Long) storeId);
                    });
        }
        return orders.saveAndFlush(order).getId();
    }

    @Override
    public void updateComplementary(Long orderId, String complementaryOrder) {
        OrderEntity order = order(orderId);
        order.setComplementaryOrder(complementaryOrder == null || complementaryOrder.isBlank() ? null : complementaryOrder.trim());
        orders.save(order);
    }

    @Override
    public void setState(Long orderId, String state, String cancelReason) {
        OrderEntity order = order(orderId);
        order.setState(state);
        order.setCancelReason(cancelReason);
        Instant now = Instant.now();
        switch (state) {
            case "PAID" -> order.setPaidAt(order.getPaidAt() == null ? now : order.getPaidAt());
            case "SHIPPED" -> order.setShippedAt(now);
            case "DELIVERED" -> order.setDeliveredAt(now);
            case "CANCELLED" -> order.setCancelledAt(now);
            default -> {
            }
        }
        orders.saveAndFlush(order);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Long> saleWarehouse(Long orderId) {
        List<Object> rows = em.createNativeQuery("""
                        SELECT COALESCE(o.warehouse_id, r.warehouse_id, st.warehouse_id)
                          FROM orders o
                          LEFT JOIN cash_sessions s ON s.id_cash_session = o.cash_session_id
                          LEFT JOIN cash_registers r ON r.id_cash_register = s.cash_register_id
                          LEFT JOIN stores st ON st.id_store = r.store_id
                         WHERE o.id_order = :id""")
                .setParameter("id", orderId).getResultList();
        return rows.stream().filter(java.util.Objects::nonNull).map(v -> ((Number) v).longValue()).findFirst();
    }

    @Override
    public void addReturnedAmount(Long orderId, BigDecimal amount) {
        OrderEntity order = order(orderId);
        BigDecimal current = order.getReturnedAmount() == null ? BigDecimal.ZERO : order.getReturnedAmount();
        order.setReturnedAmount(current.add(amount).setScale(2, java.math.RoundingMode.HALF_UP));
        orders.saveAndFlush(order);
    }

    @Override
    public void refreshTotal(Long orderId) {
        lines.flush();
        Object[] sums = (Object[]) em.createNativeQuery("""
                        SELECT COALESCE(SUM(total), 0), COALESCE(SUM(COALESCE(tax_amount, 0)), 0),
                               COALESCE(SUM(COALESCE(tax_base, total)), 0)
                          FROM product_orders WHERE order_id = :id AND deleted = 0""")
                .setParameter("id", orderId).getSingleResult();
        OrderEntity order = order(orderId);
        // V16: el envío (menos su descuento) se suma al total y a la base; no lleva impuesto.
        BigDecimal shipping = zero(order.getShippingAmount()).subtract(zero(order.getShippingDiscount())).max(BigDecimal.ZERO);
        order.setTotal(new BigDecimal(sums[0].toString()).add(shipping).setScale(2, java.math.RoundingMode.HALF_UP));
        order.setTaxAmount(new BigDecimal(sums[1].toString()).setScale(2, java.math.RoundingMode.HALF_UP));
        order.setNetAmount(new BigDecimal(sums[2].toString()).add(shipping).setScale(2, java.math.RoundingMode.HALF_UP));
        orders.saveAndFlush(order);
    }

    @Override
    public Optional<OrderLine> findLine(Long lineId) {
        return lines.findById(lineId).map(OrderLedgerAdapter::toLine);
    }

    @Override
    public List<OrderLine> lines(Long orderId) {
        return lines.findByOrderIdIn(List.of(orderId)).stream().map(OrderLedgerAdapter::toLine).toList();
    }

    @Override
    public OrderLine saveLine(OrderLine line) {
        ProductOrderEntity entity = line.id() == null ? new ProductOrderEntity()
                : lines.findById(line.id()).orElseThrow(() -> new NotFoundException("No se encontró la línea " + line.id() + "."));
        entity.setOrderId(line.orderId());
        entity.setProductId(line.productId());
        entity.setSkuId(line.skuId());
        entity.setSkuCode(line.skuCode());
        entity.setProductName(line.productName());
        entity.setUnitPrice(line.unitPrice());
        entity.setQuantity(line.quantity());
        entity.setDiscount(line.discount());
        entity.setSubtotal(line.subtotal());
        entity.setTotal(line.total());
        LineTax tax = line.tax();
        entity.setTaxRateId(tax == null ? null : tax.rateId());
        entity.setTaxCode(tax == null ? null : tax.code());
        entity.setTaxKind(tax == null ? null : tax.kind());
        entity.setTaxRate(tax == null ? null : tax.rate());
        entity.setTaxBase(tax == null ? null : tax.base());
        entity.setTaxAmount(tax == null ? null : tax.amount());
        return toLine(lines.saveAndFlush(entity));
    }

    @Override
    public void deleteLine(Long lineId) {
        lines.findById(lineId).ifPresent(entity -> {
            lines.delete(entity);
            lines.flush();
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Long> expiredPendingOrders(Instant createdBefore) {
        List<Number> ids = em.createNativeQuery("""
                SELECT id_order FROM orders
                 WHERE stock_managed = 1 AND state = 'PENDING' AND deleted = 0 AND created_at < :cutoff
                 ORDER BY id_order
                """).setParameter("cutoff", Timestamp.from(createdBefore)).getResultList();
        return ids.stream().map(Number::longValue).toList();
    }

    // ── V16 ────────────────────────────────────────────────────────────────────

    @Override
    public Optional<OrderDetails> details(Long orderId) {
        return orders.findById(orderId).map(OrderLedgerAdapter::toDetails);
    }

    @Override
    public void saveDetails(Long orderId, OrderDetails d) {
        OrderEntity o = order(orderId);
        o.setChannel(d.channel());
        o.setComplementaryOrder(d.complementaryOrder());
        writeCustomer(o, d.customer());
        DeliveryInfo v = d.delivery();
        o.setDeliveryMethod(v.method().name());
        o.setPickupStoreId(v.pickupStoreId());
        o.setRecipientName(v.recipientName());
        o.setRecipientPhone(v.recipientPhone());
        o.setShipAddress(v.address());
        o.setShipAddressDetail(v.addressDetail());
        o.setShipCountryId(v.countryId());
        o.setShipStateId(v.stateId());
        o.setShipCityId(v.cityId());
        o.setShipCountry(v.country());
        o.setShipState(v.state());
        o.setShipCity(v.city());
        o.setShipPostalCode(v.postalCode());
        o.setDeliveryNotes(v.notes());
        o.setCarrierId(v.carrierId());
        o.setShippingAmount(v.shippingAmount());
        o.setPromisedDate(v.promisedDate());
        o.setStoreId(d.storeId());
        o.setWarehouseId(d.warehouseId());
        o.setAssignedTo(d.assignedTo());
        o.setPriority(d.priority() == null ? OrderPriority.NORMAL.name() : d.priority().name());
        o.setInternalNotes(d.internalNotes());
        o.setCustomerNotes(d.customerNotes());
        orders.saveAndFlush(o);
    }

    @Override
    public void saveCustomer(Long orderId, CustomerInfo customer) {
        OrderEntity o = order(orderId);
        writeCustomer(o, customer);
        orders.saveAndFlush(o);
    }

    private static void writeCustomer(OrderEntity o, CustomerInfo c) {
        o.setCustomerId(c.customerId());
        o.setCustomerKind(c.kind() == null ? null : c.kind().name());
        o.setCustomerDocType(c.docType());
        o.setCustomerDocNumber(c.docNumber());
        o.setCustomerName(c.name());
        o.setCustomerEmail(c.email());
        o.setCustomerPhone(c.phone());
        o.setBillingAddress(c.billingAddress());
        o.setBillingCity(c.billingCity());
    }

    @Override
    public void saveDiscount(Long orderId, DiscountType type, BigDecimal value, Long couponId, String couponCode) {
        OrderEntity o = order(orderId);
        o.setDiscountType(type.name());
        o.setDiscountValue(value);
        o.setCouponId(couponId);
        o.setCouponCode(couponCode);
        orders.saveAndFlush(o);
    }

    @Override
    public List<LineState> lineStates(Long orderId) {
        return lines.findByOrderIdIn(List.of(orderId)).stream()
                .sorted(java.util.Comparator.comparing(ProductOrderEntity::getId))
                .map(e -> new LineState(e.getId(), e.getProductId(), e.getSkuId(),
                        e.getSkuCode() != null ? e.getSkuCode() : e.getProductName() != null ? e.getProductName() : "#" + e.getProductId(),
                        e.getQuantity() == null ? 0 : e.getQuantity(), zero(e.getSubtotal()), zero(e.getDiscount()),
                        zero(e.getOrderDiscount()), zero(e.getTotal()), e.getPickedQuantity() == null ? 0 : e.getPickedQuantity(),
                        toLine(e).tax()))
                .toList();
    }

    @Override
    public void applyPricing(Long orderId, Map<Long, LinePrice> prices, BigDecimal discountAmount, BigDecimal shippingDiscount) {
        for (ProductOrderEntity e : lines.findByOrderIdIn(List.of(orderId))) {
            LinePrice price = prices.get(e.getId());
            if (price == null) {
                continue;
            }
            e.setOrderDiscount(price.orderDiscount());
            e.setTotal(price.total());
            if (price.tax() != null) {
                e.setTaxBase(price.tax().base());
                e.setTaxAmount(price.tax().amount());
            }
            lines.save(e);
        }
        lines.flush();
        OrderEntity o = order(orderId);
        o.setDiscountAmount(zero(discountAmount));
        o.setShippingDiscount(zero(shippingDiscount));
        orders.saveAndFlush(o);
    }

    @Override
    public void setPicked(Long lineId, int picked) {
        ProductOrderEntity e = lines.findById(lineId).orElseThrow(() -> new NotFoundException("No se encontró la línea " + lineId + "."));
        e.setPickedQuantity(picked);
        lines.saveAndFlush(e);
    }

    @Override
    public void setShipment(Long orderId, Long carrierId, String trackingNumber) {
        OrderEntity o = order(orderId);
        o.setCarrierId(carrierId);
        o.setTrackingNumber(trackingNumber);
        orders.saveAndFlush(o);
    }

    @Override
    public OrderNumber lockNumbering() {
        Object[] row = (Object[]) em.createNativeQuery(
                        "SELECT prefix, padding, next_number FROM order_numbering WHERE id = 1 FOR UPDATE")
                .getResultStream().findFirst().orElse(null);
        if (row == null) {
            Number max = (Number) em.createNativeQuery("SELECT COALESCE(MAX(id_order), 0) + 1 FROM orders").getSingleResult();
            em.createNativeQuery("INSERT IGNORE INTO order_numbering (id, prefix, padding, next_number) VALUES (1, 'ORD', 6, :n)")
                    .setParameter("n", max.longValue()).executeUpdate();
            return new OrderNumber("ORD", 6, max.longValue());
        }
        return new OrderNumber((String) row[0], ((Number) row[1]).intValue(), ((Number) row[2]).longValue());
    }

    @Override
    public void saveNumbering(OrderNumber numbering) {
        em.createNativeQuery("UPDATE order_numbering SET prefix = :p, padding = :d, next_number = :n WHERE id = 1")
                .setParameter("p", numbering.prefix()).setParameter("d", numbering.padding())
                .setParameter("n", numbering.next()).executeUpdate();
    }

    @Override
    public boolean orderNumberExists(String orderNumber) {
        Number count = (Number) em.createNativeQuery("SELECT COUNT(*) FROM orders WHERE order_number = :n")
                .setParameter("n", orderNumber).getSingleResult();
        return count.longValue() > 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<MarketplaceLink> marketplaceLink(Long orderId) {
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT o.purchase_id, o.seller_id, CASE WHEN s.kind = 'EXTERNAL' THEN 1 ELSE 0 END
                          FROM orders o LEFT JOIN mp_sellers s ON s.id = o.seller_id
                         WHERE o.id_order = :id AND o.purchase_id IS NOT NULL""")
                .setParameter("id", orderId).getResultList();
        return rows.stream().findFirst().map(row -> new MarketplaceLink(((Number) row[0]).longValue(),
                row[1] == null ? null : ((Number) row[1]).longValue(), ((Number) row[2]).intValue() == 1));
    }

    @Override
    public boolean consignmentSale(Long orderId) {
        Object row = em.createNativeQuery("""
                        SELECT CASE WHEN COUNT(*) > 0 AND SUM(CASE WHEN f.fulfillment = 'PLATFORM' THEN 1 ELSE 0 END) = COUNT(*)
                                    THEN 1 ELSE 0 END
                          FROM product_orders l LEFT JOIN mp_offers f ON f.id = l.offer_id
                         WHERE l.order_id = :id AND l.deleted = 0""")
                .setParameter("id", orderId).getSingleResult();
        return row != null && ((Number) row).intValue() == 1;
    }

    @Override
    public boolean storeCollects(Long orderId) {
        return !"SELLER".equalsIgnoreCase(collection == null ? "" : collection.trim()) || consignmentSale(orderId);
    }

    /** Toma el siguiente número libre (salta los que ya existan) y avanza la numeración. */
    private String takeNumber() {
        OrderNumber numbering = lockNumbering();
        String number = numbering.current();
        while (orderNumberExists(number)) {
            numbering = numbering.advance();
            number = numbering.current();
        }
        saveNumbering(numbering.advance());
        return number;
    }

    static OrderDetails toDetails(OrderEntity o) {
        CustomerInfo customer = new CustomerInfo(o.getCustomerId(),
                o.getCustomerKind() == null ? CustomerKind.PERSON : CustomerKind.parse(o.getCustomerKind()),
                o.getCustomerDocType(), o.getCustomerDocNumber(), o.getCustomerName(), o.getCustomerEmail(),
                o.getCustomerPhone(), o.getBillingAddress(), o.getBillingCity());
        DeliveryInfo delivery = new DeliveryInfo(DeliveryMethod.parse(o.getDeliveryMethod()), o.getPickupStoreId(),
                o.getRecipientName(), o.getRecipientPhone(), o.getShipAddress(), o.getShipAddressDetail(),
                o.getShipCountryId(), o.getShipStateId(), o.getShipCityId(), o.getShipCountry(), o.getShipState(),
                o.getShipCity(), o.getShipPostalCode(), o.getDeliveryNotes(), o.getCarrierId(),
                zero(o.getShippingAmount()).setScale(2, java.math.RoundingMode.HALF_UP), o.getPromisedDate());
        return new OrderDetails(o.getId(), o.getState(), o.getOrderNumber(), o.getChannel(), o.getCashSessionId(),
                o.getComplementaryOrder(), customer, delivery, o.getStoreId(), o.getWarehouseId(), o.getAssignedTo(),
                OrderPriority.parse(o.getPriority()), DiscountType.parse(o.getDiscountType()), o.getDiscountValue(),
                o.getCouponId(), o.getCouponCode(), o.getInternalNotes(), o.getCustomerNotes(), o.getCarrierId(),
                o.getTrackingNumber());
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value;
    }

    private OrderEntity order(Long orderId) {
        return orders.findById(orderId).orElseThrow(() -> new NotFoundException("No se encontró la orden " + orderId + "."));
    }

    static OrderLine toLine(ProductOrderEntity entity) {
        return new OrderLine(entity.getId(), entity.getOrderId(), entity.getProductId(), entity.getSkuId(),
                entity.getSkuCode(), entity.getProductName(), entity.getUnitPrice(),
                entity.getQuantity() == null ? 0 : entity.getQuantity(), entity.getDiscount(), entity.getSubtotal(),
                entity.getTotal(), entity.getTaxAmount() == null || entity.getTaxBase() == null ? null
                        : new LineTax(entity.getTaxRateId(), entity.getTaxCode(), entity.getTaxKind(), entity.getTaxRate(),
                        entity.getTaxBase(), entity.getTaxAmount()));
    }
}
