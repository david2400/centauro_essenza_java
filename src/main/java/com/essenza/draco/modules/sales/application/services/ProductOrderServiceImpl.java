package com.essenza.draco.modules.sales.application.services;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.sales.application.dto.product_order.CreateProductOrderDto;
import com.essenza.draco.modules.sales.application.dto.product_order.ProductOrderDto;
import com.essenza.draco.modules.sales.application.dto.product_order.UpdateProductOrderDto;
import com.essenza.draco.modules.sales.application.input.product_order.CreateProductOrderUseCase;
import com.essenza.draco.modules.sales.application.input.product_order.DeleteProductOrderUseCase;
import com.essenza.draco.modules.sales.application.input.product_order.FindProductOrderByIdUseCase;
import com.essenza.draco.modules.sales.application.input.product_order.FindProductOrdersUseCase;
import com.essenza.draco.modules.sales.application.input.product_order.UpdateProductOrderUseCase;
import com.essenza.draco.modules.sales.application.output.repository.OrderLedger;
import com.essenza.draco.modules.sales.application.output.repository.OrderLedger.OrderHeader;
import com.essenza.draco.modules.sales.application.output.repository.OrderLedger.OrderLine;
import com.essenza.draco.modules.sales.application.output.repository.ProductOrderRepository;
import com.essenza.draco.modules.sales.domain.model.LineTax;
import com.essenza.draco.modules.sales.domain.model.OrderLineAmounts;
import com.essenza.draco.modules.sales.domain.model.OrderStatus;
import com.essenza.draco.shared.common.catalog.SkuPricing;
import com.essenza.draco.shared.common.catalog.SkuPricing.SkuSnapshot;
import com.essenza.draco.shared.common.inventory.StockReservations;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;

import static com.essenza.draco.modules.sales.domain.model.order.OrderEventType.LINES;

/**
 * Líneas de orden por SKU. El precio, el código y el nombre del SKU se congelan al
 * crear la línea; el backend calcula subtotal, total y el total de la orden.
 * Solo se modifican líneas de órdenes pendientes; en las que gestionan stock cada
 * cambio ajusta la reserva.
 */
