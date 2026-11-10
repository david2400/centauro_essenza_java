package com.essenza.draco.modules.sales.application.services.storefront;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.sales.application.dto.order.SaveOrderDetailsDto;
import com.essenza.draco.modules.sales.application.dto.payment.PurchaseCheckoutDto;
import com.essenza.draco.modules.sales.application.dto.payment.StartPurchaseCheckoutDto;
import com.essenza.draco.modules.sales.application.dto.purchase.PurchaseDtos.CreatePurchaseRequest;
import com.essenza.draco.modules.sales.application.dto.purchase.PurchaseDtos.PurchaseDetailDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.CategoryDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.CheckoutRequest;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.CheckoutResultDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.LineViewDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.OptionDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.OrderViewDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.PageDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.PaymentOptionDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ProductCardDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ProductDetailDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.SkuDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.StoreInfoDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.SubOrderViewDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ShippingQuoteDto;
import com.essenza.draco.modules.sales.application.output.storefront.ShippingRateStore;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontAccessStore;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReadStore;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReadStore.OfferRow;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReadStore.ProductRow;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReadStore.SkuRow;
import com.essenza.draco.modules.sales.application.services.payment.StartPurchaseCheckoutService;
import com.essenza.draco.modules.sales.application.services.purchase.PurchaseService;
import com.essenza.draco.modules.sales.domain.model.order.OrderRuleViolation;
import com.essenza.draco.modules.sales.domain.model.storefront.RateLimiter;
import com.essenza.draco.modules.sales.domain.model.storefront.ShippingRates;
import com.essenza.draco.modules.sales.domain.model.storefront.StorefrontRules;
import com.essenza.draco.modules.sales.domain.model.storefront.StorefrontRules.Address;
import com.essenza.draco.modules.sales.domain.model.storefront.StorefrontRules.Buyer;
import com.essenza.draco.modules.sales.domain.model.storefront.StorefrontRules.CartLine;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.ActorScope;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Tienda pública (comprador invitado). Vende el catálogo de UNA empresa, fijada en la configuración
 * ({@code essenza.storefront.company-id}); el navegador nunca elige empresa, vendedor ni precio: envía SKU, oferta
 * y cantidad, y el servidor resuelve el resto con las mismas reglas del panel (compra multivendedor y un solo link
 * de pago). La compra se registra a nombre del actor "Tienda en línea" con solo VENTAS.VER/GESTIONAR, y el comprador
 * recibe un enlace privado (secreto de 256 bits; en la base solo su hash) para seguir y pagar su pedido.
 * Apagada por defecto ({@code essenza.storefront.enabled}): responde 404.
 */
@Service
public class StorefrontService {

    static final String ACTOR_ID = "storefront";
    static final String ACTOR_NAME = "Tienda en línea";
    static final int MAX_PAGE = 48;
    static final Set<String> SORTS = Set.of("NEWEST", "NAME");

    private final StorefrontReadStore read;
    private final StorefrontAccessStore access;
    private final ShippingRateStore shippingRates;
    private final PurchaseService purchases;
    private final StartPurchaseCheckoutService checkout;
    private final Clock clock;
    private final boolean enabled;
    private final Long companyId;
    private final String storeName;
    private final String currency;
    private final String returnUrl;
    private final BigDecimal shipping;
    private final RateLimiter checkoutLimiter;
    private final RateLimiter lookupLimiter;

    @Autowired
    public StorefrontService(StorefrontReadStore read, StorefrontAccessStore access, ShippingRateStore shippingRates,
                             PurchaseService purchases, StartPurchaseCheckoutService checkout,
                             @Value("${essenza.storefront.enabled:false}") boolean enabled,
                             @Value("${essenza.storefront.company-id:}") String companyId,
                             @Value("${essenza.storefront.store-name:Tienda en línea}") String storeName,
                             @Value("${essenza.marketplace.catalog-currency:COP}") String currency,
                             @Value("${essenza.storefront.return-url:}") String returnUrl,
                             @Value("${essenza.storefront.shipping-amount:0}") BigDecimal shipping,
                             @Value("${essenza.storefront.checkouts-per-hour:10}") int checkoutsPerHour) {
        this(read, access, shippingRates, purchases, checkout, Clock.systemUTC(), enabled, companyId, storeName, currency, returnUrl, shipping,
                checkoutsPerHour);
    }

