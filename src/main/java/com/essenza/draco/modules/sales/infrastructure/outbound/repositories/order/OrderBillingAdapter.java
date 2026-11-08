package com.essenza.draco.modules.sales.infrastructure.outbound.repositories.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.sales.infrastructure.outbound.persistence.mysql.shop.OrderEntity;
import com.essenza.draco.modules.sales.infrastructure.outbound.persistence.mysql.shop.ProductOrderEntity;
import com.essenza.draco.modules.sales.infrastructure.outbound.repositories.product_order.JpaProductOrderRepository;
import com.essenza.draco.shared.common.sales.OrderBilling;

import jakarta.persistence.EntityManager;

/** Orden lista para facturar ({@link OrderBilling}): líneas con impuesto y tipos de pago aprobados. */
@Component
public class OrderBillingAdapter implements OrderBilling {

    private final JpaOrderRepository orders;
    private final JpaProductOrderRepository lines;
    private final EntityManager em;

    public OrderBillingAdapter(JpaOrderRepository orders, JpaProductOrderRepository lines, EntityManager em) {
        this.orders = orders;
        this.lines = lines;
        this.em = em;
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Optional<BillableOrder> find(Long orderId) {
        Optional<OrderEntity> order = orders.findById(orderId);
        if (order.isEmpty()) {
            return Optional.empty();
        }
        List<BillableLine> billable = lines.findByOrderIdIn(List.of(orderId)).stream()
                .sorted(java.util.Comparator.comparing(ProductOrderEntity::getId))
                .map(OrderBillingAdapter::toLine)
                .toList();
        List<String> kinds = em.createNativeQuery("""
                        SELECT DISTINCT kind FROM payments
                         WHERE order_id = :id AND status = 'APPROVED' AND deleted = 0""")
                .setParameter("id", orderId).getResultList();
        OrderEntity o = order.get();
        // V16: el envío cobrado va como una línea sin impuesto para que la factura cuadre con el total.
        BigDecimal shipping = zero(o.getShippingAmount()).subtract(zero(o.getShippingDiscount())).max(BigDecimal.ZERO)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        if (shipping.signum() > 0) {
            List<BillableLine> withShipping = new java.util.ArrayList<>(billable);
            withShipping.add(new BillableLine(null, "ENVIO", "Envío", 1, shipping, BigDecimal.ZERO.setScale(2), shipping,
                    null, "EXCLUDED", BigDecimal.ZERO, shipping, BigDecimal.ZERO.setScale(2)));
            billable = List.copyOf(withShipping);
        }
        OrderBilling.BillableBuyer buyer = o.getCustomerDocNumber() == null || o.getCustomerDocNumber().isBlank() ? null
                : new OrderBilling.BillableBuyer(o.getCustomerDocType(), o.getCustomerDocNumber(), o.getCustomerName(),
                        o.getCustomerEmail(), o.getCustomerPhone(),
                        o.getBillingAddress() != null ? o.getBillingAddress() : o.getShipAddress(),
                        o.getBillingCity() != null ? o.getBillingCity() : o.getShipCity());
        // Marketplace (V19): el subpedido de un vendedor externo lo factura ese vendedor.
        boolean thirdParty = !em.createNativeQuery("""
                        SELECT 1 FROM orders x JOIN mp_sellers s ON s.id = x.seller_id
                         WHERE x.id_order = :id AND s.kind = 'EXTERNAL'""")
                .setParameter("id", orderId).getResultList().isEmpty();
        return Optional.of(new BillableOrder(o.getId(), o.getState(), o.getTotal(), zero(o.getTaxAmount()),
                o.getNetAmount() == null ? o.getTotal() : o.getNetAmount(), billable, kinds, o.getChannel(), buyer,
                thirdParty));
    }

    private static BillableLine toLine(ProductOrderEntity line) {
        BigDecimal total = zero(line.getTotal());
        // Línea sin impuesto congelado: todo el total es base (no debería ocurrir tras V9).
        BigDecimal base = line.getTaxBase() == null ? total : line.getTaxBase();
        BigDecimal tax = line.getTaxAmount() == null ? BigDecimal.ZERO.setScale(2) : line.getTaxAmount();
        return new BillableLine(line.getId(), line.getSkuCode(),
                line.getProductName() == null ? "Producto " + line.getProductId() : line.getProductName(),
                line.getQuantity() == null ? 0 : line.getQuantity(), zero(line.getUnitPrice()),
                // V16: el descuento facturado incluye la parte del descuento global de la orden.
                zero(line.getDiscount()).add(zero(line.getOrderDiscount())),
                total, line.getTaxCode(), line.getTaxKind(), line.getTaxRate() == null ? BigDecimal.ZERO : line.getTaxRate(),
                base, tax);
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value;
    }
}
