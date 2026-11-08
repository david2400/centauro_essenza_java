package com.essenza.draco.modules.marketplace.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.marketplace.application.MarketplacePermissions;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.AdjustmentRequest;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.DocumentDto;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.LineRequest;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.MovementDto;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.MovementFilter;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.OptionDto;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.SaveDocumentRequest;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.StockFilter;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.StockRowDto;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.StockStatsDto;
import com.essenza.draco.modules.marketplace.application.port.ConsignmentStore;
import com.essenza.draco.modules.marketplace.application.port.ConsignmentStore.NewMovement;
import com.essenza.draco.modules.marketplace.application.port.ConsignmentStore.OfferRef;
import com.essenza.draco.modules.marketplace.application.port.ConsignmentStore.SellerRef;
import com.essenza.draco.modules.marketplace.application.port.SellerLedgerStore;
import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.domain.consignment.ConsignmentRules;
import com.essenza.draco.modules.marketplace.domain.consignment.ConsignmentRules.Bin;
import com.essenza.draco.modules.marketplace.domain.consignment.ConsignmentRules.DocumentKind;
import com.essenza.draco.modules.marketplace.domain.consignment.ConsignmentRules.Line;
import com.essenza.draco.modules.marketplace.domain.consignment.ConsignmentRules.MovementKind;
import com.essenza.draco.modules.marketplace.domain.consignment.ConsignmentRules.Pick;
import com.essenza.draco.shared.common.marketplace.ConsignmentReturns;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Consignación: la mercancía de un vendedor externo guardada en una bodega propia.
 *
 * <ul>
 *   <li>Saldo físico por vendedor, SKU y bodega, separado del inventario propio.</li>
 *   <li>La oferta abierta del vendedor con despacho de la plataforma vende ese saldo: su disponible
 *   es el saldo total menos lo reservado en ventas pendientes. Recibir, devolver al vendedor o
 *   ajustar lo actualiza en la misma transacción.</li>
 *   <li>Pagar una venta descuenta el saldo (primero de la bodega que más tiene); cancelarla ya
 *   pagada lo repone en las mismas bodegas; una devolución de cliente vuelve a consignación.</li>
 * </ul>
 * Empresa y permisos salen del token (CONSIGNACION.VER / CONSIGNACION.GESTIONAR).
 */
@Service
@Transactional
public class ConsignmentService implements ConsignmentReturns {

    static final String SYSTEM = "sistema";

    private final ConsignmentStore store;
    private final ActorProvider actors;
    private final Clock clock;
    private final SellerLedgerStore ledger;