    public StorefrontService(StorefrontReadStore read, StorefrontAccessStore access, ShippingRateStore shippingRates,
                             PurchaseService purchases, StartPurchaseCheckoutService checkout, Clock clock, boolean enabled, String companyId, String storeName,
                             String currency, String returnUrl, BigDecimal shipping, int checkoutsPerHour) {
        this.read = read;
        this.access = access;
        this.shippingRates = shippingRates;
        this.purchases = purchases;
        this.checkout = checkout;
        this.clock = clock;
        this.companyId = companyId == null || companyId.isBlank() ? null : Long.valueOf(companyId.trim());
        this.storeName = storeName == null || storeName.isBlank() ? ACTOR_NAME : storeName.trim();
        this.currency = currency.trim().toUpperCase(Locale.ROOT);
        this.returnUrl = returnUrl == null || returnUrl.isBlank() ? null : returnUrl.trim();
        if (this.returnUrl != null && !this.returnUrl.matches("https?://[^\\s]+")) {
            throw new IllegalStateException("essenza.storefront.return-url debe ser una URL http(s).");
        }
        // Sin empresa o sin URL de regreso de la tienda no se habilita (el comprador volvería al panel).
        this.enabled = enabled && this.companyId != null && this.returnUrl != null;
        this.shipping = shipping == null || shipping.signum() < 0 ? BigDecimal.ZERO : shipping;
        this.checkoutLimiter = new RateLimiter(Math.max(1, checkoutsPerHour), Duration.ofHours(1), 10_000);
        this.lookupLimiter = new RateLimiter(120, Duration.ofMinutes(10), 10_000);
    }

    // ── Catálogo ───────────────────────────────────────────────────────────

    public StoreInfoDto info() {
        long company = company();
        return new StoreInfoDto(storeName, currency, read.gateways().stream()
                .map(g -> new PaymentOptionDto(g.id(), g.name(), g.provider())).toList(),
                StorefrontRules.DOC_TYPES.stream().sorted().toList(), shipping);
    }

    public List<CategoryDto> categories() {
        return read.categories(company(), currency).stream().map(c -> new CategoryDto(c.id(), c.name(), c.slug(), c.products())).toList();
    }

    public PageDto<ProductCardDto> search(String q, Long categoryId, String sort, Integer page, Integer size) {
        long company = company();
        int p = page == null || page < 0 ? 0 : Math.min(page, 1000);
        int s = size == null || size <= 0 ? 24 : Math.min(size, MAX_PAGE);
        String order = sort == null ? "NEWEST" : sort.trim().toUpperCase(Locale.ROOT);
        if (!SORTS.contains(order)) {
            throw new OrderRuleViolation("Orden no válido: " + sort + ".");
        }
        StorefrontReadStore.ProductPage found = read.search(company, currency, StorefrontRules.likePattern(q), categoryId, order,
                p * s, s);
        Map<Long, ProductRow> rows = read.products(found.ids());
        Map<Long, List<SkuView>> skus = skus(company, found.ids());
        List<ProductCardDto> cards = new ArrayList<>();
        for (Long id : found.ids()) {
            ProductRow row = rows.get(id);
            List<SkuView> views = skus.getOrDefault(id, List.of());
            if (row == null || views.isEmpty()) {
                continue; // cambió entre la búsqueda y la lectura
            }
            BigDecimal from = views.stream().flatMap(v -> v.options().stream()).map(OptionDto::price).min(Comparator.naturalOrder())
                    .orElse(null);
            BigDecimal compareAt = views.stream().map(SkuView::compareAt).filter(c -> c != null && from != null && c.compareTo(from) > 0)
                    .max(Comparator.naturalOrder()).orElse(null);
            int sellers = (int) views.stream().flatMap(v -> v.options().stream()).map(o -> o.store() ? "" : o.sellerName()).distinct().count();
            String image = row.imageUrl() != null ? row.imageUrl()
                    : views.stream().map(SkuView::imageUrl).filter(u -> u != null).findFirst().orElse(null);
            cards.add(new ProductCardDto(row.id(), row.slug(), row.name(), image, row.category(), row.brand(), from, compareAt, sellers));
        }
        return new PageDto<>(cards, p, s, found.total());
    }

