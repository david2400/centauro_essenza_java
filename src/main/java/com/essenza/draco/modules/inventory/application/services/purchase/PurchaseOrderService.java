package com.essenza.draco.modules.inventory.application.services.purchase;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.LineDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.LineRequest;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.OptionDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.OrderDetailDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.OrderSummaryDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.ReceiveLine;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.ReceiveRequest;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.SaveOrderRequest;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.StatsDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.StatusRequest;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.SupplierOptionDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.SupplierProductDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.SuggestionGroupDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.SuggestionLineDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.SuggestionsDto;
import com.essenza.draco.modules.inventory.application.output.repository.PurchaseOrderStore;
import com.essenza.draco.modules.inventory.application.output.repository.PurchaseOrderStore.NewLine;
import com.essenza.draco.modules.inventory.application.output.repository.PurchaseOrderStore.OrderRow;
import com.essenza.draco.modules.inventory.application.output.repository.PurchaseOrderStore.SkuInfo;
import com.essenza.draco.modules.inventory.application.output.repository.PurchaseOrderStore.LowStockRow;
import com.essenza.draco.modules.inventory.application.output.repository.PurchaseOrderStore.SupplierCandidate;
import com.essenza.draco.modules.inventory.application.output.repository.PurchaseOrderStore.SupplierRef;
import com.essenza.draco.modules.inventory.application.output.repository.PurchaseStockEntry;
import com.essenza.draco.modules.inventory.domain.model.purchase.PurchaseOrderRules;
import com.essenza.draco.modules.inventory.domain.model.purchase.PurchaseOrderRules.Line;
import com.essenza.draco.modules.inventory.domain.model.purchase.PurchaseOrderRules.Pending;
import com.essenza.draco.modules.inventory.domain.model.purchase.PurchaseOrderRules.PurchaseRuleViolation;
import com.essenza.draco.modules.inventory.domain.model.purchase.PurchaseOrderRules.Status;
import com.essenza.draco.modules.inventory.domain.model.purchase.ReorderPolicy;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Órdenes de compra a proveedores: borrador con los productos que surte el proveedor y su costo,
 * envío, recepciones parciales o totales en la bodega destino (suman al inventario propio) y
 * cierre. Permisos configurables: COMPRAS.VER, COMPRAS.GESTIONAR y COMPRAS.RECIBIR.
 */
@Service
@Transactional
public class PurchaseOrderService {

    public static final String VIEW = "COMPRAS.VER";
    public static final String MANAGE = "COMPRAS.GESTIONAR";
    public static final String RECEIVE = "COMPRAS.RECIBIR";
    static final ZoneId ZONE = ZoneId.of("America/Bogota");

    private final PurchaseOrderStore store;
    private final PurchaseStockEntry stock;
    private final ActorProvider actors;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PurchaseOrderService(PurchaseOrderStore store, PurchaseStockEntry stock, ActorProvider actors) {
        this(store, stock, actors, Clock.system(ZONE));
    }

    /** Para pruebas: reloj fijo. */
    public PurchaseOrderService(PurchaseOrderStore store, PurchaseStockEntry stock, ActorProvider actors, Clock clock) {
        this.store = store;
        this.stock = stock;
        this.actors = actors;
        this.clock = clock;
    }

    public OrderDetailDto create(SaveOrderRequest request) {
        RequestActor actor = actors.current().require(MANAGE);
        Draft draft = draft(request);
        Instant now = clock.instant();
        LocalDate expected = request.expectedDate() != null ? request.expectedDate()
                : draft.supplier().leadTimeDays() == null ? null : LocalDate.now(clock).plusDays(draft.supplier().leadTimeDays());
        long id = store.insertOrder(actor.companyId(), draft.supplier(), request.warehouseId(), expected, clip(request.notes(), 1000),
                PurchaseOrderRules.total(draft.lines()), actor.userId(), actor.displayName(), now);
        store.setOrderNumber(id, PurchaseOrderRules.orderNumber(id));
        store.replaceLines(id, draft.newLines());
        store.addEvent(id, "CREATE", null, Status.DRAFT.name(), null, actor.userId(), actor.displayName(), now);
        return detail(actor, id);
    }

    public OrderDetailDto update(long id, SaveOrderRequest request) {
        RequestActor actor = actors.current().require(MANAGE);
        OrderRow row = load(actor, id, true);
        PurchaseOrderRules.checkEditable(status(row));
        Draft draft = draft(request);
        Instant now = clock.instant();
        if (!store.updateOrder(id, lock(row, request.lockVersion()), draft.supplier().id(), request.warehouseId(), request.expectedDate(),
                clip(request.notes(), 1000), PurchaseOrderRules.total(draft.lines()), now)) {
            throw stale();
        }
        store.replaceLines(id, draft.newLines());
        store.addEvent(id, "EDIT", Status.DRAFT.name(), Status.DRAFT.name(), null, actor.userId(), actor.displayName(), now);
        return detail(actor, id);
    }