@Service
@Transactional
public class ProductOrderServiceImpl implements CreateProductOrderUseCase,
        UpdateProductOrderUseCase,
        DeleteProductOrderUseCase,
        FindProductOrderByIdUseCase,
        FindProductOrdersUseCase {

    private final ProductOrderRepository productOrderRepository;
    private final OrderLedger ledger;
    private final SkuPricing skuPricing;
    private final StockReservations reservations;
    private final Duration reservationTtl;
    private final com.essenza.draco.modules.sales.application.services.order.OrderSoporte soporte;

    public ProductOrderServiceImpl(ProductOrderRepository productOrderRepository, OrderLedger ledger, SkuPricing skuPricing,
                                   StockReservations reservations,
                                   @Value("${essenza.orders.reservation-ttl:PT24H}") Duration reservationTtl,
                                   com.essenza.draco.modules.sales.application.services.order.OrderSoporte soporte) {
        this.productOrderRepository = productOrderRepository;
        this.ledger = ledger;
        this.skuPricing = skuPricing;
        this.reservations = reservations;
        this.reservationTtl = reservationTtl;
        this.soporte = soporte;
    }

    @Override
    public ProductOrderDto create(CreateProductOrderDto input) {
        return create(input, true);
    }

    /** Agrega la línea; {@code audit} = false al crear la orden completa (la creación ya queda registrada). */
    public ProductOrderDto create(CreateProductOrderDto input, boolean audit) {
        if (input.getOrderId() == null) {
            throw new IllegalArgumentException("Indica la orden (order_id) de la línea.");
        }
        OrderHeader order = pendingOrder(input.getOrderId());
        SkuSnapshot sku = sellable(skuPricing.resolve(input.getProductId(), input.getSkuId()));
        OrderLineAmounts amounts = OrderLineAmounts.of(sku.unitPrice(), input.getQuantity(), input.getDiscount());
        OrderLine line = ledger.saveLine(new OrderLine(null, order.id(), sku.productId(), sku.skuId(), sku.code(), sku.name(),
                amounts.unitPrice(), amounts.quantity(), amounts.discount(), amounts.subtotal(), amounts.total(),
                taxOf(sku, amounts.total())));
        reserve(order, line);
        soporte.reprice(order.id());
        if (audit) {
            soporte.log(order.id(), LINES, null, null, "+ " + label(line) + " × " + line.quantity(), null);
        }
        return priced(line);
    }

    @Override
    public ProductOrderDto update(Long id, UpdateProductOrderDto input) {
        OrderLine current = ledger.findLine(id)
                .orElseThrow(() -> new NotFoundException("No se encontró la línea " + id + "."));
        if (input.getOrderId() != null && !Objects.equals(input.getOrderId(), current.orderId())) {
            throw new IllegalArgumentException("Una línea no se puede mover a otra orden.");
        }
        OrderHeader order = pendingOrder(current.orderId());

        boolean skuChanged = (input.getSkuId() != null && !Objects.equals(input.getSkuId(), current.skuId()))
                || (input.getSkuId() == null && input.getProductId() != null
                && !Objects.equals(input.getProductId(), current.productId()));
        Long productId = current.productId();
        Long skuId = current.skuId();
        String code = current.skuCode();
        String name = current.productName();
        java.math.BigDecimal unitPrice = current.unitPrice();
        LineTax tax = current.tax();
        boolean needsSnapshot = skuChanged || skuId == null || unitPrice == null;
        if (needsSnapshot || tax == null) {
            // Otro SKU (o línea heredada sin foto): se toma el precio vigente. Una línea sin impuesto
            // congelado (anterior a V9) solo toma la tarifa vigente y conserva su precio.
            SkuSnapshot sku = skuPricing.resolve(skuChanged ? input.getProductId() : current.productId(),
                    skuChanged ? input.getSkuId() : current.skuId());
            if (needsSnapshot) {
                sellable(sku);
                productId = sku.productId();
                skuId = sku.skuId();
                code = sku.code();
                name = sku.name();
                unitPrice = sku.unitPrice();
            }
            tax = taxOf(sku, java.math.BigDecimal.ZERO);
        }
        Integer quantity = input.getQuantity() != null ? input.getQuantity() : current.quantity();
        OrderLineAmounts amounts = OrderLineAmounts.of(unitPrice, quantity,
                input.getDiscount() != null ? input.getDiscount() : current.discount());
        OrderLine line = ledger.saveLine(new OrderLine(id, order.id(), productId, skuId, code, name, amounts.unitPrice(),
                amounts.quantity(), amounts.discount(), amounts.subtotal(), amounts.total(), tax.forTotal(amounts.total())));
        reserve(order, line);
        soporte.reprice(order.id());
        soporte.log(order.id(), LINES, null, null, "~ " + label(line) + " × " + line.quantity(), null);
        return priced(line);
    }

    @Override
    public boolean deleteById(Long id) {
        Optional<OrderLine> current = ledger.findLine(id);
        if (current.isEmpty()) {
            return false;
        }
        OrderHeader order = pendingOrder(current.get().orderId());
        if (order.stockManaged() && current.get().skuId() != null) {
            reservations.reserveLine(order.id(), id, current.get().productId(), current.get().skuId(), 0, expiresAt(order));
        }
        ledger.deleteLine(id);
        soporte.reprice(order.id());
        soporte.log(order.id(), LINES, null, null, "- " + label(current.get()) + " × " + current.get().quantity(), null);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductOrderDto> findById(Long id) {
        return productOrderRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductOrderDto> findAll() {
        return productOrderRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductOrderDto> findByOrderId(Long orderId) {
        return productOrderRepository.findByOrderId(orderId);
    }

    private OrderHeader pendingOrder(Long orderId) {
        OrderHeader order = ledger.lockOrder(orderId)
                .orElseThrow(() -> new NotFoundException("No se encontró la orden " + orderId + "."));
        if (OrderStatus.tryParse(order.state()).orElse(null) != OrderStatus.PENDING) {
            throw new ConflictException("Solo se modifican las líneas de una orden pendiente (la orden " + orderId
                    + " está " + order.state() + ").");
        }
        if (ledger.marketplaceLink(orderId).map(OrderLedger.MarketplaceLink::thirdParty).orElse(false)) {
            throw new ConflictException("La orden " + orderId + " es el subpedido de un vendedor externo: sus productos "
                    + "salen de la oferta del vendedor. Cancélalo y registra otra compra para cambiarlos.");
        }
        return order;
    }

    private static SkuSnapshot sellable(SkuSnapshot sku) {
        if (!sku.sellable()) {
            throw new ConflictException("\"" + sku.name() + "\" (" + sku.code()
                    + ") no está a la venta: el SKU está inactivo o el producto no está publicado.");
        }
        return sku;
    }

    /** Impuesto incluido en el total con la tarifa vigente del producto (se congela en la línea). */
    static LineTax taxOf(SkuSnapshot sku, java.math.BigDecimal total) {
        return LineTax.included(sku.taxRateId(), sku.taxCode(), sku.taxKind(), sku.taxRate(), total);
    }

    private void reserve(OrderHeader order, OrderLine line) {
        if (order.stockManaged()) {
            reservations.reserveLine(order.id(), line.id(), line.productId(), line.skuId(), line.quantity(), expiresAt(order),
                    ledger.saleWarehouse(order.id()).orElse(null));
        }
    }

    private Instant expiresAt(OrderHeader order) {
        Instant base = order.createdAt() != null ? order.createdAt() : Instant.now();
        return base.plus(reservationTtl);
    }

    /** La línea con el total y el impuesto que dejó el reparto del descuento global. */
    private ProductOrderDto priced(OrderLine line) {
        ProductOrderDto dto = toDto(line);
        ledger.lineStates(line.orderId()).stream().filter(state -> state.id().equals(line.id())).findFirst().ifPresent(state -> {
            dto.setOrderDiscount(state.orderDiscount());
            dto.setTotal(state.total());
            if (state.tax() != null) {
                dto.setTaxBase(state.tax().base());
                dto.setTaxAmount(state.tax().amount());
            }
        });
        return dto;
    }

    private static String label(OrderLine line) {
        return line.skuCode() != null ? line.skuCode() : line.productName() != null ? line.productName() : "#" + line.productId();
    }

    static ProductOrderDto toDto(OrderLine line) {
        return ProductOrderDto.builder()
                .id(line.id())
                .orderId(line.orderId())
                .productId(line.productId())
                .skuId(line.skuId())
                .skuCode(line.skuCode())
                .productName(line.productName())
                .unitPrice(line.unitPrice())
                .quantity(line.quantity())
                .discount(line.discount())
                .subtotal(line.subtotal())
                .total(line.total())
                .taxRateId(line.tax() == null ? null : line.tax().rateId())
                .taxCode(line.tax() == null ? null : line.tax().code())
                .taxRate(line.tax() == null ? null : line.tax().rate())
                .taxBase(line.tax() == null ? null : line.tax().base())
                .taxAmount(line.tax() == null ? null : line.tax().amount())
                .build();
    }
}