    public ProductDetailDto product(String slugOrId) {
        long company = company();
        if (slugOrId == null || !slugOrId.matches("[A-Za-z0-9-]{1,160}")) {
            throw new NotFoundException("Producto no encontrado.");
        }
        Long id = read.productBySlug(slugOrId).orElseThrow(() -> new NotFoundException("Producto no encontrado."));
        ProductRow row = read.products(List.of(id)).get(id);
        List<SkuView> views = skus(company, List.of(id)).getOrDefault(id, List.of());
        if (row == null || views.isEmpty()) {
            throw new NotFoundException("Este producto no está disponible.");
        }
        List<String> images = new ArrayList<>(read.images(List.of(id)).getOrDefault(id, List.of()));
        if (images.isEmpty() && row.imageUrl() != null) {
            images.add(row.imageUrl());
        }
        return new ProductDetailDto(row.id(), row.slug(), row.name(), row.description(), row.category(), row.brand(), images,
                views.stream().map(v -> new SkuDto(v.skuId(), v.code(), v.name(), v.imageUrl(), v.compareAt(), v.options())).toList());
    }

    private record SkuView(long skuId, String code, String name, String imageUrl, BigDecimal compareAt, List<OptionDto> options) {
    }

    /** SKU con algo para vender: la tienda (si tiene stock en línea) y las ofertas, de menor a mayor precio. */
    private Map<Long, List<SkuView>> skus(long company, List<Long> productIds) {
        List<SkuRow> rows = read.skus(productIds);
        Map<Long, List<OfferRow>> offers = read.offers(company, currency, rows.stream().map(SkuRow::skuId).toList()).stream()
                .collect(Collectors.groupingBy(OfferRow::skuId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<SkuView>> result = new LinkedHashMap<>();
        for (SkuRow sku : rows) {
            List<OptionDto> options = new ArrayList<>();
            if (sku.ownAvailable() > 0 && sku.price() != null && sku.price().signum() > 0) {
                options.add(new OptionDto(null, storeName, true, sku.price(), StorefrontRules.shownAvailable(sku.ownAvailable()), null,
                        "NEW"));
            }
            for (OfferRow offer : offers.getOrDefault(sku.skuId(), List.of())) {
                options.add(new OptionDto(offer.offerId(), offer.sellerName(), false, offer.price(),
                        StorefrontRules.shownAvailable(offer.available()), offer.handlingDays(), offer.condition()));
            }
            if (options.isEmpty()) {
                continue;
            }
            options.sort(Comparator.comparing(OptionDto::price));
            result.computeIfAbsent(sku.productId(), k -> new ArrayList<>())
                    .add(new SkuView(sku.skuId(), sku.code(), sku.name(), sku.imageUrl(), sku.compareAtPrice(), options));
        }
        return result;
    }

    // ── Envío ──────────────────────────────────────────────────────────────

    /** Costo de envío para un lugar y un subtotal (para mostrarlo antes de pagar; el definitivo lo calcula el checkout). */
    public ShippingQuoteDto shipping(String state, String city, BigDecimal subtotal) {
        long company = company();
        return quote(company, state, city, subtotal == null || subtotal.signum() < 0 ? BigDecimal.ZERO : subtotal)
                .map(q -> new ShippingQuoteDto(true, q.amount(), q.freeFrom(), q.minDays(), q.maxDays(), q.free()))
                .orElseGet(() -> new ShippingQuoteDto(false, null, null, null, null, false));
    }

    /** Sin tarifas configuradas rige el valor fijo de la configuración; con tarifas, la que aplique o ninguna. */
    java.util.Optional<ShippingRates.Quote> quote(long company, String state, String city, BigDecimal subtotal) {
        List<ShippingRates.Rate> rates = shippingRates.rates(company);
        if (rates.stream().noneMatch(ShippingRates.Rate::active)) {
            return java.util.Optional.of(new ShippingRates.Quote(shipping, null, null, null, false));
        }
        return ShippingRates.match(rates, state, city).map(rate -> ShippingRates.quote(rate, subtotal));
    }

    /** Subtotal de referencia con los precios vigentes (solo para decidir el envío gratis). */
    BigDecimal estimate(long company, List<CartLine> lines) {
        Map<Long, BigDecimal> own = read.ownPrices(lines.stream().filter(l -> l.offerId() == null).map(CartLine::skuId).toList());
        Map<Long, BigDecimal> offered = read.offerPrices(company, currency,
                lines.stream().filter(l -> l.offerId() != null).map(CartLine::offerId).toList());
        BigDecimal total = BigDecimal.ZERO;
        for (CartLine line : lines) {
            BigDecimal price = line.offerId() == null ? own.get(line.skuId()) : offered.get(line.offerId());
            if (price != null) {
                total = total.add(price.multiply(BigDecimal.valueOf(line.quantity())));
            }
        }
        return total;
    }

    // ── Compra ─────────────────────────────────────────────────────────────

    /**
     * Crea la compra (un subpedido por vendedor, reserva stock u ofertas), el enlace de seguimiento y el link de pago,
     * todo en una transacción: si algo falla no queda nada a medias.
     */
    @Transactional
    public CheckoutResultDto checkout(CheckoutRequest request, String clientKey) {
        long company = company();
        if (!checkoutLimiter.tryAcquire(clientKey, clock.instant())) {
            throw new ConflictException("Demasiados intentos de compra. Espera un momento e inténtalo de nuevo.");
        }
        if (request == null) {
            throw new OrderRuleViolation("Faltan los datos de la compra.");
        }
        if (!Boolean.TRUE.equals(request.acceptTerms())) {
            throw new OrderRuleViolation("Debes aceptar los términos y la política de datos.");
        }
        Buyer buyer = StorefrontRules.buyer(request.buyer() == null ? null : new Buyer(request.buyer().name(), request.buyer().docType(),
                request.buyer().docNumber(), request.buyer().email(), request.buyer().phone()));
        Address address = StorefrontRules.address(request.delivery() == null ? null : new Address(request.delivery().recipientName(),
                request.delivery().phone(), request.delivery().address(), request.delivery().addressDetail(), request.delivery().city(),
                request.delivery().state(), request.delivery().notes()), buyer.name(), buyer.phone());
        List<CartLine> lines = StorefrontRules.cart(request.lines() == null ? null : request.lines().stream()
                .map(l -> l == null ? null : new CartLine(l.skuId(), l.offerId(), l.quantity() == null ? 0 : l.quantity())).toList());
        Long method = gateway(request.paymentMethodId());
        ShippingRates.Quote shippingQuote = quote(company, address.state(), address.city(), estimate(company, lines))
                .orElseThrow(() -> new OrderRuleViolation("Por ahora no hacemos envíos a " + address.city() + "."));

        SaveOrderDetailsDto details = new SaveOrderDetailsDto(
                new SaveOrderDetailsDto.Customer(null, "NIT".equals(buyer.docType()) ? "COMPANY" : "PERSON", buyer.docType(),
                        buyer.docNumber(), buyer.name(), buyer.email(), buyer.phone(), address.address(), address.city()),
                new SaveOrderDetailsDto.Delivery("SHIPPING", null, address.recipientName(), address.phone(), address.address(),
                        address.addressDetail(), null, null, null, "Colombia", address.state(), address.city(), null, address.notes(),
                        null, shippingQuote.amount(), null),
                null, "ONLINE", null, null, null, null, null, "Compra en la tienda en línea.", address.notes(), ACTOR_NAME);
        CreatePurchaseRequest create = new CreatePurchaseRequest(details,
                lines.stream().map(l -> new CreatePurchaseRequest.Line(l.skuId(), l.offerId(), l.quantity())).toList());

        return ActorScope.runAs(actor(company), () -> {
            PurchaseDetailDto purchase = purchases.create(create);
            String token = StorefrontRules.newToken();
            access.insert(purchase.id(), company, StorefrontRules.hash(token), buyer.email(), clock.instant());
            PurchaseCheckoutDto link = checkout.start(purchase.id(), new StartPurchaseCheckoutDto(method, buyer.email(),
                    returnUrl(token)));
            return new CheckoutResultDto(token, purchase.number(), link.amount(), link.checkoutUrl());
        });
    }

    /** Estado del pedido por el enlace privado. */
    @Transactional(readOnly = true)
    public OrderViewDto order(String token, String clientKey) {
        long company = company();
        long purchaseId = purchaseOf(token, company, clientKey);
        return ActorScope.runAs(actor(company), () -> view(purchases.get(purchaseId)));
    }

    /** Nuevo link de pago por el saldo (el anterior venció o falló). */
    @Transactional
    public OrderViewDto pay(String token, Long paymentMethodId, String clientKey) {
        long company = company();
        long purchaseId = purchaseOf(token, company, clientKey);
        if (!checkoutLimiter.tryAcquire(clientKey, clock.instant())) {
            throw new ConflictException("Demasiados intentos. Espera un momento e inténtalo de nuevo.");
        }
        Long method = gateway(paymentMethodId);
        return ActorScope.runAs(actor(company), () -> {
            PurchaseDetailDto purchase = purchases.get(purchaseId);
            checkout.start(purchaseId, new StartPurchaseCheckoutDto(method, purchase.customerEmail(), returnUrl(token)));
            return view(purchases.get(purchaseId));
        });
    }

    private OrderViewDto view(PurchaseDetailDto p) {
        List<Long> orderIds = p.subOrders().stream().map(s -> s.orderId()).toList();
        Map<Long, List<LineViewDto>> lines = read.orderLines(orderIds).stream().collect(Collectors.groupingBy(
                StorefrontReadStore.LineRow::orderId, LinkedHashMap::new, Collectors.mapping(
                        l -> new LineViewDto(l.name(), l.skuCode(), l.quantity(), l.unitPrice(), l.total()), Collectors.toList())));
        boolean owed = p.pendingTotal() != null && p.pendingTotal().signum() > 0;
        String link = owed ? read.openCheckout(orderIds, clock.instant()).orElse(null) : null;
        return new OrderViewDto(p.number(), p.status(), p.currency(), p.total(), p.pendingTotal(), p.createdAt(), p.customerName(),
                maskEmail(p.customerEmail()), link, owed && !"CANCELLED".equals(p.status()),
                p.subOrders().stream().map(s -> new SubOrderViewDto(s.orderNumber(), s.external() ? s.sellerName() : storeName,
                        !s.external(), s.state(), s.total(), lines.getOrDefault(s.orderId(), List.of()))).toList());
    }

    private long purchaseOf(String token, long company, String clientKey) {
        if (!lookupLimiter.tryAcquire(clientKey, clock.instant())) {
            throw new ConflictException("Demasiadas consultas. Espera un momento.");
        }
        if (!StorefrontRules.wellFormed(token)) {
            throw new NotFoundException("Pedido no encontrado.");
        }
        return access.find(StorefrontRules.hash(token)).filter(a -> a.companyId() == company)
                .map(StorefrontAccessStore.Access::purchaseId)
                .orElseThrow(() -> new NotFoundException("Pedido no encontrado."));
    }

    private Long gateway(Long paymentMethodId) {
        if (paymentMethodId == null || read.gateways().stream().noneMatch(g -> g.id() == paymentMethodId)) {
            throw new OrderRuleViolation("Elige un medio de pago en línea disponible.");
        }
        return paymentMethodId;
    }

    private String returnUrl(String token) {
        return returnUrl == null ? null : returnUrl.replace("{token}", token);
    }

    private long company() {
        if (!enabled) {
            throw new NotFoundException("La tienda en línea no está habilitada.");
        }
        return companyId;
    }

    static RequestActor actor(long company) {
        return new RequestActor(company, ACTOR_ID, ACTOR_NAME, Set.of(PurchaseService.SALES_VIEW, PurchaseService.SALES_MANAGE));
    }

    static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        int at = email.indexOf('@');
        return email.charAt(0) + "••••" + email.substring(at);
    }
}