    public OrderDetailDto send(long id, StatusRequest request) {
        RequestActor actor = actors.current().require(MANAGE);
        OrderRow row = load(actor, id, true);
        SupplierRef supplier = store.supplier(row.summary().supplierId())
                .orElseThrow(() -> new NotFoundException("No se encontró el proveedor de la orden."));
        PurchaseOrderRules.checkSend(status(row), "ACTIVE".equals(supplier.status()), row.summary().total(), supplier.minOrderAmount());
        return transition(actor, row, request, Status.SENT, "SEND", null, true, false);
    }

    public OrderDetailDto cancel(long id, StatusRequest request) {
        RequestActor actor = actors.current().require(MANAGE);
        OrderRow row = load(actor, id, true);
        String reason = request == null ? null : request.reason();
        PurchaseOrderRules.checkCancel(status(row), store.hasReceipts(id), reason);
        return transition(actor, row, request, Status.CANCELLED, "CANCEL", clip(reason, 500), false, true);
    }

    public OrderDetailDto close(long id, StatusRequest request) {
        RequestActor actor = actors.current().require(MANAGE);
        OrderRow row = load(actor, id, true);
        String reason = request == null ? null : request.reason();
        PurchaseOrderRules.checkClose(status(row), reason);
        return transition(actor, row, request, Status.CLOSED, "CLOSE", clip(reason, 500), false, true);
    }

    /** Recepción en la bodega destino: suma al inventario y deja la orden recibida en parte o completa. */
    public OrderDetailDto receive(long id, ReceiveRequest request) {
        RequestActor actor = actors.current().require(RECEIVE);
        violation(request != null && request.lines() != null, "Indica las cantidades recibidas.");
        OrderRow row = load(actor, id, true);
        List<LineDto> lines = store.lines(id);
        Map<Long, Integer> receiving = new HashMap<>();
        for (ReceiveLine line : request.lines()) {
            violation(line != null && line.lineId() != null, "Cada cantidad debe indicar su línea.");
            violation(receiving.put(line.lineId(), line.quantity() == null ? 0 : line.quantity()) == null,
                    "La línea " + line.lineId() + " está repetida.");
        }
        Status next = PurchaseOrderRules.checkReceive(status(row), lines.stream()
                .map(l -> new Pending(l.id(), l.skuCode() != null ? l.skuCode() : "SKU " + l.skuId(), l.quantity(), l.received()))
                .toList(), receiving);
        int lockVersion = lock(row, request.lockVersion());
        Instant now = clock.instant();
        long warehouseId = row.summary().warehouseId();
        int units = receiving.values().stream().mapToInt(Integer::intValue).sum();
        long receiptId = store.insertReceipt(actor.companyId(), id, warehouseId, clip(request.reference(), 80), clip(request.notes(), 500),
                units, actor.userId(), actor.displayName(), now);
        String receiptNumber = PurchaseOrderRules.receiptNumber(receiptId);
        store.setReceiptNumber(receiptId, receiptNumber);
        List<PurchaseStockEntry.Entry> entries = new ArrayList<>();
        for (LineDto line : lines) {
            int qty = receiving.getOrDefault(line.id(), 0);
            if (qty > 0) {
                store.insertReceiptLine(receiptId, line.id(), qty);
                store.addReceived(line.id(), qty);
                entries.add(new PurchaseStockEntry.Entry(line.productId(), line.skuId(), qty, line.unitCost()));
            }
        }
        stock.receive(receiptId, receiptNumber, warehouseId, entries);
        if (!store.setStatus(id, lockVersion, next.name(), null, false, next == Status.RECEIVED, now)) {
            throw stale();
        }
        store.addEvent(id, "RECEIVE", status(row).name(), next.name(), receiptNumber + " · " + units + " u.", actor.userId(),
                actor.displayName(), now);
        return detail(actor, id);
    }

