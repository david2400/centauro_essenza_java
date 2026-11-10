package com.essenza.draco.modules.sales.application.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.essenza.draco.modules.sales.application.dto.payment.PurchaseCheckoutDto;
import com.essenza.draco.modules.sales.application.dto.payment.StartPurchaseCheckoutDto;
import com.essenza.draco.modules.sales.application.dto.purchase.PurchaseDtos.CreatePurchaseRequest;
import com.essenza.draco.modules.sales.application.dto.purchase.PurchaseDtos.PurchaseDetailDto;
import com.essenza.draco.modules.sales.application.dto.purchase.PurchaseDtos.SubOrderDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.AddressInput;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.BuyerInput;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.CheckoutRequest;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.LineInput;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ShippingRequest;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontAccessStore;
import com.essenza.draco.shared.common.logistics.ShippingQuotes;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReadStore;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReadStore.OfferRow;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReadStore.PaymentOption;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReadStore.ProductRow;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReadStore.SkuRow;
import com.essenza.draco.modules.sales.application.services.payment.StartPurchaseCheckoutService;
import com.essenza.draco.modules.sales.application.services.purchase.PurchaseService;
import com.essenza.draco.modules.sales.application.services.storefront.StorefrontService;
import com.essenza.draco.modules.sales.domain.model.order.OrderRuleViolation;
import com.essenza.draco.modules.sales.domain.model.storefront.StorefrontRules;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.ActorScope;
import com.essenza.draco.shared.security.RequestActor;

/** Tienda pública con los puertos simulados: empresa fija, actor de servidor y enlace privado. */
class StorefrontServiceTest {

    static final long COMPANY = 7;
    StorefrontReadStore read;
    StorefrontAccessStore access;
    ShippingQuotes shippingQuotes;
    PurchaseService purchases;
    StartPurchaseCheckoutService checkout;
    StorefrontService store;
    final List<RequestActor> actorsSeen = new ArrayList<>();

    @BeforeEach
    void setUp() {
        read = mock(StorefrontReadStore.class);
        access = mock(StorefrontAccessStore.class);
        shippingQuotes = mock(ShippingQuotes.class);
        purchases = mock(PurchaseService.class);
        checkout = mock(StartPurchaseCheckoutService.class);
        store = service(true, "7", 3);
        when(read.gateways()).thenReturn(List.of(new PaymentOption(5, "Wompi", "WOMPI")));
    }