    /** Para pruebas sin estado de cuenta. */
    public ConsignmentService(ConsignmentStore store, ActorProvider actors, Clock marketplaceClock) {
        this(store, actors, marketplaceClock, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ConsignmentService(ConsignmentStore store, ActorProvider actors, Clock marketplaceClock, SellerLedgerStore ledger) {
        this.store = store;
        this.actors = actors;
        this.clock = marketplaceClock;
        this.ledger = ledger;
    }

    // ── Documentos ────────────────────────────────────────────────────────

    /** Recepción de mercancía del vendedor o devolución al vendedor. */
    public DocumentDto saveDocument(SaveDocumentRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.CONSIGNMENT_MANAGE);
        MarketplaceRuleViolation.check(request != null, "Faltan los datos del documento.");
        DocumentKind kind = DocumentKind.parse(request.kind());
        MarketplaceRuleViolation.check(kind != DocumentKind.ADJUSTMENT, "Los ajustes se registran con el conteo de un SKU.");
        SellerRef seller = seller(actor, request.sellerId());
        MarketplaceRuleViolation.check(kind != DocumentKind.RECEIPT || !"CLOSED".equals(seller.status()),
                "El vendedor " + seller.name() + " está cerrado: no se le recibe mercancía.");
        String warehouse = warehouse(request.warehouseId());
        List<Line> lines = ConsignmentRules.checkLines(lines(request.lines()));
        Map<Long, String> codes = store.skuCodes(lines.stream().map(Line::skuId).toList());
        for (Line line : lines) {
            if (!codes.containsKey(line.skuId())) {
                throw new NotFoundException("No se encontró el SKU " + line.skuId() + ".");
            }
        }
        Instant now = clock.instant();
        // Orden fijo de bloqueo (por SKU) para no cruzarse con otra transacción.
        List<Line> ordered = new ArrayList<>(lines);
        ordered.sort(Comparator.comparingLong(Line::skuId));
        if (kind == DocumentKind.RETURN_TO_SELLER) {
            // Se valida todo antes de numerar el documento: o sale completo o no sale.
            for (Line line : ordered) {
                bin(actor, seller.id(), line, request.warehouseId(), warehouse, codes.get(line.skuId()));
            }
        }
        int units = lines.stream().mapToInt(Line::quantity).sum();
        long documentId = store.insertDocument(actor.companyId(), kind.name(), seller.id(), request.warehouseId(),
                clean(request.reference(), 80), clean(request.notes(), 500), units, actor.userId(), actor.displayName(), now);
        store.setDocumentNumber(documentId, ConsignmentRules.number(documentId));
        for (Line line : ordered) {
            if (kind == DocumentKind.RECEIPT) {
                receive(actor, seller.id(), line, request.warehouseId(), documentId, now);
            } else {
                giveBack(actor, seller.id(), line, request.warehouseId(), warehouse, codes.get(line.skuId()), documentId, now);
            }
        }
        return document(actor, documentId);
    }

    /** Conteo físico: deja el saldo del SKU en la bodega en lo contado (documento ADJUSTMENT). */
    public DocumentDto adjust(AdjustmentRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.CONSIGNMENT_MANAGE);
        MarketplaceRuleViolation.check(request != null && request.skuId() != null && request.countedQuantity() != null,
                "Indica el SKU y la cantidad contada.");
        SellerRef seller = seller(actor, request.sellerId());
        warehouse(request.warehouseId());
        if (store.skuCodes(List.of(request.skuId())).isEmpty()) {
            throw new NotFoundException("No se encontró el SKU " + request.skuId() + ".");
        }
        Instant now = clock.instant();
        store.lockBins(actor.companyId(), seller.id(), request.skuId()); // mismo orden de bloqueo que las ventas
        Bin bin = store.lockOrCreate(actor.companyId(), seller.id(), request.skuId(), request.warehouseId(), now);
        int total = store.lockBins(actor.companyId(), seller.id(), request.skuId()).stream().mapToInt(Bin::onHand).sum();
        int reserved = store.activeReserved(actor.companyId(), seller.id(), request.skuId());
        // Lo reservado que no cabe en las demás bodegas tiene que quedarse en esta.
        int mustStay = Math.max(0, reserved - (total - bin.onHand()));
        int delta = ConsignmentRules.adjustment(bin.onHand(), request.countedQuantity(), mustStay, request.reason());
        String reason = clean(request.reason(), 500);
        long documentId = store.insertDocument(actor.companyId(), DocumentKind.ADJUSTMENT.name(), seller.id(),
                request.warehouseId(), null, reason, Math.abs(delta), actor.userId(), actor.displayName(), now);
        store.setDocumentNumber(documentId, ConsignmentRules.number(documentId));
        int balance = store.addToStock(bin.stockId(), delta, now);
        store.insertMovement(new NewMovement(actor.companyId(), bin.stockId(), seller.id(), request.skuId(),
                request.warehouseId(), MovementKind.ADJUSTMENT.name(), delta, balance, documentId, null, null, null, reason,
                actor.userId(), now));
        syncOffer(actor.companyId(), seller.id(), request.skuId(), delta, now);
        return document(actor, documentId);
    }

    private void receive(RequestActor actor, long sellerId, Line line, long warehouseId, long documentId, Instant now) {
        Bin bin = store.lockOrCreate(actor.companyId(), sellerId, line.skuId(), warehouseId, now);
        int balance = store.addToStock(bin.stockId(), line.quantity(), now);
        store.insertMovement(new NewMovement(actor.companyId(), bin.stockId(), sellerId, line.skuId(), warehouseId,
                MovementKind.RECEIPT.name(), line.quantity(), balance, documentId, null, null, null, null, actor.userId(), now));
        syncOffer(actor.companyId(), sellerId, line.skuId(), line.quantity(), now);
    }

