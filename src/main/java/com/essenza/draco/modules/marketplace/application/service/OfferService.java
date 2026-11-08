package com.essenza.draco.modules.marketplace.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.marketplace.application.MarketplacePermissions;
import com.essenza.draco.modules.marketplace.application.dto.OfferDtos.CatalogSkuDto;
import com.essenza.draco.modules.marketplace.application.dto.OfferDtos.OfferDetailDto;
import com.essenza.draco.modules.marketplace.application.dto.OfferDtos.OfferEventDto;
import com.essenza.draco.modules.marketplace.application.dto.OfferDtos.OfferStatsDto;
import com.essenza.draco.modules.marketplace.application.dto.OfferDtos.OfferSummaryDto;
import com.essenza.draco.modules.marketplace.application.dto.OfferDtos.OfferTransitionRequest;
import com.essenza.draco.modules.marketplace.application.dto.OfferDtos.SaveOfferRequest;
import com.essenza.draco.modules.marketplace.application.port.OfferStore;
import com.essenza.draco.modules.marketplace.application.port.OfferStore.CatalogCopy;
import com.essenza.draco.modules.marketplace.application.port.OfferStore.NewOffer;
import com.essenza.draco.modules.marketplace.application.port.OfferStore.OfferFilter;
import com.essenza.draco.modules.marketplace.application.port.OfferStore.OfferRow;
import com.essenza.draco.modules.marketplace.application.port.SellerStore;
import com.essenza.draco.modules.marketplace.application.port.SellerStore.SellerRow;
import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.domain.offer.OfferEnums.Action;
import com.essenza.draco.modules.marketplace.domain.offer.OfferEnums.Condition;
import com.essenza.draco.modules.marketplace.domain.offer.OfferEnums.Fulfillment;
import com.essenza.draco.modules.marketplace.domain.offer.OfferEnums.Status;
import com.essenza.draco.modules.marketplace.domain.offer.OfferTerms;
import com.essenza.draco.modules.marketplace.domain.offer.OfferWorkflow;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.Kind;
import com.essenza.draco.shared.common.catalog.SkuPricing;
import com.essenza.draco.shared.common.catalog.SkuPricing.SkuSnapshot;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Ofertas de vendedores externos sobre SKUs del catálogo y su moderación. El producto y el SKU
 * se leen del catálogo por el puerto compartido {@link SkuPricing} (no se duplican). El vendedor
 * propio vende con el precio del SKU y el inventario existentes, sin ofertas.
 */
@Service
public class OfferService {

    private final OfferStore offers;
    private final SellerStore sellers;
    private final SkuPricing skus;
    private final ActorProvider actors;
    private final Clock clock;
    private final String catalogCurrency;
    private final boolean fourEyes;
    private final ConsignmentService consignment;

