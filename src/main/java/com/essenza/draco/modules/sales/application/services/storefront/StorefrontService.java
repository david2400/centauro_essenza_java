package com.essenza.draco.modules.sales.application.services.storefront;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.BrandDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.BrandProfileDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.CategoryDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.CheckoutRequest;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.CheckoutResultDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.CouponQuoteDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.CouponRequest;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.DeliveryViewDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.LineViewDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.OptionDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.OrderViewDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.PageDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.PaymentOptionDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ProductCardDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.QuickAddDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ReviewDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ReviewPageDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ReviewRequest;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ReviewSummaryDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ReviewableDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.SellerProfileDto;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReviewStore;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.QuickAddDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ReviewDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ReviewPageDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ReviewRequest;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ReviewSummaryDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ReviewableDto;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReviewStore;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ProductDetailDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.SkuDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.StoreInfoDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.SubOrderViewDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ShippingOptionDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ShippingQuoteDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ShippingRequest;
import com.essenza.draco.modules.sales.application.output.gateway.PaymentGateway;
import com.essenza.draco.modules.sales.application.output.gateway.PaymentGateways;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontAccessStore;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReadStore;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReadStore.OfferRow;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReadStore.ProductRow;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReadStore.SkuRef;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReadStore.SkuRow;
import com.essenza.draco.modules.sales.application.services.payment.StartPurchaseCheckoutService;
import com.essenza.draco.modules.sales.application.services.purchase.PurchaseService;
import com.essenza.draco.modules.sales.domain.model.order.CouponRule;
import com.essenza.draco.modules.sales.domain.model.payment.GatewayProvider;
import com.essenza.draco.modules.sales.domain.model.order.DiscountType;
import com.essenza.draco.modules.sales.domain.model.order.OrderPricing;
import com.essenza.draco.modules.sales.domain.model.order.OrderRuleViolation;
import com.essenza.draco.modules.sales.domain.model.storefront.RateLimiter;
import com.essenza.draco.modules.sales.domain.model.storefront.StorefrontRules;
import com.essenza.draco.modules.sales.domain.model.storefront.StorefrontRules.Address;
import com.essenza.draco.modules.sales.domain.model.storefront.StorefrontRules.Buyer;
import com.essenza.draco.modules.sales.domain.model.storefront.StorefrontRules.CartLine;
import com.essenza.draco.shared.common.logistics.ShippingQuotes;
import com.essenza.draco.shared.common.promotions.Coupons;
import com.essenza.draco.shared.common.promotions.Coupons.CouponTerms;
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
    /** Vigencia de los cupones: la misma zona que las órdenes del panel. */
    static final ZoneId ZONE = ZoneId.of("America/Bogota");
    static final Set<String> SORTS = Set.of("NEWEST", "NAME", "PRICE_ASC", "PRICE_DESC");

    private final StorefrontReadStore read;
    private final StorefrontAccessStore access;
    private final ShippingQuotes shippingQuotes;
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
    private final RateLimiter quoteLimiter;
    private final RateLimiter couponLimiter;
    private final RateLimiter reviewLimiter = new RateLimiter(10, Duration.ofHours(1), 10_000);
    private StorefrontReviewStore reviews;
    private Coupons coupons;
    private PaymentGateways gateways;

    @Autowired
    public StorefrontService(StorefrontReadStore read, StorefrontAccessStore access, ShippingQuotes shippingQuotes,
                             PurchaseService purchases, StartPurchaseCheckoutService checkout,
                             @Value("${essenza.storefront.enabled:false}") boolean enabled,
                             @Value("${essenza.storefront.company-id:}") String companyId,
                             @Value("${essenza.storefront.store-name:Tienda en línea}") String storeName,
                             @Value("${essenza.marketplace.catalog-currency:COP}") String currency,
                             @Value("${essenza.storefront.return-url:}") String returnUrl,
                             @Value("${essenza.storefront.shipping-amount:0}") BigDecimal shipping,
                             @Value("${essenza.storefront.checkouts-per-hour:10}") int checkoutsPerHour) {
        this(read, access, shippingQuotes, purchases, checkout, Clock.systemUTC(), enabled, companyId, storeName, currency, returnUrl, shipping,
                checkoutsPerHour);
    }

    public StorefrontService(StorefrontReadStore read, StorefrontAccessStore access, ShippingQuotes shippingQuotes,
                             PurchaseService purchases, StartPurchaseCheckoutService checkout, Clock clock, boolean enabled, String companyId, String storeName,
                             String currency, String returnUrl, BigDecimal shipping, int checkoutsPerHour) {
        this.read = read;
        this.access = access;
        this.shippingQuotes = shippingQuotes;
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
        this.quoteLimiter = new RateLimiter(300, Duration.ofMinutes(10), 10_000);
        this.couponLimiter = new RateLimiter(30, Duration.ofMinutes(10), 10_000);
    }

    /**
     * Pasarelas del servidor (opcional): la tienda solo ofrece los medios cuya pasarela tiene llaves. Sin esto un medio
     * activo sin llaves aparecería en el pago y fallaría al crear el link.
     */
    @Autowired(required = false)
    public void setGateways(PaymentGateways gateways) {
        this.gateways = gateways;
    }

    /** Reseñas (opcional: sin el adaptador la tienda funciona igual, sin calificaciones). */
    @Autowired(required = false)
    public void setReviews(StorefrontReviewStore reviews) {
        this.reviews = reviews;
    }

    private List<StorefrontReadStore.PaymentOption> payableGateways() {
        List<StorefrontReadStore.PaymentOption> all = read.gateways();
        if (gateways == null) {
            return all;
        }
        return all.stream().filter(g -> GatewayProvider.tryParse(g.provider()).flatMap(gateways::find)
                .map(PaymentGateway::configured).orElse(false)).toList();
    }

    /** Cupones de Promociones (opcional: sin el módulo, la tienda no acepta cupones). */
    @Autowired(required = false)
    public void setCoupons(Coupons coupons) {
        this.coupons = coupons;
    }

    // ── Catálogo ───────────────────────────────────────────────────────────

    public StoreInfoDto info() {
        long company = company();
        return new StoreInfoDto(storeName, currency, payableGateways().stream()
                .map(g -> new PaymentOptionDto(g.id(), g.name(), g.provider())).toList(),
                StorefrontRules.DOC_TYPES.stream().sorted().toList(), shipping);
    }

    public List<CategoryDto> categories() {
        return read.categories(company(), currency).stream().map(c -> new CategoryDto(c.id(), c.name(), c.slug(), c.products())).toList();
    }

    public List<BrandDto> brands() {
        return read.brands(company(), currency).stream().map(b -> new BrandDto(b.id(), b.name(), b.products(), b.slug())).toList();
    }

    /** Página pública de una marca con productos a la venta. */
    public BrandProfileDto brand(String slugOrId) {
        long company = company();
        if (slugOrId == null || !slugOrId.matches("[A-Za-z0-9-]{1,160}")) {
            throw new NotFoundException("Marca no encontrada.");
        }
        StorefrontReadStore.BrandProfileRow row = read.brand(company, currency, slugOrId)
                .orElseThrow(() -> new NotFoundException("Marca no encontrada."));
        return new BrandProfileDto(row.id(), row.name(), row.slug(), blankToNull(row.description()), row.products());
    }

    /**
     * Página pública de un vendedor: solo externos aprobados de la empresa de la tienda (los demás responden 404, igual
     * que uno que no existe). No expone documento, correo, teléfono ni dirección.
     */
    public SellerProfileDto seller(Long sellerId) {
        long company = company();
        if (sellerId == null || sellerId <= 0) {
            throw new NotFoundException("Vendedor no encontrado.");
        }
        StorefrontReadStore.SellerRow row = read.seller(company, currency, sellerId)
                .orElseThrow(() -> new NotFoundException("Vendedor no encontrado."));
        return new SellerProfileDto(row.id(), row.name(), row.city(), row.description(), StorefrontRules.publicWebsite(row.website()),
                row.approvedAt() == null ? null : row.approvedAt().getYear(), row.products());
    }

    public PageDto<ProductCardDto> search(String q, Long categoryId, String sort, Integer page, Integer size) {
        return search(q, categoryId, null, null, null, sort, page, size);
    }

    /** Catálogo con filtros. {@code minPrice}/{@code maxPrice}: sobre el "desde" del producto, en la moneda de la tienda. */
    public PageDto<ProductCardDto> search(String q, Long categoryId, Long brandId, BigDecimal minPrice, BigDecimal maxPrice,
                                          String sort, Integer page, Integer size) {
        return search(q, categoryId, brandId, minPrice, maxPrice, null, sort, page, size);
    }

    /** Igual, y además {@code sellerId}: solo productos con ofertas a la venta de ese vendedor. */
    public PageDto<ProductCardDto> search(String q, Long categoryId, Long brandId, BigDecimal minPrice, BigDecimal maxPrice,
                                          Long sellerId, String sort, Integer page, Integer size) {
        long company = company();
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)) {
            throw new OrderRuleViolation("El precio no puede ser negativo.");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new OrderRuleViolation("El precio mínimo no puede ser mayor que el máximo.");
        }
        int p = page == null || page < 0 ? 0 : Math.min(page, 1000);
        int s = size == null || size <= 0 ? 24 : Math.min(size, MAX_PAGE);
        String order = sort == null ? "NEWEST" : sort.trim().toUpperCase(Locale.ROOT);
        if (!SORTS.contains(order)) {
            throw new OrderRuleViolation("Orden no válido: " + sort + ".");
        }
        StorefrontReadStore.ProductPage found = read.search(company, currency, new StorefrontReadStore.SearchFilter(
                StorefrontRules.likePattern(q), categoryId, brandId, minPrice, maxPrice, sellerId), order, p * s, s);
        return new PageDto<>(cards(company, found.ids()), p, s, found.total());
    }

    /** Tope de {@link #cards(List)}: una página de favoritos o de vistos recientemente. */
    static final int MAX_CARDS = 60;

    /**
     * Tarjetas de los productos pedidos (favoritos, vistos), en el mismo orden. Los que ya no se venden se omiten, igual
     * que en el catálogo.
     */
    public List<ProductCardDto> cards(List<Long> ids) {
        long company = company();
        List<Long> clean = ids == null ? List.of() : ids.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (clean.size() > MAX_CARDS) {
            throw new OrderRuleViolation("Se pueden pedir hasta " + MAX_CARDS + " productos.");
        }
        return clean.isEmpty() ? List.of() : cards(company, clean);
    }

    private List<ProductCardDto> cards(long company, List<Long> ids) {
        Map<Long, ProductRow> rows = read.products(ids);
        Map<Long, List<SkuView>> skus = skus(company, ids);
        Map<Long, List<String>> images = read.images(ids);
        Map<Long, StorefrontReviewStore.Summary> ratings = reviews == null ? Map.of() : reviews.summaries(ids);
        List<ProductCardDto> cards = new ArrayList<>();
        for (Long id : ids) {
            ProductRow row = rows.get(id);
            List<SkuView> views = skus.getOrDefault(id, List.of());
            if (row == null || views.isEmpty()) {
                continue; // ya no se vende (o cambió entre la búsqueda y la lectura)
            }
            BigDecimal from = views.stream().flatMap(v -> v.options().stream()).map(OptionDto::price).min(Comparator.naturalOrder())
                    .orElse(null);
            BigDecimal compareAt = views.stream().map(SkuView::compareAt).filter(c -> c != null && from != null && c.compareTo(from) > 0)
                    .max(Comparator.naturalOrder()).orElse(null);
            int sellers = (int) views.stream().flatMap(v -> v.options().stream()).map(o -> o.store() ? "" : o.sellerName()).distinct().count();
            String image = row.imageUrl() != null ? row.imageUrl()
                    : views.stream().map(SkuView::imageUrl).filter(u -> u != null).findFirst().orElse(null);
            String hover = images.getOrDefault(id, List.of()).stream().filter(u -> !u.equals(image)).findFirst().orElse(null);
            OptionDto cheapest = views.stream().flatMap(v -> v.options().stream()).min(Comparator.comparing(OptionDto::price))
                    .orElse(null);
            QuickAddDto quick = null;
            if (views.size() == 1 && cheapest != null) {
                SkuView only = views.get(0);
                quick = new QuickAddDto(only.skuId(), cheapest.offerId(), cheapest.sellerName(), cheapest.price(),
                        cheapest.available(), only.name());
            }
            cards.add(new ProductCardDto(row.id(), row.slug(), row.name(), image, row.category(), row.brand(), from, compareAt, sellers,
                    hover, cheapest == null ? 0 : cheapest.available(), quick,
                    ratings.containsKey(id) ? ratings.get(id).average() : null, ratings.containsKey(id) ? ratings.get(id).count() : 0));
        }
        return cards;
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
                views.stream().map(v -> new SkuDto(v.skuId(), v.code(), v.name(), v.imageUrl(), v.compareAt(), v.options())).toList(),
                row.brandSlug());
    }

    // ── Reseñas ────────────────────────────────────────────────────────────

    static final int REVIEW_PAGE = 10;
    /** Solo quien ya recibió el pedido puede calificar (compra verificada y producto en sus manos). */
    private static final java.util.Set<String> REVIEWABLE = java.util.Set.of("DELIVERED");

    /** Reseñas aprobadas de un producto (más recientes primero) con su resumen por estrellas. */
    public ReviewPageDto reviews(String slugOrId, int page, int size) {
        company();
        if (slugOrId == null || !slugOrId.matches("[A-Za-z0-9-]{1,160}")) {
            throw new NotFoundException("Producto no encontrado.");
        }
        long id = read.productBySlug(slugOrId).orElseThrow(() -> new NotFoundException("Producto no encontrado."));
        int p = Math.max(0, Math.min(page, 500));
        int s = Math.max(1, Math.min(size <= 0 ? REVIEW_PAGE : size, 30));
        if (reviews == null) {
            return new ReviewPageDto(new ReviewSummaryDto(null, 0, List.of(0L, 0L, 0L, 0L, 0L)), List.of(), p, s, 0);
        }
        Map<Integer, Long> stars = reviews.distribution(id);
        long total = stars.values().stream().mapToLong(Long::longValue).sum();
        BigDecimal average = total == 0 ? null : BigDecimal.valueOf(stars.entrySet().stream()
                .mapToLong(e -> e.getKey() * e.getValue()).sum()).divide(BigDecimal.valueOf(total), 1, java.math.RoundingMode.HALF_UP);
        List<Long> byStars = java.util.stream.IntStream.rangeClosed(1, 5).map(i -> 6 - i)
                .mapToObj(star -> stars.getOrDefault(star, 0L)).toList();
        List<ReviewDto> items = reviews.approved(id, p * s, s).stream()
                .map(r -> new ReviewDto(r.id(), StorefrontRules.publicName(r.customerName()), r.rating(), blankToNull(r.title()), r.comment(),
                        r.date() == null ? null : r.date().toLocalDate(), r.verified(), blankToNull(r.storeResponse()),
                        r.storeResponseAt() == null ? null : r.storeResponseAt().toLocalDate()))
                .toList();
        return new ReviewPageDto(new ReviewSummaryDto(average, total, byStars), items, p, s, total);
    }

    /** Productos de la compra que el comprador puede calificar (la compra debe estar entregada), con el estado de su reseña. */
    public List<ReviewableDto> reviewable(String token, String clientKey) {
        long company = company();
        long purchaseId = purchaseOf(token, company, clientKey);
        if (reviews == null || !paidPurchase(company, purchaseId)) {
            return List.of();
        }
        Map<Long, String> products = reviews.purchasedProducts(purchaseId);
        Map<Long, String> statuses = reviews.buyer(purchaseId).map(b -> reviews.reviewStatuses(b.email(), products.keySet()))
                .orElse(Map.of());
        Map<Long, String> slugs = reviews.slugs(products.keySet());
        return products.entrySet().stream().map(e -> new ReviewableDto(e.getKey(), e.getValue(), statuses.containsKey(e.getKey()),
                statuses.get(e.getKey()), slugs.get(e.getKey()))).toList();
    }

    /**
     * Calificar un producto comprado, desde el enlace privado del pedido. Una reseña por producto y comprador; entra
     * pendiente y se publica cuando el panel la aprueba.
     */
    @Transactional
    public void review(String token, ReviewRequest request, String clientKey) {
        long company = company();
        if (!reviewLimiter.tryAcquire(clientKey, clock.instant())) {
            throw new ConflictException("Demasiadas reseñas seguidas. Inténtalo más tarde.");
        }
        long purchaseId = purchaseOf(token, company, clientKey);
        if (reviews == null) {
            throw new OrderRuleViolation("Las reseñas no están disponibles en este momento.");
        }
        if (request == null || request.productId() == null) {
            throw new OrderRuleViolation("Elige el producto que quieres calificar.");
        }
        int rating = request.rating() == null ? 0 : request.rating();
        if (rating < 1 || rating > 5) {
            throw new OrderRuleViolation("La calificación va de 1 a 5 estrellas.");
        }
        String comment = request.comment() == null ? "" : request.comment().strip();
        if (comment.length() < 10 || comment.length() > 2000) {
            throw new OrderRuleViolation("Cuéntanos tu experiencia en al menos 10 caracteres (máximo 2000).");
        }
        String title = blankToNull(request.title() == null ? null : request.title().strip());
        if (title != null && title.length() > 120) {
            throw new OrderRuleViolation("El título puede tener hasta 120 caracteres.");
        }
        if (!paidPurchase(company, purchaseId)) {
            throw new OrderRuleViolation("Podrás calificar tus productos cuando recibas tu pedido.");
        }
        if (!reviews.purchasedProducts(purchaseId).containsKey(request.productId())) {
            throw new NotFoundException("Ese producto no está en tu pedido.");
        }
        StorefrontReviewStore.Buyer buyer = reviews.buyer(purchaseId)
                .orElseThrow(() -> new OrderRuleViolation("No encontramos los datos del comprador de este pedido."));
        if (reviews.reviewedBy(buyer.email(), List.of(request.productId())).contains(request.productId())) {
            throw new ConflictException("Ya calificaste este producto. Gracias por tu opinión.");
        }
        reviews.insert(request.productId(), buyer, rating, title, comment, clock.instant());
    }

    private boolean paidPurchase(long company, long purchaseId) {
        return ActorScope.runAs(actor(company), () -> REVIEWABLE.contains(purchases.get(purchaseId).status()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
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
                        StorefrontRules.shownAvailable(offer.available()), offer.handlingDays(), offer.condition(), offer.sellerId()));
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

    // ── Cupón ──────────────────────────────────────────────────────────────

    /**
     * Vista previa de un cupón para el carrito. Los cupones son de la tienda: aplican solo a lo que ella vende (su
     * subpedido), con las mismas reglas que una orden del panel (vigencia, usos, compra mínima, productos y
     * categorías). No reserva nada; el checkout lo vuelve a validar. Límite propio para no adivinar códigos.
     */
    public CouponQuoteDto coupon(CouponRequest request, String clientKey) {
        company();
        if (!couponLimiter.tryAcquire(clientKey, clock.instant())) {
            throw new ConflictException("Demasiados intentos con cupones. Espera un momento e inténtalo de nuevo.");
        }
        String code = request == null || request.code() == null ? "" : request.code().strip();
        if (code.isEmpty() || code.length() > 50) {
            throw new OrderRuleViolation("Escribe el código del cupón.");
        }
        List<CartLine> lines = StorefrontRules.cart(request.lines() == null ? null : request.lines().stream()
                .map(l -> l == null ? null : new CartLine(l.skuId(), l.offerId(), l.quantity() == null ? 0 : l.quantity())).toList());
        CouponTerms terms = coupons == null ? null : coupons.findByCode(code).orElse(null);
        if (terms == null) {
            return new CouponQuoteDto(false, code, BigDecimal.ZERO, false, "El cupón " + code + " no existe.");
        }
        List<CartLine> own = lines.stream().filter(l -> l.offerId() == null).toList();
        if (own.isEmpty()) {
            return new CouponQuoteDto(false, terms.code(), BigDecimal.ZERO, false,
                    "Los cupones aplican a los productos que vende la tienda; tu carrito no tiene ninguno.");
        }
        Map<Long, SkuRef> refs = read.skuRefs(own.stream().map(CartLine::skuId).distinct().toList());
        List<OrderPricing.PricedLine> priced = new ArrayList<>();
        long lineId = 0;
        for (CartLine line : own) {
            SkuRef ref = refs.get(line.skuId());
            if (ref == null || ref.price() == null) {
                continue; // ya no se vende: el checkout lo rechaza con su mensaje
            }
            priced.add(new OrderPricing.PricedLine(++lineId, ref.productId(), ref.categoryId(),
                    ref.price().multiply(BigDecimal.valueOf(line.quantity()))));
        }
        CouponRule rule = new CouponRule(terms.id(), terms.code(), terms.discountType(),
                terms.discountValue() == null ? BigDecimal.ZERO : terms.discountValue(), terms.maximumDiscount(), terms.minimumOrder(),
                terms.validFrom(), terms.validUntil(), terms.active(), terms.usageLimit(), terms.usageCount(), terms.products(),
                terms.excludedProducts(), terms.categories(), terms.excludedCategories());
        try {
            OrderPricing.Result result = OrderPricing.price(priced, DiscountType.COUPON, null, rule, false, BigDecimal.ZERO,
                    LocalDateTime.now(clock.withZone(ZONE)));
            return new CouponQuoteDto(true, terms.code(), result.discountAmount(), rule.freeShipping(), null);
        } catch (OrderRuleViolation e) {
            return new CouponQuoteDto(false, terms.code(), BigDecimal.ZERO, false, e.getMessage());
        }
    }

    // ── Envío ──────────────────────────────────────────────────────────────

    /**
     * Opciones de envío para el destino y el carrito (peso y medidas del catálogo, envío gratis según los precios
     * vigentes). Solo para mostrarlas antes de pagar: el checkout vuelve a cotizar y nunca toma el valor del navegador.
     */
    public ShippingQuoteDto shipping(ShippingRequest request, String clientKey) {
        long company = company();
        if (!quoteLimiter.tryAcquire(clientKey, clock.instant())) {
            throw new ConflictException("Demasiadas consultas. Espera un momento e inténtalo de nuevo.");
        }
        if (request == null) {
            throw new OrderRuleViolation("Faltan el destino y el carrito.");
        }
        List<CartLine> lines = StorefrontRules.cart(request.lines() == null ? null : request.lines().stream()
                .map(l -> l == null ? null : new CartLine(l.skuId(), l.offerId(), l.quantity() == null ? 0 : l.quantity())).toList());
        List<ShippingOptionDto> options = shippingOptions(company, request.countryId(), request.stateId(), request.cityId(),
                label(request.city(), request.state()), lines);
        return new ShippingQuoteDto(!options.isEmpty(), options);
    }

    /**
     * Cotiza con el tarifario de logística. Si la empresa aún no tiene tarifario, rige el valor fijo de la
     * configuración ({@code essenza.storefront.shipping-amount}); con tarifario, lista vacía = sin cobertura.
     */
    List<ShippingOptionDto> shippingOptions(long company, Long countryId, Long stateId, Long cityId, String label, List<CartLine> lines) {
        if (countryId == null || stateId == null || cityId == null) {
            throw new OrderRuleViolation("Elige el departamento y la ciudad de entrega.");
        }
        if (!shippingQuotes.hasTariff(company)) {
            return List.of(new ShippingOptionDto(0L, null, null, shipping, shipping.signum() == 0, null, null, null));
        }
        BigDecimal subtotal = estimate(company, lines);
        ShippingQuotes.QuoteResult result = shippingQuotes.quote(company, new ShippingQuotes.QuoteRequest(null, countryId, stateId,
                cityId, label, lines.stream().map(l -> new ShippingQuotes.Item(l.skuId(), null, l.quantity())).toList(), subtotal,
                subtotal, null, null));
        return result.options().stream().map(o -> new ShippingOptionDto(o.rateCardId(), o.carrierId(), o.carrierName(), o.total(), o.free(),
                o.minDays(), o.maxDays(), o.deliveryTo())).toList();
    }

    private static String label(String city, String state) {
        String c = city == null ? "" : city.trim();
        String s = state == null ? "" : state.trim();
        String joined = c.isEmpty() ? s : s.isEmpty() ? c : c + ", " + s;
        return joined.isEmpty() ? null : joined.substring(0, Math.min(joined.length(), 250));
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
        var delivery = request.delivery();
        List<ShippingOptionDto> options = shippingOptions(company, delivery.countryId(), delivery.stateId(), delivery.cityId(),
                label(address.city(), address.state()), lines);
        if (options.isEmpty()) {
            throw new OrderRuleViolation("Por ahora no hacemos envíos a " + address.city() + ".");
        }
        // Se vuelve a cotizar aquí: el valor del envío nunca sale del navegador, solo la opción elegida.
        ShippingOptionDto shippingOption = request.shippingOptionId() == null ? options.get(0) : options.stream()
                .filter(o -> o.id().equals(request.shippingOptionId())).findFirst()
                .orElseThrow(() -> new ConflictException("La opción de envío elegida ya no está disponible. Revisa el envío e inténtalo de nuevo."));
        Long carrierId = shippingOption.carrierId();

        SaveOrderDetailsDto details = new SaveOrderDetailsDto(
                new SaveOrderDetailsDto.Customer(null, "NIT".equals(buyer.docType()) ? "COMPANY" : "PERSON", buyer.docType(),
                        buyer.docNumber(), buyer.name(), buyer.email(), buyer.phone(), address.address(), address.city()),
                new SaveOrderDetailsDto.Delivery("SHIPPING", null, address.recipientName(), address.phone(), address.address(),
                        address.addressDetail(), delivery.countryId(), delivery.stateId(), delivery.cityId(),
                        delivery.country() == null || delivery.country().isBlank() ? "Colombia" : delivery.country().trim(),
                        address.state(), address.city(), null, address.notes(), carrierId, shippingOption.amount(),
                        shippingOption.deliveryTo()),
                couponOf(request.couponCode()), "ONLINE", null, null, null, null, null, "Compra en la tienda en línea.", address.notes(),
                ACTOR_NAME);
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

    /** Cupón del checkout: va con el subpedido de la tienda (Ventas lo valida y lo descuenta al crear la orden). */
    private SaveOrderDetailsDto.Discount couponOf(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String clean = code.strip();
        if (clean.length() > 50) {
            throw new OrderRuleViolation("El código del cupón no es válido.");
        }
        return new SaveOrderDetailsDto.Discount("COUPON", null, clean);
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
                        l -> new LineViewDto(l.name(), l.skuCode(), l.quantity(), l.unitPrice(), l.total(), l.skuId(), l.offerId(),
                                l.productSlug()), Collectors.toList())));
        Map<Long, StorefrontReadStore.DeliveryRow> deliveries = read.deliveries(orderIds);
        boolean owed = p.pendingTotal() != null && p.pendingTotal().signum() > 0;
        String link = owed ? read.openCheckout(orderIds, clock.instant()).orElse(null) : null;
        return new OrderViewDto(p.number(), p.status(), p.currency(), p.total(), p.pendingTotal(), p.createdAt(), p.customerName(),
                maskEmail(p.customerEmail()), link, owed && !"CANCELLED".equals(p.status()),
                p.subOrders().stream().map(s -> new SubOrderViewDto(s.orderNumber(), s.external() ? s.sellerName() : storeName,
                        !s.external(), s.state(), s.total(), lines.getOrDefault(s.orderId(), List.of()),
                        delivery(deliveries.get(s.orderId())))).toList());
    }

    private static DeliveryViewDto delivery(StorefrontReadStore.DeliveryRow row) {
        return row == null ? null : new DeliveryViewDto(row.pickup(), row.carrier(), row.trackingNumber(), row.pickupStore(),
                row.pickupAddress(), row.pickupHours());
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
        if (paymentMethodId == null || payableGateways().stream().noneMatch(g -> g.id() == paymentMethodId)) {
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