    private void giveBack(RequestActor actor, long sellerId, Line line, long warehouseId, String warehouse, String skuCode,
                          long documentId, Instant now) {
        Bin bin = bin(actor, sellerId, line, warehouseId, warehouse, skuCode);
        int balance = store.addToStock(bin.stockId(), -line.quantity(), now);
        store.insertMovement(new NewMovement(actor.companyId(), bin.stockId(), sellerId, line.skuId(), warehouseId,
                MovementKind.RETURN_TO_SELLER.name(), -line.quantity(), balance, documentId, null, null, null, null,
                actor.userId(), now));
        syncOffer(actor.companyId(), sellerId, line.skuId(), -line.quantity(), now);
    }

    /** Saldo de la bodega bloqueado, si alcanza para devolver al vendedor sin tocar lo reservado. */
    private Bin bin(RequestActor actor, long sellerId, Line line, long warehouseId, String warehouse, String skuCode) {
        List<Bin> bins = store.lockBins(actor.companyId(), sellerId, line.skuId());
        Bin bin = bins.stream().filter(b -> b.warehouseId() == warehouseId).findFirst().orElse(null);
        int here = bin == null ? 0 : bin.onHand();
        MarketplaceRuleViolation.check(here >= line.quantity(), "En " + warehouse + " hay " + here + " unidades de " + skuCode
                + " del vendedor; no se pueden devolver " + line.quantity() + ".");
        int free = bins.stream().mapToInt(Bin::onHand).sum() - store.activeReserved(actor.companyId(), sellerId, line.skuId());
        MarketplaceRuleViolation.check(free >= line.quantity(), "De " + skuCode + " solo hay " + Math.max(0, free)
                + " unidades libres: las demás están reservadas en ventas pendientes de pago.");
        return bin;
    }

    /** El disponible de la oferta abierta con despacho de la plataforma sigue al saldo. */
    private void syncOffer(long companyId, long sellerId, long skuId, int delta, Instant now) {
        store.lockOpenPlatformOffer(companyId, sellerId, skuId).ifPresent(offer -> {
            if (!store.adjustOfferAvailable(offer.id(), delta, now)) {
                throw new ConflictException("El disponible de la oferta " + offer.id() + " no cuadra con la consignación: "
                        + "revisa sus ventas pendientes antes de ajustar.");
            }
        });
    }

    // ── Ventas (los llama OfferStockService dentro de la transacción del pedido) ──

    /** Disponible para vender del vendedor y SKU: saldo de todas las bodegas menos lo reservado. */
    public int availableFor(long companyId, long sellerId, long skuId) {
        int onHand = store.lockBins(companyId, sellerId, skuId).stream().mapToInt(Bin::onHand).sum();
        return Math.max(0, onHand - store.activeReserved(companyId, sellerId, skuId));
    }

    /** Pagar: descuenta el saldo físico si la oferta despacha desde la plataforma. */
    public void sold(long offerId, long orderId, long lineId, int quantity) {
        OfferRef offer = store.offer(offerId).orElse(null);
        if (offer == null || !"PLATFORM".equals(offer.fulfillment())) {
            return;
        }
        Instant now = clock.instant();
        List<Bin> bins = store.lockBins(offer.companyId(), offer.sellerId(), offer.skuId());
        for (Pick pick : ConsignmentRules.allocate(bins, quantity)) {
            int balance = store.addToStock(pick.stockId(), -pick.quantity(), now);
            store.insertMovement(new NewMovement(offer.companyId(), pick.stockId(), offer.sellerId(), offer.skuId(),
                    pick.warehouseId(), MovementKind.SALE.name(), -pick.quantity(), balance, null, orderId, lineId, null, null,
                    SYSTEM, now));
        }
        // La tienda cobró: le debe al vendedor lo que pagó el comprador por la línea (la comisión va aparte).
        store.lineSale(lineId).filter(ConsignmentStore.LineSale::storeCollects).ifPresent(sale ->
                postProceeds(offer, sale, saleKey(lineId), "CONSIGNMENT_SALE", sale.total().negate(), now));
    }