    /** Para pruebas sin consignación. */
    public OfferService(OfferStore offers, SellerStore sellers, SkuPricing skus, ActorProvider actors, Clock marketplaceClock,
                        String catalogCurrency, boolean fourEyes) {
        this(offers, sellers, skus, actors, marketplaceClock, catalogCurrency, fourEyes, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OfferService(OfferStore offers, SellerStore sellers, SkuPricing skus, ActorProvider actors, Clock marketplaceClock,
                        @Value("${essenza.marketplace.catalog-currency:COP}") String catalogCurrency,
                        @Value("${essenza.marketplace.offers.four-eyes:false}") boolean fourEyes,
                        ConsignmentService consignment) {
        this.offers = offers;
        this.sellers = sellers;
        this.skus = skus;
        this.actors = actors;
        this.clock = marketplaceClock;
        this.catalogCurrency = catalogCurrency;
        this.fourEyes = fourEyes;
        this.consignment = consignment;
    }

    @Transactional(readOnly = true)
    public Page<OfferSummaryDto> search(OfferFilter filter, Pageable pageable) {
        RequestActor actor = actors.current().requireAny(MarketplacePermissions.OFFERS_VIEW, MarketplacePermissions.OFFERS_MODERATE);
        Page<OfferRow> page = offers.search(actor.companyId(), filter, pageable);
        return new PageImpl<>(page.getContent().stream().map(OfferService::summary).toList(), pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public OfferStatsDto stats(Long sellerId) {
        RequestActor actor = actors.current().requireAny(MarketplacePermissions.OFFERS_VIEW, MarketplacePermissions.OFFERS_MODERATE);
        Map<String, Long> counts = offers.countByStatus(actor.companyId(), sellerId);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new OfferStatsDto(total, counts.getOrDefault("DRAFT", 0L), counts.getOrDefault("PENDING_REVIEW", 0L),
                counts.getOrDefault("ACTIVE", 0L), counts.getOrDefault("REJECTED", 0L), counts.getOrDefault("PAUSED", 0L),
                counts.getOrDefault("CLOSED", 0L));
    }

    @Transactional(readOnly = true)
    public OfferDetailDto get(long id) {
        RequestActor actor = actors.current().requireAny(MarketplacePermissions.OFFERS_VIEW, MarketplacePermissions.OFFERS_MODERATE);
        return detail(actor, load(actor, id, false));
    }

    @Transactional(readOnly = true)
    public List<OfferEventDto> events(long id) {
        RequestActor actor = actors.current().requireAny(MarketplacePermissions.OFFERS_VIEW, MarketplacePermissions.OFFERS_MODERATE);
        load(actor, id, false);
        return offers.events(actor.companyId(), id, 300);
    }

    @Transactional
    public OfferDetailDto create(SaveOfferRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.OFFERS_MANAGE);
        MarketplaceRuleViolation.check(request.sellerId() != null, "Elige el vendedor.");
        MarketplaceRuleViolation.check(request.skuId() != null, "Elige el SKU del catálogo.");
        // El vendedor se busca en la empresa del token: un id de otra empresa "no existe".
        SellerRow seller = sellers.find(actor.companyId(), request.sellerId(), true)
                .orElseThrow(() -> new NotFoundException("Vendedor " + request.sellerId() + " no encontrado."));
        MarketplaceRuleViolation.check(seller.profile().kind() == Kind.EXTERNAL,
                "El vendedor propio vende con el precio y el inventario del catálogo; las ofertas son de vendedores externos.");
        MarketplaceRuleViolation.check(!"CLOSED".equals(seller.status().name()), "Un vendedor cerrado no publica ofertas.");
        SkuSnapshot sku = sku(request.skuId());
        OfferTerms terms = platformAvailable(actor, seller.id(), request.skuId(), terms(request, null), null);
        Instant now = clock.instant();
        long id = offers.insert(actor.companyId(), new NewOffer(seller.id(), request.skuId(), copy(sku), terms), actor.userId(), now);
        offers.addEvent(actor.companyId(), id, "CREATED", null, Status.DRAFT, null,
                sku.code() + " · " + terms.currency() + " " + terms.price().toPlainString(), actor.userId(), actor.displayName(), now);
        return detail(actor, load(actor, id, false));
    }

    @Transactional
    public OfferDetailDto update(long id, SaveOfferRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.OFFERS_MANAGE);
        OfferRow row = load(actor, id, true);
        checkLock(row, request.lockVersion());
        OfferTerms terms = platformAvailable(actor, row.sellerId(), row.skuId(), terms(request, row.terms()), row.terms());
        List<String> changed = OfferWorkflow.checkEdit(row.status(), row.terms(), terms);
        if (changed.isEmpty()) {
            return detail(actor, row);
        }
        Instant now = clock.instant();
        CatalogCopy catalog = copyOrKeep(row);
        if (!offers.updateTerms(actor.companyId(), id, row.lockVersion(), terms, catalog, actor.userId(), now)) {
            throw stale();
        }
        offers.addEvent(actor.companyId(), id, "EDITED", null, null, null, describe(row.terms(), terms, changed),
                actor.userId(), actor.displayName(), now);
        return detail(actor, load(actor, id, false));
    }

    @Transactional
    public OfferDetailDto transition(long id, OfferTransitionRequest request) {
        RequestActor actor = actors.current();
        Action action = PlanEnums.parse(Action.class, request.action(), "La acción");
        actor.require(permissionFor(action));
        OfferRow row = load(actor, id, true);
        checkLock(row, request.lockVersion());
        SkuSnapshot sku = skuOrNull(row.skuId());
        OfferWorkflow.Context context = new OfferWorkflow.Context("APPROVED".equals(row.sellerStatus()),
                sku != null && sku.sellable(), actor.userId().equals(row.createdBy()), fourEyes);
        Status next = OfferWorkflow.transition(row.status(), action, request.reason(), context);
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        Instant now = clock.instant();
        if (!offers.updateStatus(actor.companyId(), id, row.lockVersion(), next, reason, action == Action.APPROVE,
                sku == null ? row.catalog() : copy(sku), actor.userId(), now)) {
            throw stale();
        }
        offers.addEvent(actor.companyId(), id, action.name(), row.status(), next, reason, null, actor.userId(),
                actor.displayName(), now);
        return detail(actor, load(actor, id, false));
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    static String permissionFor(Action action) {
        return OfferWorkflow.moderation(action) ? MarketplacePermissions.OFFERS_MODERATE : MarketplacePermissions.OFFERS_MANAGE;
    }

    private OfferRow load(RequestActor actor, long id, boolean forUpdate) {
        return offers.find(actor.companyId(), id, forUpdate)
                .orElseThrow(() -> new NotFoundException("Oferta " + id + " no encontrada."));
    }

    private SkuSnapshot sku(long skuId) {
        return skus.resolve(null, skuId);
    }

    /** Datos vigentes del SKU; null si ya no existe en el catálogo. */
    private SkuSnapshot skuOrNull(long skuId) {
        try {
            return skus.resolve(null, skuId);
        } catch (NotFoundException ex) {
            return null;
        }
    }

    private CatalogCopy copyOrKeep(OfferRow row) {
        SkuSnapshot sku = skuOrNull(row.skuId());
        return sku == null ? row.catalog() : copy(sku);
    }

    private static CatalogCopy copy(SkuSnapshot sku) {
        String name = sku.name() == null ? null : sku.name().length() > 300 ? sku.name().substring(0, 300) : sku.name();
        return new CatalogCopy(sku.productId(), sku.code(), name);
    }

    private OfferTerms terms(SaveOfferRequest r, OfferTerms previous) {
        return OfferTerms.of(
                r.currency() == null || r.currency().isBlank() ? (previous != null ? previous.currency() : catalogCurrency) : r.currency(),
                r.price(),
                r.availableQuantity(),
                r.handlingDays(),
                r.condition() == null || r.condition().isBlank() ? null : PlanEnums.parse(Condition.class, r.condition(), "La condición"),
                r.fulfillment() == null || r.fulfillment().isBlank() ? null : PlanEnums.parse(Fulfillment.class, r.fulfillment(), "El despacho"),
                r.sellerSku());
    }

    /**
     * Despacho de la plataforma: el disponible no se escribe, sale de la consignación (saldo menos
     * lo reservado). Si la oferta ya despachaba desde la plataforma, conserva el disponible que
     * lleva la consignación.
     */
    private OfferTerms platformAvailable(RequestActor actor, long sellerId, long skuId, OfferTerms terms, OfferTerms previous) {
        if (terms.fulfillment() != Fulfillment.PLATFORM) {
            return terms;
        }
        if (previous != null && previous.fulfillment() == Fulfillment.PLATFORM) {
            return terms.withAvailableQuantity(previous.availableQuantity());
        }
        MarketplaceRuleViolation.check(consignment != null, "La consignación no está disponible en este entorno.");
        return terms.withAvailableQuantity(consignment.availableFor(actor.companyId(), sellerId, skuId));
    }

    private OfferDetailDto detail(RequestActor actor, OfferRow row) {
        SkuSnapshot sku = skuOrNull(row.skuId());
        boolean sellable = sku != null && sku.sellable();
        boolean sellerApproved = "APPROVED".equals(row.sellerStatus());
        BigDecimal deviation = sku == null ? null : row.terms().deviationFrom(sku.unitPrice(), catalogCurrency);
        boolean moderator = actor.has(MarketplacePermissions.OFFERS_MODERATE);
        boolean manager = actor.has(MarketplacePermissions.OFFERS_MANAGE);
        boolean creator = actor.userId().equals(row.createdBy());
        List<String> allowed = new ArrayList<>();
        for (Action action : OfferWorkflow.actionsFrom(row.status())) {
            boolean permitted = OfferWorkflow.moderation(action) ? moderator && !(fourEyes && creator) : manager;
            if (permitted) {
                allowed.add(action.name());
            }
        }
        List<String> editable = !manager ? List.of() : switch (row.status()) {
            case DRAFT, REJECTED -> List.of("price", "available_quantity", "handling_days", "condition", "seller_sku", "currency",
                    "fulfillment");
            case ACTIVE, PAUSED -> List.of("price", "available_quantity", "handling_days");
            default -> List.of();
        };
        if (row.terms().fulfillment() == Fulfillment.PLATFORM) {
            // El disponible lo lleva la consignación.
            editable = editable.stream().filter(field -> !"available_quantity".equals(field)).toList();
        }
        OfferTerms t = row.terms();
        return new OfferDetailDto(row.id(), row.sellerId(), row.sellerName(), row.sellerStatus(),
                sku == null ? new CatalogSkuDto(row.skuId(), row.catalog().productId(), row.catalog().skuCode(),
                        row.catalog().productName(), null, catalogCurrency, false)
                        : new CatalogSkuDto(row.skuId(), sku.productId(), sku.code(), sku.name(), sku.unitPrice(), catalogCurrency,
                        sku.sellable()),
                t.currency(), t.price(), t.availableQuantity(), t.handlingDays(), t.condition().name(), t.fulfillment().name(),
                t.sellerSku(), row.status().name(), row.statusReason(), row.statusChangedAt(), row.publishedAt(),
                OfferWorkflow.visible(row.status(), sellerApproved, sellable), deviation, OfferTerms.deviationAlert(deviation),
                allowed, editable, row.lockVersion(), row.createdAt(), row.createdBy(), row.updatedAt(), row.updatedBy());
    }

    private static OfferSummaryDto summary(OfferRow r) {
        OfferTerms t = r.terms();
        return new OfferSummaryDto(r.id(), r.sellerId(), r.sellerName(), r.sellerStatus(), r.skuId(), r.catalog().productId(),
                r.catalog().skuCode(), r.catalog().productName(), t.currency(), t.price(), t.availableQuantity(),
                t.handlingDays(), t.condition().name(), r.status().name(), r.publishedAt(), r.updatedAt());
    }

    /** "price: 89900 → 79900, available_quantity: 5 → 8" para la auditoría. */
    static String describe(OfferTerms before, OfferTerms after, List<String> changed) {
        List<String> parts = new ArrayList<>();
        for (String field : changed) {
            String from;
            String to;
            switch (field) {
                case "price" -> { from = before.price().toPlainString(); to = after.price().toPlainString(); }
                case "available_quantity" -> { from = "" + before.availableQuantity(); to = "" + after.availableQuantity(); }
                case "handling_days" -> { from = "" + before.handlingDays(); to = "" + after.handlingDays(); }
                case "condition" -> { from = before.condition().name(); to = after.condition().name(); }
                case "currency" -> { from = before.currency(); to = after.currency(); }
                case "fulfillment" -> { from = before.fulfillment().name(); to = after.fulfillment().name(); }
                default -> { from = String.valueOf(before.sellerSku()); to = String.valueOf(after.sellerSku()); }
            }
            parts.add(field + ": " + from + " → " + to);
        }
        String text = String.join(", ", parts);
        return text.length() > 1000 ? text.substring(0, 1000) : text;
    }

    private static void checkLock(OfferRow row, Integer lockVersion) {
        MarketplaceRuleViolation.check(lockVersion != null, "Falta lock_version (concurrencia).");
        if (row.lockVersion() != lockVersion) {
            throw stale();
        }
    }

    private static ConflictException stale() {
        return new ConflictException("La oferta cambió mientras la editabas. Recarga e intenta de nuevo.");
    }
}
