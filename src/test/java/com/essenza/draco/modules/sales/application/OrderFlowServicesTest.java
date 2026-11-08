package com.essenza.draco.modules.sales.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.essenza.draco.modules.sales.application.dto.order.ChangeOrderStatusDto;
import com.essenza.draco.modules.sales.application.dto.order.CreateOrderDto;
import com.essenza.draco.modules.sales.application.dto.order.OrderDto;
import com.essenza.draco.modules.sales.application.dto.product_order.CreateProductOrderDto;
import com.essenza.draco.modules.sales.application.dto.product_order.UpdateProductOrderDto;
import com.essenza.draco.modules.sales.application.output.repository.OrderEventLog;
import com.essenza.draco.modules.sales.application.output.repository.OrderInsights;
import com.essenza.draco.modules.sales.application.output.repository.OrderLedger;
import com.essenza.draco.modules.sales.application.output.repository.OrderLedger.OrderDetails;
import com.essenza.draco.modules.sales.application.output.repository.OrderReferences;
import com.essenza.draco.modules.sales.application.output.repository.OrderLedger.OrderHeader;
import com.essenza.draco.modules.sales.application.output.repository.OrderLedger.OrderLine;
import com.essenza.draco.modules.sales.application.output.repository.OrderRepository;
import com.essenza.draco.modules.sales.application.output.repository.ProductOrderRepository;
import com.essenza.draco.modules.sales.application.services.OrderServiceImpl;
import com.essenza.draco.modules.sales.application.services.ProductOrderServiceImpl;
import com.essenza.draco.modules.sales.application.services.order.OrderSoporte;
import com.essenza.draco.modules.sales.application.services.order.OrderTransitions;
import com.essenza.draco.modules.sales.domain.model.order.CustomerInfo;
import com.essenza.draco.modules.sales.domain.model.order.DeliveryInfo;
import com.essenza.draco.modules.sales.domain.model.order.DiscountType;
import com.essenza.draco.modules.sales.domain.model.order.OrderPriority;
import com.essenza.draco.shared.common.promotions.Coupons;
import com.essenza.draco.shared.common.catalog.SkuPricing;
import com.essenza.draco.shared.common.catalog.SkuPricing.SkuSnapshot;
import com.essenza.draco.shared.common.inventory.StockReservations;
import com.essenza.draco.shared.common.marketplace.OfferStock;
import com.essenza.draco.shared.common.marketplace.SellerCommissions;
import com.essenza.draco.shared.exceptions.ConflictException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderFlowServicesTest {

    @Mock OrderRepository orderRepository;
    @Mock ProductOrderRepository productOrderRepository;
    @Mock OrderLedger ledger;
    @Mock SkuPricing skuPricing;
    @Mock StockReservations reservations;
    @Mock OrderInsights insights;
    @Mock OrderEventLog events;
    @Mock OrderReferences references;
    @Mock Coupons coupons;

    OrderServiceImpl orders;
    ProductOrderServiceImpl lines;
    static final Instant CREATED = Instant.parse("2026-10-05T10:00:00Z");
    static final Duration TTL = Duration.ofHours(24);

    @BeforeEach
    void setUp() {
        OrderSoporte soporte = new OrderSoporte(ledger, orderRepository, insights, events, references, coupons);
        OrderTransitions transitions = new OrderTransitions(ledger, orderRepository, reservations, event -> { }, soporte, OfferStock.NONE, SellerCommissions.NONE);
        orders = new OrderServiceImpl(orderRepository, ledger, reservations, TTL, transitions, soporte, OfferStock.NONE);
        lines = new ProductOrderServiceImpl(productOrderRepository, ledger, skuPricing, reservations, TTL, soporte);
        when(insights.enrich(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledger.details(anyLong())).thenAnswer(inv -> Optional.of(new OrderDetails(inv.getArgument(0), "PENDING", "ORD-7",
                "BACKOFFICE", null, null, CustomerInfo.EMPTY, DeliveryInfo.NONE, null, null, null, OrderPriority.NORMAL,
                DiscountType.NONE, null, null, null, null, null, null, null)));
        when(orderRepository.findById(anyLong())).thenAnswer(inv -> Optional.of(
                OrderDto.builder().id(inv.getArgument(0)).state("PENDING").build()));
        when(ledger.saveLine(any())).thenAnswer(inv -> {
            OrderLine line = inv.getArgument(0);
            return line.id() != null ? line : new OrderLine(70L, line.orderId(), line.productId(), line.skuId(), line.skuCode(),
                    line.productName(), line.unitPrice(), line.quantity(), line.discount(), line.subtotal(), line.total());
        });
        when(skuPricing.resolve(any(), eq(10L))).thenReturn(new SkuSnapshot(10L, 5L, "SKU-5-1", "Crema · 50 ml",
                new BigDecimal("35000"), true));
        when(skuPricing.resolve(any(), eq(11L))).thenReturn(new SkuSnapshot(11L, 5L, "SKU-5-2", "Crema · 100 ml",
                new BigDecimal("59000"), true));
        when(skuPricing.resolve(any(), eq(12L))).thenReturn(new SkuSnapshot(12L, 6L, "SKU-6", "Sin publicar",
                new BigDecimal("1000"), false));
    }

    private void order(String state, boolean managed) {
        when(ledger.lockOrder(7L)).thenReturn(Optional.of(new OrderHeader(7L, state, managed, CREATED)));
    }

    private static CreateProductOrderDto line(Long skuId, int quantity, String discount) {
        CreateProductOrderDto dto = new CreateProductOrderDto();
        dto.setOrderId(7L);
        dto.setSkuId(skuId);
        dto.setQuantity(quantity);
        dto.setDiscount(discount == null ? null : new BigDecimal(discount));
        return dto;
    }

    @Test
    void lasOrdenesSeCreanPendientes() {
        when(ledger.createOrder("web-1")).thenReturn(7L);
        CreateOrderDto dto = new CreateOrderDto();
        dto.setComplementaryOrder("web-1");
        assertThat(orders.create(dto).getNextStates()).containsExactlyInAnyOrder("PAID", "CANCELLED");

        dto.setState("PAID");
        assertThatThrownBy(() -> orders.create(dto)).hasMessageContaining("pendientes");
    }

    @Test
    void laLineaCongelaElPrecioYReservaElStock() {
        order("PENDING", true);
        var dto = lines.create(line(10L, 3, "5000"));

        assertThat(dto.getUnitPrice()).isEqualByComparingTo("35000");
        assertThat(dto.getSubtotal()).isEqualByComparingTo("105000");
        assertThat(dto.getTotal()).isEqualByComparingTo("100000");
        assertThat(dto.getProductName()).isEqualTo("Crema · 50 ml");
        verify(reservations).reserveLine(7L, 70L, 5L, 10L, 3, CREATED.plus(TTL), null);
        verify(ledger).refreshTotal(7L);
    }

    @Test
    void cambiarCantidadConservaElPrecioCongeladoYCambiarSkuTomaElNuevo() {
        order("PENDING", true);
        when(ledger.findLine(70L)).thenReturn(Optional.of(new OrderLine(70L, 7L, 5L, 10L, "SKU-5-1", "Crema · 50 ml",
                new BigDecimal("30000"), 3, BigDecimal.ZERO, new BigDecimal("90000"), new BigDecimal("90000"))));
        UpdateProductOrderDto dto = new UpdateProductOrderDto();
        dto.setId(70L);
        dto.setQuantity(2);
        assertThat(lines.update(70L, dto).getTotal()).isEqualByComparingTo("60000");

        dto.setSkuId(11L);
        assertThat(lines.update(70L, dto).getUnitPrice()).isEqualByComparingTo("59000");
        verify(reservations).reserveLine(7L, 70L, 5L, 11L, 2, CREATED.plus(TTL), null);
    }

    @Test
    void soloSeModificanLineasDeOrdenesPendientesYDeSkusALaVenta() {
        order("PAID", true);
        assertThatThrownBy(() -> lines.create(line(10L, 1, null))).isInstanceOf(ConflictException.class)
                .hasMessageContaining("pendiente");
        order("PENDING", true);
        assertThatThrownBy(() -> lines.create(line(12L, 1, null))).isInstanceOf(ConflictException.class)
                .hasMessageContaining("no está a la venta");
        verify(reservations, never()).reserveLine(anyLong(), anyLong(), anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }

    @Test
    void lasOrdenesHeredadasNoReservan() {
        order("PENDING", false);
        lines.create(line(10L, 1, null));
        verify(reservations, never()).reserveLine(anyLong(), anyLong(), anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }

    @Test
    void pagarDescuentaYExigeLineas() {
        order("PENDING", true);
        when(ledger.lines(7L)).thenReturn(List.of());
        assertThatThrownBy(() -> orders.changeStatus(7L, new ChangeOrderStatusDto("PAID", null)))
                .hasMessageContaining("no tiene líneas");

        when(ledger.lines(7L)).thenReturn(List.of(new OrderLine(70L, 7L, 5L, 10L, "c", "n", BigDecimal.ONE, 1,
                BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE)));
        orders.changeStatus(7L, new ChangeOrderStatusDto("PAID", null));
        verify(reservations).commitOrder(7L);
        verify(ledger).setState(7L, "PAID", null);
    }

    @Test
    void cancelarLiberaODevuelveSegunElEstado() {
        order("PENDING", true);
        orders.changeStatus(7L, new ChangeOrderStatusDto("CANCELLED", "Cliente desistió"));
        verify(reservations).releaseOrder(7L, false);
        verify(ledger).setState(7L, "CANCELLED", "Cliente desistió");

        order("PROCESSING", true);
        orders.changeStatus(7L, new ChangeOrderStatusDto("CANCELLED", null));
        verify(reservations).restockOrder(7L);
    }

    @Test
    void transicionNoPermitidaEs409() {
        order("SHIPPED", true);
        assertThatThrownBy(() -> orders.changeStatus(7L, new ChangeOrderStatusDto("CANCELLED", null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("SHIPPED a CANCELLED");
    }

    @Test
    void noSeBorraUnaOrdenPagada() {
        order("PAID", true);
        assertThatThrownBy(() -> orders.deleteById(7L)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("pendientes o canceladas");
        order("PENDING", true);
        when(orderRepository.deleteById(7L)).thenReturn(true);
        assertThat(orders.deleteById(7L)).isTrue();
        verify(reservations).releaseOrder(7L, false);
    }

    @Test
    void vencerCancelaSoloSiSigueSiendoPendiente() {
        order("PENDING", true);
        assertThat(orders.cancelExpired(7L)).isTrue();
        verify(reservations).releaseOrder(7L, true);
        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(ledger).setState(eq(7L), eq("CANCELLED"), reason.capture());
        assertThat(reason.getValue()).contains("vencida");

        order("PAID", true);
        assertThat(orders.cancelExpired(7L)).isFalse();
    }
}