    @Transactional(readOnly = true)
    public OrderDetailDto get(long id) {
        return detail(view(), id);
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryDto> search(String status, Long supplierId, String q, Pageable pageable) {
        RequestActor actor = view();
        String s = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (s != null && !"OPEN".equals(s) && !"OVERDUE".equals(s)) {
            try {
                s = Status.valueOf(s).name();
            } catch (IllegalArgumentException ex) {
                throw new PurchaseRuleViolation("Estado no válido: " + status + ".");
            }
        }
        return store.search(actor.companyId(), s, supplierId, q, LocalDate.now(clock), pageable);
    }

    @Transactional(readOnly = true)
    public StatsDto stats() {
        return store.stats(view().companyId(), LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public List<SupplierOptionDto> supplierOptions() {
        view();
        return store.supplierOptions();
    }

    @Transactional(readOnly = true)
    public List<SupplierProductDto> supplierProducts(long supplierId) {
        view();
        return List.copyOf(store.supplierProducts(supplierId).values());
    }

    static final int SUGGESTION_LIMIT = 500;

    private record GroupHead(LowStockRow row, SupplierCandidate supplier) {
    }

    /**
     * Sugerencia de compra desde el stock bajo: por bodega y proveedor sugerido (preferido o el de
     * menor costo), con la cantidad para reponer hasta el doble del mínimo menos lo que ya viene.
     */
    @Transactional(readOnly = true)
    public SuggestionsDto suggestions(Long warehouseId) {
        RequestActor actor = view();
        List<LowStockRow> rows = store.lowStock(actor.companyId(), warehouseId, SUGGESTION_LIMIT + 1);
        boolean truncated = rows.size() > SUGGESTION_LIMIT;
        if (truncated) {
            rows = rows.subList(0, SUGGESTION_LIMIT);
        }
        Map<Long, List<SupplierCandidate>> candidates = store.supplierCandidates(rows.stream().map(LowStockRow::skuId).distinct().toList());
        Map<String, List<SuggestionLineDto>> grouped = new java.util.LinkedHashMap<>();
        Map<String, GroupHead> heads = new HashMap<>();
        int covered = 0;
        for (LowStockRow row : rows) {
            int available = row.onHand() - row.reserved();
            SupplierCandidate pick = ReorderPolicy.pickSupplier(candidates.getOrDefault(row.skuId(), List.of())).orElse(null);
            int qty = ReorderPolicy.suggestedQuantity(available, row.minThreshold(), row.onOrder(), pick == null ? null : pick.minOrderQty());
            if (qty <= 0) {
                covered++;
                continue;
            }
            BigDecimal cost = pick == null ? null : pick.unitCost();
            String key = row.warehouseId() + ":" + (pick == null ? "-" : pick.supplierId());
            heads.putIfAbsent(key, new GroupHead(row, pick));
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(new SuggestionLineDto(row.skuId(), row.productId(), row.skuCode(),
                    row.productName(), pick == null ? null : pick.supplierCode(), row.onHand(), available, row.minThreshold(), row.onOrder(),
                    qty, cost, row.averageCost(), cost == null ? null : cost.multiply(BigDecimal.valueOf(qty))));
        }
        List<SuggestionGroupDto> groups = new ArrayList<>();
        List<SuggestionGroupDto> uncovered = new ArrayList<>();
        grouped.forEach((key, lines) -> {
            LowStockRow row = heads.get(key).row();
            SupplierCandidate pick = heads.get(key).supplier();
            if (pick == null) {
                uncovered.add(new SuggestionGroupDto(row.warehouseId(), row.warehouseName(), null, null, null, null, null, false, lines));
                return;
            }
            BigDecimal total = lines.stream().map(SuggestionLineDto::lineTotal).filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean below = pick.minOrderAmount() != null && total.compareTo(pick.minOrderAmount()) < 0;
            groups.add(new SuggestionGroupDto(row.warehouseId(), row.warehouseName(), pick.supplierId(), pick.supplierName(),
                    pick.leadTimeDays(), pick.minOrderAmount(), total, below, lines));
        });
        return new SuggestionsDto(rows.size(), covered, truncated, groups, uncovered);
    }

    @Transactional(readOnly = true)
    public List<OptionDto> warehouseOptions() {
        view();
        return store.warehouseOptions();
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────

    private record Draft(SupplierRef supplier, List<Line> lines, List<NewLine> newLines) {
    }

    /** Proveedor activo, bodega activa, SKU existentes y costo (el escrito o el pactado con el proveedor). */
    private Draft draft(SaveOrderRequest request) {
        violation(request != null && request.supplierId() != null, "Elige el proveedor.");
        violation(request.warehouseId() != null, "Elige la bodega destino.");
        SupplierRef supplier = store.supplier(request.supplierId())
                .orElseThrow(() -> new NotFoundException("No se encontró el proveedor " + request.supplierId() + "."));
        violation("ACTIVE".equals(supplier.status()), "El proveedor " + supplier.name() + " no está activo.");
        store.activeWarehouse(request.warehouseId())
                .orElseThrow(() -> new NotFoundException("La bodega " + request.warehouseId() + " no existe o está inactiva."));
        violation(request.lines() != null && !request.lines().isEmpty(), "Agrega al menos un producto a la orden.");
        Map<Long, SupplierProductDto> catalog = store.supplierProducts(supplier.id());
        List<Line> requested = new ArrayList<>();
        for (LineRequest line : request.lines()) {
            violation(line != null && line.skuId() != null, "Cada línea necesita su SKU.");
            BigDecimal cost = line.unitCost() != null ? line.unitCost()
                    : catalog.containsKey(line.skuId()) ? catalog.get(line.skuId()).unitCost() : null;
            violation(cost != null, "Indica el costo del SKU " + line.skuId() + " (el proveedor no tiene uno pactado).");
            requested.add(new Line(line.skuId(), line.quantity() == null ? 0 : line.quantity(), cost));
        }
        List<Line> lines = PurchaseOrderRules.checkLines(requested);
        Map<Long, SkuInfo> skus = store.skus(lines.stream().map(Line::skuId).toList());
        List<NewLine> newLines = new ArrayList<>();
        for (Line line : lines) {
            SkuInfo sku = skus.get(line.skuId());
            if (sku == null) {
                throw new NotFoundException("No se encontró el SKU " + line.skuId() + ".");
            }
            SupplierProductDto item = catalog.get(line.skuId());
            newLines.add(new NewLine(sku.skuId(), sku.productId(), sku.code(), sku.name(), item == null ? null : item.supplierCode(),
                    line.quantity(), line.unitCost()));
        }
        return new Draft(supplier, lines, newLines);
    }

    private OrderDetailDto transition(RequestActor actor, OrderRow row, StatusRequest request, Status next, String action, String reason,
                                      boolean sent, boolean closed) {
        Instant now = clock.instant();
        long id = row.summary().id();
        if (!store.setStatus(id, lock(row, request == null ? null : request.lockVersion()), next.name(), reason, sent, closed, now)) {
            throw stale();
        }
        store.addEvent(id, action, status(row).name(), next.name(), reason, actor.userId(), actor.displayName(), now);
        return detail(actor, id);
    }

    private OrderDetailDto detail(RequestActor actor, long id) {
        OrderRow r = load(actor, id, false);
        Status status = status(r);
        List<String> actions = new ArrayList<>();
        if (actor.has(MANAGE)) {
            if (status == Status.DRAFT) {
                actions.add("EDIT");
                actions.add("SEND");
            }
            if (status == Status.DRAFT || status == Status.SENT) {
                actions.add("CANCEL");
            }
            if (status == Status.PARTIALLY_RECEIVED) {
                actions.add("CLOSE");
            }
        }
        if (actor.has(RECEIVE) && status.receivable()) {
            actions.add("RECEIVE");
        }
        OrderSummaryDto o = r.summary();
        // Vencida según el reloj del servicio (zona de la tienda).
        OrderSummaryDto s = new OrderSummaryDto(o.id(), o.number(), o.supplierId(), o.supplierName(), o.warehouseId(), o.warehouseName(),
                o.status(), o.expectedDate(), status.receivable() && o.expectedDate() != null && o.expectedDate().isBefore(LocalDate.now(clock)),
                o.total(), o.orderedUnits(), o.receivedUnits(), o.createdAt());
        return new OrderDetailDto(s, r.currency(), r.notes(), r.statusReason(), r.sentAt(), r.closedAt(), r.lockVersion(),
                r.createdByName(), store.lines(id), store.receipts(id), store.events(id), actions);
    }

    private RequestActor view() {
        return actors.current().requireAny(VIEW, MANAGE, RECEIVE);
    }

    private OrderRow load(RequestActor actor, long id, boolean forUpdate) {
        return store.find(actor.companyId(), id, forUpdate)
                .orElseThrow(() -> new NotFoundException("No se encontró la orden de compra " + id + "."));
    }

    private static Status status(OrderRow row) {
        return Status.valueOf(row.summary().status());
    }

    private static int lock(OrderRow row, Integer requested) {
        violation(requested != null, "Falta lock_version.");
        if (requested != row.lockVersion()) {
            throw stale();
        }
        return requested;
    }

    private static ConflictException stale() {
        return new ConflictException("Otra persona modificó la orden de compra. Recarga para ver los cambios.");
    }

    private static void violation(boolean ok, String message) {
        if (!ok) {
            throw new PurchaseRuleViolation(message);
        }
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        return text.length() > max ? text.substring(0, max) : text;
    }
}