    /** Cancelar una venta pagada: repone lo vendido en las mismas bodegas. */
    public void unsold(long offerId, long orderId, long lineId) {
        OfferRef offer = store.offer(offerId).orElse(null);
        if (offer == null || !"PLATFORM".equals(offer.fulfillment())) {
            return;
        }
        Instant now = clock.instant();
        List<Bin> bins = store.lockBins(offer.companyId(), offer.sellerId(), offer.skuId());
        for (Map.Entry<Long, Integer> sold : store.soldByStock(lineId).entrySet()) {
            if (sold.getValue() <= 0) {
                continue;
            }
            long warehouseId = bins.stream().filter(b -> b.stockId() == sold.getKey()).map(Bin::warehouseId).findFirst()
                    .orElseThrow(() -> new ConflictException("No se encontró el saldo " + sold.getKey() + " de consignación."));
            int balance = store.addToStock(sold.getKey(), sold.getValue(), now);
            store.insertMovement(new NewMovement(offer.companyId(), sold.getKey(), offer.sellerId(), offer.skuId(), warehouseId,
                    MovementKind.SALE_CANCEL.name(), sold.getValue(), balance, null, orderId, lineId, null, null, SYSTEM, now));
        }
        reverseProceeds(offer, lineId, "CSG-CANCEL:" + lineId, null, now);
    }

    private static String saleKey(long lineId) {
        return "CSG-SALE:" + lineId;
    }

    /** Reversa lo abonado por la línea (todo o {@code quantity} unidades) si se abonó. */
    private void reverseProceeds(OfferRef offer, long lineId, String key, Integer quantity, Instant now) {
        if (ledger == null || !store.ledgerKeyExists(offer.companyId(), saleKey(lineId))) {
            return;
        }
        store.lineSale(lineId).ifPresent(sale -> {
            java.math.BigDecimal amount = quantity == null || sale.quantity() <= 0 ? sale.total()
                    : sale.total().multiply(java.math.BigDecimal.valueOf(Math.min(quantity, sale.quantity())))
                            .divide(java.math.BigDecimal.valueOf(sale.quantity()), 2, java.math.RoundingMode.HALF_UP);
            postProceeds(offer, sale, key, "CONSIGNMENT_REVERSAL", amount, now);
        });
    }

    private void postProceeds(OfferRef offer, ConsignmentStore.LineSale sale, String key, String type, java.math.BigDecimal amount,
                              Instant now) {
        if (ledger == null || amount.signum() == 0) {
            return;
        }
        java.time.LocalDate today = java.time.LocalDate.ofInstant(now, clock.getZone());
        ledger.post(offer.companyId(), new SellerLedgerStore.NewEntry(offer.sellerId(), type, amount, "COP", today, null,
                java.math.BigDecimal.ZERO, sale.orderNumber() != null ? sale.orderNumber() : "#" + sale.orderId(), null, null, key,
                null), SYSTEM, null, now);
    }

    @Override
    public Set<Long> receiveReturn(Long orderId, Long returnCaseId, Long warehouseId, List<ReturnedLine> lines) {
        Set<Long> taken = new HashSet<>();
        if (lines == null || lines.isEmpty()) {
            return taken;
        }
        Instant now = clock.instant();
        for (ReturnedLine line : lines) {
            if (line.orderLineId() == null || line.quantity() <= 0) {
                continue;
            }
            OfferRef offer = store.lineOffer(line.orderLineId()).flatMap(store::offer).orElse(null);
            if (offer == null || !"PLATFORM".equals(offer.fulfillment())) {
                continue;
            }
            if (warehouseId == null) {
                throw new ConflictException("Indica la bodega de reingreso: la línea " + line.orderLineId()
                        + " se vendió desde consignación y vuelve al stock del vendedor.");
            }
            Bin bin = store.lockOrCreate(offer.companyId(), offer.sellerId(), offer.skuId(), warehouseId, now);
            int balance = store.addToStock(bin.stockId(), line.quantity(), now);
            store.insertMovement(new NewMovement(offer.companyId(), bin.stockId(), offer.sellerId(), offer.skuId(), warehouseId,
                    MovementKind.CUSTOMER_RETURN.name(), line.quantity(), balance, null, orderId, line.orderLineId(), returnCaseId,
                    null, SYSTEM, now));
            syncOffer(offer.companyId(), offer.sellerId(), offer.skuId(), line.quantity(), now);
            reverseProceeds(offer, line.orderLineId(), "CSG-RETURN:" + returnCaseId + ":" + line.orderLineId(), line.quantity(), now);
            taken.add(line.orderLineId());
        }
        return taken;
    }