    StorefrontService service(boolean enabled, String company, int perHour) {
        return new StorefrontService(read, access, shippingQuotes, purchases, checkout, Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneOffset.UTC),
                enabled, company, "Essenza", "COP", "https://tienda.example.co/es/pedido/{token}", new BigDecimal("8000"), perHour);
    }

    static CheckoutRequest request(Long paymentMethod) {
        return new CheckoutRequest(new BuyerInput("Ana Pérez", "CC", "1020304050", "ana@x.co", "3001234567"),
                new AddressInput(null, null, "Calle 10 # 20-30", "Apto 301", "Medellín", "Antioquia", "Portería", 1L, 10L, 100L, null),
                List.of(new LineInput(11L, null, 2), new LineInput(12L, 90L, 1)), paymentMethod, true, null);
    }

    static PurchaseDetailDto purchase(BigDecimal pending) {
        return new PurchaseDetailDto(44L, "CMP-000044", "PENDING", false, "ONLINE", "COP", new BigDecimal("150000"), pending,
                "Ana Pérez", "CC", "1020304050", "ana@x.co", "3001234567", "storefront", "Tienda en línea",
                Instant.parse("2026-11-09T15:00:00Z"),
                List.of(new SubOrderDto(100L, "ORD-100", "PENDING", new BigDecimal("100000"), 1L, "Propia", false, 1, 2, null),
                        new SubOrderDto(101L, "ORD-101", "PENDING", new BigDecimal("50000"), 9L, "Moda Andina", true, 1, 1, null)));
    }

    @Test
    void disabledOrWithoutCompanyAnswers404() {
        assertThatThrownBy(() -> service(false, "7", 3).categories()).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service(true, " ", 3).info()).isInstanceOf(NotFoundException.class);
    }

    @Test
    void checkoutRunsAsTheStoreActorAndReturnsThePrivateLink() {
        when(purchases.create(any())).thenAnswer(inv -> {
            actorsSeen.add(ActorScope.current().orElseThrow());
            return purchase(new BigDecimal("158000"));
        });
        when(checkout.start(eq(44L), any())).thenAnswer(inv -> {
            actorsSeen.add(ActorScope.current().orElseThrow());
            return new PurchaseCheckoutDto("PUR-44-AB", new BigDecimal("158000"), "https://checkout.wompi.co/l/x", List.of());
        });
        var result = store.checkout(request(5L), "1.1.1.1");

        assertThat(result.purchaseNumber()).isEqualTo("CMP-000044");
        assertThat(result.checkoutUrl()).isEqualTo("https://checkout.wompi.co/l/x");
        assertThat(StorefrontRules.wellFormed(result.token())).isTrue();
        assertThat(actorsSeen).hasSize(2).allSatisfy(a -> {
            assertThat(a.companyId()).isEqualTo(COMPANY);
            assertThat(a.permissions()).containsExactlyInAnyOrder("VENTAS.VER", "VENTAS.GESTIONAR");
        });
        assertThat(ActorScope.current()).isEmpty(); // no queda fijado en el hilo
        verify(access).insert(eq(44L), eq(COMPANY), eq(StorefrontRules.hash(result.token())), eq("ana@x.co"), any());

        ArgumentCaptor<CreatePurchaseRequest> created = ArgumentCaptor.forClass(CreatePurchaseRequest.class);
        verify(purchases).create(created.capture());
        assertThat(created.getValue().details().channel()).isEqualTo("ONLINE");
        assertThat(created.getValue().details().delivery().shippingAmount()).isEqualByComparingTo("8000");
        assertThat(created.getValue().details().customer().customerId()).isNull();
        assertThat(created.getValue().lines()).extracting(CreatePurchaseRequest.Line::offerId).containsExactly(null, 90L);
        ArgumentCaptor<StartPurchaseCheckoutDto> started = ArgumentCaptor.forClass(StartPurchaseCheckoutDto.class);
        verify(checkout).start(eq(44L), started.capture());
        assertThat(started.getValue().returnUrl()).isEqualTo("https://tienda.example.co/es/pedido/" + result.token());
    }

    @Test
    void checkoutRejectsBadInputBeforeTouchingStock() {
        assertThatThrownBy(() -> store.checkout(request(99L), "2.2.2.2")).isInstanceOf(OrderRuleViolation.class)
                .hasMessageContaining("medio de pago");
        CheckoutRequest noTerms = new CheckoutRequest(request(5L).buyer(), request(5L).delivery(), request(5L).lines(), 5L, false, null);
        assertThatThrownBy(() -> store.checkout(noTerms, "3.3.3.3")).hasMessageContaining("términos");
        verify(purchases, never()).create(any());
    }

    @Test
    void checkoutIsRateLimitedPerClient() {
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> store.checkout(request(99L), "9.9.9.9")).isInstanceOf(OrderRuleViolation.class);
        }
        assertThatThrownBy(() -> store.checkout(request(5L), "9.9.9.9")).isInstanceOf(ConflictException.class)
                .hasMessageContaining("Demasiados");
    }

    @Test
    void orderLookupNeedsAValidTokenOfTheStoreCompany() {
        String token = StorefrontRules.newToken();
        assertThatThrownBy(() -> store.order("nope", "4.4.4.4")).isInstanceOf(NotFoundException.class);
        when(access.find(StorefrontRules.hash(token))).thenReturn(Optional.of(new StorefrontAccessStore.Access(44, 8)));
        assertThatThrownBy(() -> store.order(token, "4.4.4.4")).isInstanceOf(NotFoundException.class); // otra empresa

        when(access.find(StorefrontRules.hash(token))).thenReturn(Optional.of(new StorefrontAccessStore.Access(44, COMPANY)));
        when(purchases.get(44L)).thenReturn(purchase(new BigDecimal("158000")));
        when(read.orderLines(any())).thenReturn(List.of(
                new StorefrontReadStore.LineRow(100, "Camisa", "CAM-M", 2, new BigDecimal("50000"), new BigDecimal("100000"))));
        when(read.openCheckout(any(), any())).thenReturn(Optional.of("https://checkout.wompi.co/l/x"));
        var view = store.order(token, "4.4.4.4");
        assertThat(view.customerEmail()).isEqualTo("a••••@x.co");
        assertThat(view.canPay()).isTrue();
        assertThat(view.checkoutUrl()).isEqualTo("https://checkout.wompi.co/l/x");
        assertThat(view.subOrders()).extracting(s -> s.sellerName()).containsExactly("Essenza", "Moda Andina");
        assertThat(view.subOrders().get(0).lines()).hasSize(1);
    }

    @Test
    void catalogShowsTheStoreAndSellersCheapestFirstWithoutExactStock() {
        when(read.productBySlug("camisa-lino")).thenReturn(Optional.of(3L));
        when(read.products(List.of(3L))).thenReturn(Map.of(3L, new ProductRow(3, "camisa-lino", "Camisa de lino", "Fresca", null,
                "Ropa", "Andes")));
        when(read.skus(List.of(3L))).thenReturn(List.of(
                new SkuRow(11, 3, "CAM-M", "Talla M", null, true, new BigDecimal("120000"), new BigDecimal("150000"), 500),
                new SkuRow(12, 3, "CAM-L", "Talla L", null, false, new BigDecimal("120000"), null, 0)));
        when(read.offers(COMPANY, "COP", List.of(11L, 12L))).thenReturn(List.of(
                new OfferRow(90, 11, "Moda Andina", new BigDecimal("99000"), 4, 2, "NEW")));
        when(read.images(List.of(3L))).thenReturn(Map.of());
        var detail = store.product("camisa-lino");
        assertThat(detail.skus()).hasSize(1); // la talla L no tiene stock propio ni ofertas
        assertThat(detail.skus().get(0).options()).extracting(o -> o.sellerName()).containsExactly("Moda Andina", "Essenza");
        assertThat(detail.skus().get(0).options().get(1).available()).isEqualTo(10);
        assertThatThrownBy(() -> store.product("x' OR 1=1")).isInstanceOf(NotFoundException.class);
        verify(read, never()).productBySlug("x' OR 1=1");
        verify(purchases, never()).get(anyLong());
    }

    static ShippingQuotes.Option option(long card, long carrier, String name, String total, boolean free) {
        return new ShippingQuotes.Option(card, carrier, name, "LOCAL", null, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, false,
                new BigDecimal(total), BigDecimal.ZERO, free ? BigDecimal.ZERO : new BigDecimal(total), free, 1, 2, null,
                java.time.LocalDate.of(2026, 11, 11));
    }

    @Test
    void shippingUsesTheLogisticsTariffOrTheFixedAmountWithoutOne() {
        var lines = List.of(new LineInput(11L, null, 2));
        // Sin tarifario: valor fijo de la configuración.
        var fixed = store.shipping(new ShippingRequest(1L, 10L, 100L, "Medellín", "Antioquia", lines), "5.5.5.5");
        assertThat(fixed.options()).singleElement().satisfies(o -> assertThat(o.amount()).isEqualByComparingTo("8000"));
        verify(shippingQuotes, never()).quote(anyLong(), any());

        when(shippingQuotes.hasTariff(COMPANY)).thenReturn(true);
        when(read.ownPrices(List.of(11L))).thenReturn(Map.of(11L, new BigDecimal("100000")));
        when(shippingQuotes.quote(eq(COMPANY), any())).thenReturn(new ShippingQuotes.QuoteResult("LOCAL", null, 1L,
                List.of(option(3, 1, "Coordinadora", "6000", false), option(4, 2, "Servientrega", "9000", false))));
        var quoted = store.shipping(new ShippingRequest(1L, 10L, 100L, "Medellín", "Antioquia", lines), "5.5.5.5");
        assertThat(quoted.options()).extracting(o -> o.carrier()).containsExactly("Coordinadora", "Servientrega");
        ArgumentCaptor<ShippingQuotes.QuoteRequest> asked = ArgumentCaptor.forClass(ShippingQuotes.QuoteRequest.class);
        verify(shippingQuotes).quote(eq(COMPANY), asked.capture());
        assertThat(asked.getValue().cityId()).isEqualTo(100L);
        assertThat(asked.getValue().orderSubtotal()).isEqualByComparingTo("200000"); // precio vigente, no el del navegador
        assertThat(asked.getValue().items()).singleElement().satisfies(i -> assertThat(i.quantity()).isEqualTo(2));
        assertThat(asked.getValue().destinationLabel()).isEqualTo("Medellín, Antioquia");

        assertThatThrownBy(() -> store.shipping(new ShippingRequest(1L, 10L, null, "Medellín", "Antioquia", lines), "5.5.5.5"))
                .hasMessageContaining("ciudad");

        // Con tarifario y sin cobertura: no se puede comprar.
        when(shippingQuotes.quote(eq(COMPANY), any())).thenReturn(new ShippingQuotes.QuoteResult("NATIONAL", null, 1L, List.of()));
        assertThat(store.shipping(new ShippingRequest(1L, 20L, 200L, "Bogotá", "Cundinamarca", lines), "5.5.5.5").available()).isFalse();
        CheckoutRequest bogota = new CheckoutRequest(request(5L).buyer(),
                new AddressInput(null, null, "Calle 1 # 2-3", null, "Bogotá", "Cundinamarca", null, 1L, 20L, 200L, null),
                request(5L).lines(), 5L, true, null);
        assertThatThrownBy(() -> store.checkout(bogota, "7.7.7.7")).isInstanceOf(OrderRuleViolation.class)
                .hasMessageContaining("Bogotá");
        verify(purchases, never()).create(any());
    }

    @Test
    void checkoutRequotesAndKeepsTheChosenCarrier() {
        when(shippingQuotes.hasTariff(COMPANY)).thenReturn(true);
        when(shippingQuotes.quote(eq(COMPANY), any())).thenReturn(new ShippingQuotes.QuoteResult("LOCAL", null, 1L,
                List.of(option(3, 1, "Coordinadora", "6000", false), option(4, 2, "Servientrega", "9000", false))));
        when(purchases.create(any())).thenReturn(purchase(new BigDecimal("299000")));
        when(checkout.start(eq(44L), any())).thenReturn(new PurchaseCheckoutDto("R", BigDecimal.ONE, "https://x", List.of()));
        CheckoutRequest chosen = new CheckoutRequest(request(5L).buyer(), request(5L).delivery(), request(5L).lines(), 5L, true, 4L);
        store.checkout(chosen, "8.8.8.8");
        ArgumentCaptor<CreatePurchaseRequest> created = ArgumentCaptor.forClass(CreatePurchaseRequest.class);
        verify(purchases).create(created.capture());
        var delivery = created.getValue().details().delivery();
        assertThat(delivery.shippingAmount()).isEqualByComparingTo("9000");
        assertThat(delivery.carrierId()).isEqualTo(2L);
        assertThat(delivery.cityId()).isEqualTo(100L);
        assertThat(delivery.promisedDate()).isEqualTo(java.time.LocalDate.of(2026, 11, 11));

        CheckoutRequest gone = new CheckoutRequest(request(5L).buyer(), request(5L).delivery(), request(5L).lines(), 5L, true, 99L);
        assertThatThrownBy(() -> store.checkout(gone, "8.8.8.9")).isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya no está disponible");
    }
}
