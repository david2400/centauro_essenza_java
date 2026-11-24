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
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.CouponRequest;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.LineInput;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ShippingRequest;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontAccessStore;
import com.essenza.draco.shared.common.logistics.ShippingQuotes;
import com.essenza.draco.shared.common.promotions.Coupons;
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
    void catalogFiltersPassToTheStoreAndRejectBadPriceRanges() {
        when(read.search(anyLong(), any(), any(StorefrontReadStore.SearchFilter.class), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(new StorefrontReadStore.ProductPage(List.of(), 0));
        store.search("camisa", 1L, 3L, new BigDecimal("50000"), new BigDecimal("150000"), "price_asc", 0, 12);
        ArgumentCaptor<StorefrontReadStore.SearchFilter> filter = ArgumentCaptor.forClass(StorefrontReadStore.SearchFilter.class);
        verify(read).search(eq(7L), eq("COP"), filter.capture(), eq("PRICE_ASC"), eq(0), eq(12));
        assertThat(filter.getValue().brandId()).isEqualTo(3L);
        assertThat(filter.getValue().minPrice()).isEqualByComparingTo("50000");
        assertThat(filter.getValue().maxPrice()).isEqualByComparingTo("150000");

        assertThatThrownBy(() -> store.search(null, null, null, new BigDecimal("200"), new BigDecimal("100"), null, 0, 12))
                .hasMessageContaining("mínimo");
        assertThatThrownBy(() -> store.search(null, null, null, new BigDecimal("-1"), null, null, 0, 12))
                .hasMessageContaining("negativo");
        assertThatThrownBy(() -> store.search(null, null, null, null, null, "CHEAPEST", 0, 12)).hasMessageContaining("Orden no válido");
    }

    @Test
    void sellerPageOnlyForApprovedSellersAndWithoutPrivateData() {
        when(read.seller(7L, "COP", 9L)).thenReturn(java.util.Optional.of(new StorefrontReadStore.SellerRow(9L, "Moda Andina", "Medellín",
                "Ropa de lino", "http://inseguro.co", java.time.LocalDateTime.parse("2025-03-10T12:00:00"), 4)));
        var seller = store.seller(9L);
        assertThat(seller.name()).isEqualTo("Moda Andina");
        assertThat(seller.sinceYear()).isEqualTo(2025);
        assertThat(seller.website()).isNull(); // solo https
        assertThat(seller.products()).isEqualTo(4);
        when(read.seller(7L, "COP", 10L)).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> store.seller(10L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> store.seller(0L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> store.seller(null)).isInstanceOf(NotFoundException.class);

        when(read.search(anyLong(), any(), any(StorefrontReadStore.SearchFilter.class), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(new StorefrontReadStore.ProductPage(List.of(), 0));
        store.search(null, null, null, null, null, 9L, "NEWEST", 0, 24);
        ArgumentCaptor<StorefrontReadStore.SearchFilter> filter = ArgumentCaptor.forClass(StorefrontReadStore.SearchFilter.class);
        verify(read).search(eq(7L), eq("COP"), filter.capture(), eq("NEWEST"), eq(0), eq(24));
        assertThat(filter.getValue().sellerId()).isEqualTo(9L);
    }

    @Test
    void brandPageValidatesTheSlug() {
        when(read.brand(7L, "COP", "andes")).thenReturn(java.util.Optional.of(new StorefrontReadStore.BrandProfileRow(1L, "Andes", "andes",
                "  ", 2)));
        assertThat(store.brand("andes").description()).isNull();
        assertThatThrownBy(() -> store.brand("../etc")).isInstanceOf(NotFoundException.class);
        when(read.brand(7L, "COP", "nada")).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> store.brand("nada")).isInstanceOf(NotFoundException.class);
        verify(read, never()).brand(anyLong(), any(), eq("../etc"));
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
        when(read.deliveries(any())).thenReturn(Map.of(100L,
                new StorefrontReadStore.DeliveryRow(100, false, "Envía", "GU-77", null, null, null)));
        var view = store.order(token, "4.4.4.4");
        assertThat(view.customerEmail()).isEqualTo("a••••@x.co");
        assertThat(view.canPay()).isTrue();
        assertThat(view.checkoutUrl()).isEqualTo("https://checkout.wompi.co/l/x");
        assertThat(view.subOrders()).extracting(s -> s.sellerName()).containsExactly("Essenza", "Moda Andina");
        assertThat(view.subOrders().get(0).lines()).hasSize(1);
        assertThat(view.subOrders().get(0).delivery().trackingNumber()).isEqualTo("GU-77");
        assertThat(view.subOrders().get(0).delivery().carrier()).isEqualTo("Envía");
        assertThat(view.subOrders().get(1).delivery()).isNull(); // sin datos de entrega: el subpedido sigue saliendo
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

    @Test
    void cardsKeepTheRequestedOrderAndSkipWhatIsNoLongerForSale() {
        List<Long> ids = List.of(5L, 3L, 4L);
        when(read.products(ids)).thenReturn(Map.of(
                3L, new ProductRow(3, "camisa-lino", "Camisa de lino", null, null, "Ropa", null),
                5L, new ProductRow(5, "bolso", "Bolso", null, "https://cdn/b.jpg", null, null),
                4L, new ProductRow(4, "agotado", "Agotado", null, null, null, null)));
        when(read.skus(ids)).thenReturn(List.of(
                new SkuRow(11, 3, "CAM-M", "Talla M", null, true, new BigDecimal("120000"), null, 5),
                new SkuRow(21, 5, "BOL", "Único", null, true, new BigDecimal("80000"), null, 2)));
        when(read.offers(COMPANY, "COP", List.of(11L, 21L))).thenReturn(List.of());

        var cards = store.cards(List.of(5L, 3L, 5L, 4L, -1L));
        assertThat(cards).extracting(c -> c.slug()).containsExactly("bolso", "camisa-lino"); // 4 ya no se vende
        assertThat(cards.get(0).priceFrom()).isEqualByComparingTo("80000");
        assertThat(store.cards(List.of())).isEmpty();
        assertThat(store.cards(null)).isEmpty();
        List<Long> tooMany = java.util.stream.LongStream.rangeClosed(1, 61).boxed().toList();
        assertThatThrownBy(() -> store.cards(tooMany)).isInstanceOf(OrderRuleViolation.class);
    }

    @Test
    void cardsCarryHoverPhotoAndQuickAddOnlyForSingleVariantProducts() {
        List<Long> ids = List.of(5L, 3L);
        when(read.products(ids)).thenReturn(Map.of(
                3L, new ProductRow(3, "camisa-lino", "Camisa de lino", null, null, "Ropa", null),
                5L, new ProductRow(5, "bolso", "Bolso", null, "https://cdn/b.jpg", null, null)));
        when(read.skus(ids)).thenReturn(List.of(
                new SkuRow(11, 3, "CAM-M", "Talla M", null, true, new BigDecimal("120000"), null, 5),
                new SkuRow(12, 3, "CAM-L", "Talla L", null, false, new BigDecimal("125000"), null, 5),
                new SkuRow(21, 5, "BOL", "Único", null, true, new BigDecimal("80000"), null, 2)));
        when(read.offers(COMPANY, "COP", List.of(11L, 12L, 21L))).thenReturn(List.of(
                new OfferRow(90, 21, "Cueros del Sur", new BigDecimal("75000"), 30, 3, "NEW")));
        when(read.images(ids)).thenReturn(Map.of(5L, List.of("https://cdn/b.jpg", "https://cdn/b2.jpg")));

        var cards = store.cards(ids);
        var bolso = cards.get(0);
        assertThat(bolso.hoverImageUrl()).isEqualTo("https://cdn/b2.jpg"); // no repite la foto principal
        assertThat(bolso.quickAdd()).isNotNull();
        assertThat(bolso.quickAdd().skuId()).isEqualTo(21);
        assertThat(bolso.quickAdd().offerId()).isEqualTo(90L); // la opción más barata: el vendedor
        assertThat(bolso.quickAdd().price()).isEqualByComparingTo("75000");
        assertThat(bolso.available()).isEqualTo(10); // nunca el número exacto
        var camisa = cards.get(1);
        assertThat(camisa.quickAdd()).isNull(); // dos tallas: hay que elegir en la ficha
        assertThat(camisa.hoverImageUrl()).isNull();
    }

    static PurchaseDetailDto purchaseWithStatus(String status) {
        PurchaseDetailDto p = purchase(BigDecimal.ZERO);
        return new PurchaseDetailDto(p.id(), p.number(), status, false, p.channel(), p.currency(), p.total(), p.pendingTotal(),
                p.customerName(), p.customerDocType(), p.customerDocNumber(), p.customerEmail(), p.customerPhone(), p.createdBy(),
                p.createdByName(), p.createdAt(), p.subOrders());
    }

    @Test
    void reviewsShowOnlyPublicDataAndASummaryByStars() {
        var reviewStore = mock(com.essenza.draco.modules.sales.application.output.storefront.StorefrontReviewStore.class);
        store.setReviews(reviewStore);
        when(read.productBySlug("crema")).thenReturn(Optional.of(3L));
        when(reviewStore.distribution(3L)).thenReturn(Map.of(5, 3L, 4, 1L));
        when(reviewStore.approved(3L, 0, 10)).thenReturn(List.of(new com.essenza.draco.modules.sales.application.output.storefront
                .StorefrontReviewStore.ReviewRow(1, "Ana María Pérez Gómez", 5, " ", "Muy buena, la recomiendo", null, true)));
        var page = store.reviews("crema", 0, 10);
        assertThat(page.summary().count()).isEqualTo(4);
        assertThat(page.summary().average()).isEqualByComparingTo("4.8");
        assertThat(page.summary().byStars()).containsExactly(3L, 1L, 0L, 0L, 0L); // de 5 a 1 estrellas
        assertThat(page.items().get(0).author()).isEqualTo("Ana G.");
        assertThat(page.items().get(0).title()).isNull();
        assertThat(StorefrontRules.publicName(null)).isEqualTo("Cliente");
        assertThat(StorefrontRules.publicName("  luis ")).isEqualTo("luis");
    }

    @Test
    void onlyBuyersOfAPaidOrderCanReviewTheirProductsOnce() {
        var reviewStore = mock(com.essenza.draco.modules.sales.application.output.storefront.StorefrontReviewStore.class);
        store.setReviews(reviewStore);
        String token = StorefrontRules.newToken();
        when(access.find(StorefrontRules.hash(token))).thenReturn(Optional.of(new StorefrontAccessStore.Access(44, COMPANY)));
        var buyer = new com.essenza.draco.modules.sales.application.output.storefront.StorefrontReviewStore.Buyer(null, "Ana Pérez",
                "ana@x.co");
        when(reviewStore.buyer(44L)).thenReturn(Optional.of(buyer));
        when(reviewStore.purchasedProducts(44L)).thenReturn(Map.of(3L, "Crema"));
        var ok = new com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ReviewRequest(3L, 5, "Top",
                "Me encantó la textura");

        when(purchases.get(44L)).thenReturn(purchaseWithStatus("PENDING"));
        assertThatThrownBy(() -> store.review(token, ok, "7.7.7.1")).hasMessageContaining("recibas tu pedido");
        assertThat(store.reviewable(token, "7.7.7.1")).isEmpty();
        // Pagado o en camino todavía no: solo quien ya lo recibió.
        when(purchases.get(44L)).thenReturn(purchaseWithStatus("IN_DELIVERY"));
        assertThatThrownBy(() -> store.review(token, ok, "7.7.7.8")).hasMessageContaining("recibas tu pedido");
        assertThat(store.reviewable(token, "7.7.7.8")).isEmpty();

        when(purchases.get(44L)).thenReturn(purchaseWithStatus("DELIVERED"));
        assertThatThrownBy(() -> store.review(token, new com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos
                .ReviewRequest(9L, 5, null, "Me encantó la textura"), "7.7.7.2")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> store.review(token, new com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos
                .ReviewRequest(3L, 6, null, "Me encantó la textura"), "7.7.7.3")).hasMessageContaining("1 a 5");
        assertThatThrownBy(() -> store.review(token, new com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos
                .ReviewRequest(3L, 4, null, "corto"), "7.7.7.4")).hasMessageContaining("10 caracteres");

        store.review(token, ok, "7.7.7.5");
        verify(reviewStore).insert(eq(3L), eq(buyer), eq(5), eq("Top"), eq("Me encantó la textura"), any());

        when(reviewStore.reviewedBy("ana@x.co", List.of(3L))).thenReturn(java.util.Set.of(3L));
        assertThatThrownBy(() -> store.review(token, ok, "7.7.7.6")).isInstanceOf(ConflictException.class);
        when(reviewStore.reviewStatuses(eq("ana@x.co"), any())).thenReturn(Map.of(3L, "APPROVED"));
        when(reviewStore.slugs(any())).thenReturn(Map.of(3L, "crema-karite"));
        assertThat(store.reviewable(token, "7.7.7.7")).singleElement().satisfies(r -> {
            assertThat(r.reviewed()).isTrue();
            assertThat(r.status()).isEqualTo("APPROVED");
            assertThat(r.slug()).isEqualTo("crema-karite");
        });
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

    static Coupons.CouponTerms coupon(String kind, String value, java.time.LocalDateTime until, java.util.Set<Long> categories) {
        return new Coupons.CouponTerms(3L, "VERANO", kind, new BigDecimal(value), null, null, null, until, true, null, 0,
                java.util.Set.of(), java.util.Set.of(), categories, java.util.Set.of());
    }

    @Test
    void couponQuoteAppliesOnlyToWhatTheStoreSells() {
        Coupons coupons = mock(Coupons.class);
        store.setCoupons(coupons);
        when(coupons.findByCode("VERANO")).thenReturn(Optional.of(coupon("PERCENTAGE", "10", null, java.util.Set.of())));
        when(read.skuRefs(any())).thenReturn(Map.of(11L, new StorefrontReadStore.SkuRef(11, 10, 1L, new BigDecimal("50000"))));
        List<LineInput> cart = List.of(new LineInput(11L, null, 2), new LineInput(12L, 90L, 1));

        var quote = store.coupon(new CouponRequest(" VERANO ", cart), "1.1.1.1");
        assertThat(quote.valid()).isTrue();
        assertThat(quote.discount()).isEqualByComparingTo("10000"); // 10 % de 2 × 50.000; la oferta del vendedor no entra
        assertThat(quote.freeShipping()).isFalse();

        assertThat(store.coupon(new CouponRequest("NOEXISTE", cart), "1.1.1.1").valid()).isFalse();
        var onlySellers = store.coupon(new CouponRequest("VERANO", List.of(new LineInput(12L, 90L, 1))), "1.1.1.1");
        assertThat(onlySellers.valid()).isFalse();
        assertThat(onlySellers.message()).contains("vende la tienda");

        when(coupons.findByCode("VERANO")).thenReturn(Optional.of(
                coupon("PERCENTAGE", "10", java.time.LocalDateTime.of(2026, 11, 1, 0, 0), java.util.Set.of())));
        var expired = store.coupon(new CouponRequest("VERANO", cart), "1.1.1.1");
        assertThat(expired.valid()).isFalse();
        assertThat(expired.message()).contains("vencido");

        when(coupons.findByCode("VERANO")).thenReturn(Optional.of(coupon("FIXED_AMOUNT", "5000", null, java.util.Set.of(2L))));
        assertThat(store.coupon(new CouponRequest("VERANO", cart), "1.1.1.1").message()).contains("no aplica");
        assertThatThrownBy(() -> store.coupon(new CouponRequest(" ", cart), "1.1.1.1")).hasMessageContaining("código");
    }

    @Test
    void couponQuoteIsRateLimited() {
        store.setCoupons(mock(Coupons.class));
        List<LineInput> cart = List.of(new LineInput(11L, null, 1));
        for (int i = 0; i < 30; i++) {
            store.coupon(new CouponRequest("X" + i, cart), "7.7.7.7");
        }
        assertThatThrownBy(() -> store.coupon(new CouponRequest("X", cart), "7.7.7.7")).isInstanceOf(ConflictException.class);
    }

    @Test
    void checkoutSendsTheCouponWithTheOrder() {
        when(purchases.create(any())).thenReturn(purchase(new BigDecimal("150000")));
        when(checkout.start(eq(44L), any())).thenReturn(new PurchaseCheckoutDto("R", BigDecimal.ONE, "https://x", List.of()));
        CheckoutRequest withCoupon = new CheckoutRequest(request(5L).buyer(), request(5L).delivery(), request(5L).lines(), 5L, true,
                null, " VERANO ");
        store.checkout(withCoupon, "6.6.6.6");
        ArgumentCaptor<CreatePurchaseRequest> created = ArgumentCaptor.forClass(CreatePurchaseRequest.class);
        verify(purchases).create(created.capture());
        assertThat(created.getValue().details().discount().type()).isEqualTo("COUPON");
        assertThat(created.getValue().details().discount().couponCode()).isEqualTo("VERANO");
    }
}