    /**
     * Unidades-día del vendedor en el período (cierre de cada día en la zona del marketplace).
     * Lo usa la generación de cargos del ciclo, con su propio permiso.
     */
    @Transactional(readOnly = true)
    public long unitDays(long companyId, long sellerId, java.time.LocalDate from, java.time.LocalDate to) {
        java.time.ZoneId zone = clock.getZone();
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        Map<java.time.LocalDate, Integer> byDay = new java.util.HashMap<>();
        for (ConsignmentStore.Delta delta : store.deltas(companyId, sellerId, start, end)) {
            byDay.merge(java.time.LocalDate.ofInstant(delta.at(), zone), delta.quantity(), Integer::sum);
        }
        return com.essenza.draco.modules.marketplace.domain.consignment.StorageCharge.unitDays(
                store.unitsBefore(companyId, sellerId, start), byDay, from, to);
    }

    // ── Consultas ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<StockRowDto> stock(StockFilter filter, Pageable pageable) {
        RequestActor actor = view();
        return store.searchStock(actor.companyId(), filter, clock.instant(), pageable);
    }

    @Transactional(readOnly = true)
    public StockStatsDto stats() {
        return store.stats(view().companyId());
    }

    @Transactional(readOnly = true)
    public Page<MovementDto> movements(MovementFilter filter, Pageable pageable) {
        if (filter.kind() != null && !filter.kind().isBlank()) {
            try {
                MovementKind.valueOf(filter.kind().trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new MarketplaceRuleViolation("Tipo de movimiento no válido: " + filter.kind() + ".");
            }
        }
        return store.movements(view().companyId(), filter, pageable);
    }

    @Transactional(readOnly = true)
    public Page<DocumentDto> documents(Long sellerId, String kind, Pageable pageable) {
        String k = kind == null || kind.isBlank() ? null : DocumentKind.parse(kind).name();
        return store.documents(view().companyId(), sellerId, k, pageable);
    }

    @Transactional(readOnly = true)
    public DocumentDto document(long id) {
        return document(view(), id);
    }

    @Transactional(readOnly = true)
    public List<OptionDto> sellerOptions() {
        return store.sellerOptions(view().companyId());
    }

    @Transactional(readOnly = true)
    public List<OptionDto> warehouseOptions() {
        view();
        return store.warehouseOptions();
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────

    private RequestActor view() {
        return actors.current().requireAny(MarketplacePermissions.CONSIGNMENT_VIEW, MarketplacePermissions.CONSIGNMENT_MANAGE);
    }

    private DocumentDto document(RequestActor actor, long id) {
        DocumentDto head = store.document(actor.companyId(), id)
                .orElseThrow(() -> new NotFoundException("No se encontró el documento de consignación " + id + "."));
        return new DocumentDto(head.id(), head.number(), head.kind(), head.sellerId(), head.sellerName(), head.warehouseId(),
                head.warehouseName(), head.reference(), head.notes(), head.units(), head.createdBy(), head.createdByName(),
                head.createdAt(), store.documentLines(actor.companyId(), id));
    }

    private SellerRef seller(RequestActor actor, Long sellerId) {
        MarketplaceRuleViolation.check(sellerId != null, "Elige el vendedor.");
        SellerRef seller = store.seller(actor.companyId(), sellerId)
                .orElseThrow(() -> new NotFoundException("Vendedor " + sellerId + " no encontrado."));
        MarketplaceRuleViolation.check("EXTERNAL".equals(seller.kind()),
                "La consignación es para vendedores externos: la mercancía propia va en el inventario.");
        return seller;
    }

    private String warehouse(Long warehouseId) {
        MarketplaceRuleViolation.check(warehouseId != null, "Elige la bodega.");
        return store.activeWarehouse(warehouseId)
                .orElseThrow(() -> new NotFoundException("La bodega " + warehouseId + " no existe o está inactiva."));
    }

    private static List<Line> lines(List<LineRequest> requested) {
        if (requested == null) {
            return List.of();
        }
        List<Line> lines = new ArrayList<>();
        for (LineRequest line : requested) {
            MarketplaceRuleViolation.check(line != null && line.skuId() != null, "Cada línea necesita su SKU.");
            lines.add(new Line(line.skuId(), line.quantity() == null ? 0 : line.quantity()));
        }
        return lines;
    }

    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        return text.length() > max ? text.substring(0, max) : text;
    }
}
