package com.essenza.draco.modules.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.LineDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.LineRequest;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.OrderDetailDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.ReceiveLine;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.ReceiveRequest;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.SaveOrderRequest;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.StatusRequest;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.SuggestionGroupDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.SuggestionsDto;
import com.essenza.draco.modules.inventory.application.output.repository.InventoryMovementRepository;
import com.essenza.draco.modules.inventory.application.output.repository.StockItemRepository;
import com.essenza.draco.modules.inventory.application.services.purchase.PurchaseOrderService;
import com.essenza.draco.modules.inventory.application.services.purchase.PurchaseStockEntryService;
import com.essenza.draco.modules.inventory.application.dto.InventoryMovementDto;
import com.essenza.draco.modules.inventory.application.dto.StockItemDto;
import com.essenza.draco.modules.inventory.domain.model.StockItem;
import com.essenza.draco.modules.inventory.domain.model.purchase.PurchaseOrderRules.PurchaseRuleViolation;
import com.essenza.draco.modules.inventory.infrastructure.outbound.purchase.PurchaseOrderJdbcStore;
import com.essenza.draco.modules.inventory.infrastructure.outbound.purchase.SkuCostJdbcStore;
import com.essenza.draco.shared.common.web.PageableFactory;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.ForbiddenException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Órdenes de compra contra MySQL/MariaDB con los adaptadores JDBC reales y el servicio real de
 * entrada al inventario (costo promedio incluido). Las existencias se leen y guardan con JDBC sobre
 * stock_items y el kardex se captura en memoria (en la app son JPA). Solo *_test.
 */
@EnabledIfEnvironmentVariable(named = "MARKETPLACE_IT_DB_URL", matches = "jdbc:.*_test(\\?.*)?")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PurchaseOrderFlowIT {

    static final long COMPANY = 2;
    static final Set<String> ALL = Set.of("COMPRAS.VER", "COMPRAS.GESTIONAR", "COMPRAS.RECIBIR");
    static RequestActor actor = new RequestActor(COMPANY, "u-1", "Compras", ALL);
    static NamedParameterJdbcTemplate jdbc;
    static PurchaseOrderService orders;
    static long supplier, blocked, warehouse, orderId;
    static final List<InventoryMovementDto> KARDEX = new ArrayList<>();

    @BeforeAll
    static void setUp() {
        String url = System.getenv("MARKETPLACE_IT_DB_URL");
        assertThat(url).matches("jdbc:.*_test(\\?.*)?");
        jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(url, System.getenv("MARKETPLACE_IT_DB_USER"),
                System.getenv().getOrDefault("MARKETPLACE_IT_DB_PASSWORD", "")));
        clean();
        InventoryMovementRepository kardex = new InventoryMovementRepository() {
            @Override
            public InventoryMovementDto save(InventoryMovementDto dto) {
                KARDEX.add(dto);
                return dto;
            }

            @Override
            public org.springframework.data.domain.Page<InventoryMovementDto> findAll(org.springframework.data.domain.Pageable p) {
                throw new UnsupportedOperationException();
            }
        };
        PurchaseStockEntryService entry = new PurchaseStockEntryService(new JdbcStockItems(), kardex, productId -> { },
                new SkuCostJdbcStore(jdbc));
        orders = new PurchaseOrderService(new PurchaseOrderJdbcStore(jdbc), entry, () -> actor,
                Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneId.of("America/Bogota")));
        supplier = supplier("PROV-IT1", "Textiles Andes", "ACTIVE", 5, "300000");
        blocked = supplier("PROV-IT2", "Bloqueado SAS", "BLOCKED", null, null);
        warehouse = jdbc.queryForObject("SELECT MIN(id_warehouse) FROM warehouses WHERE active = 1", new MapSqlParameterSource(), Long.class);
        jdbc.update("""
                INSERT INTO supplier_products (supplier_id, product_id, sku_id, supplier_code, unit_cost, min_order_qty, preferred, active)
                SELECT :s, product_id, id_sku, CONCAT('TA-', id_sku), 25000, 10, 1, 1 FROM product_skus WHERE id_sku = 101""",
                new MapSqlParameterSource("s", supplier));
        // Costo del catálogo y 4 unidades previas en otra bodega: valoran el primer promedio.
        jdbc.update("UPDATE product_skus SET cost_price = 20000 WHERE id_sku = 101", new MapSqlParameterSource());
        jdbc.update("INSERT INTO stock_items (sku_id, product_id, warehouse_id, on_hand) SELECT id_sku, product_id, 2, 4 FROM product_skus"
                + " WHERE id_sku = 101", new MapSqlParameterSource());
    }

    @AfterAll
    static void tearDown() {
        clean();
    }

    static void clean() {
        for (String t : List.of("inv_sku_cost_history", "inv_sku_costs", "inv_purchase_receipt_lines", "inv_purchase_receipts", "inv_purchase_order_events", "inv_purchase_order_lines",
                "inv_purchase_orders", "supplier_products", "stock_items")) {
            jdbc.update("DELETE FROM " + t, new MapSqlParameterSource());
        }
        jdbc.update("DELETE FROM suppliers WHERE code LIKE 'PROV-IT%'", new MapSqlParameterSource());
    }

    static long supplier(String code, String name, String status, Integer lead, String min) {
        jdbc.update("INSERT INTO suppliers (code, name, status, lead_time_days, min_order_amount) VALUES (:c, :n, :s, :l, :m)",
                new MapSqlParameterSource("c", code).addValue("n", name).addValue("s", status).addValue("l", lead)
                        .addValue("m", min == null ? null : new BigDecimal(min)));
        return jdbc.queryForObject("SELECT MAX(id_supplier) FROM suppliers", new MapSqlParameterSource(), Long.class);
    }

    /** Existencias con JDBC (en la app es el adaptador JPA con bloqueo pesimista). */
    static final class JdbcStockItems implements StockItemRepository {
        @Override
        public StockItem lockOrEmpty(Long skuId, Long productId, Long warehouseId) {
            return jdbc.query("SELECT * FROM stock_items WHERE sku_id = :s AND warehouse_id = :w AND deleted = 0 FOR UPDATE",
                    new MapSqlParameterSource("s", skuId).addValue("w", warehouseId), (rs, i) -> new StockItem(rs.getLong("id_stock_item"),
                            skuId, rs.getLong("product_id"), warehouseId, rs.getInt("on_hand"), rs.getInt("reserved"), rs.getInt("min_threshold")))
                    .stream().findFirst().orElseGet(() -> StockItem.empty(skuId, productId, warehouseId));
        }

        @Override
        public StockItem save(StockItem item) {
            MapSqlParameterSource p = new MapSqlParameterSource("id", item.getId()).addValue("s", item.getSkuId())
                    .addValue("p", item.getProductId()).addValue("w", item.getWarehouseId()).addValue("h", item.getOnHand())
                    .addValue("r", item.getReserved()).addValue("m", item.getMinThreshold());
            if (item.getId() == null) {
                jdbc.update("INSERT INTO stock_items (sku_id, product_id, warehouse_id, on_hand, reserved, min_threshold)"
                        + " VALUES (:s, :p, :w, :h, :r, :m)", p);
            } else {
                jdbc.update("UPDATE stock_items SET on_hand = :h, reserved = :r, min_threshold = :m WHERE id_stock_item = :id", p);
            }
            return item;
        }

        @Override
        public List<StockItem> lockAllForSku(Long skuId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int totalOnHand(Long skuId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long totalOnHandInWarehouse(Long warehouseId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<StockItemDto> find(Long skuId, Long productId, Long warehouseId) {
            throw new UnsupportedOperationException();
        }
    }

    static BigDecimal averageCost(long sku) {
        return jdbc.query("SELECT avg_cost FROM inv_sku_costs WHERE sku_id = :s", new MapSqlParameterSource("s", sku),
                (rs, i) -> rs.getBigDecimal(1)).stream().findFirst().orElse(null);
    }

    static int onHand(long sku) {
        Integer v = jdbc.queryForObject("SELECT COALESCE(SUM(on_hand), 0) FROM stock_items WHERE sku_id = :s AND warehouse_id = :w",
                new MapSqlParameterSource("s", sku).addValue("w", warehouse), Integer.class);
        return v == null ? 0 : v;
    }

    @Test @Order(1)
    void draftUsesSupplierCostsAndLeadTime() {
        OrderDetailDto draft = orders.create(new SaveOrderRequest(supplier, warehouse, null, "Temporada",
                List.of(new LineRequest(101L, 10, null), new LineRequest(102L, 4, new BigDecimal("12000"))), null));
        orderId = draft.summary().id();
        assertThat(draft.summary().number()).startsWith("OC-");
        assertThat(draft.summary().status()).isEqualTo("DRAFT");
        assertThat(draft.summary().expectedDate()).isEqualTo(LocalDate.of(2026, 11, 14)); // + 5 días de entrega
        assertThat(draft.summary().total()).isEqualByComparingTo("298000"); // 10 × 25.000 + 4 × 12.000
        assertThat(draft.lines()).extracting(LineDto::supplierCode).containsExactly("TA-101", null);
        assertThat(draft.actions()).containsExactly("EDIT", "SEND", "CANCEL");

        assertThatThrownBy(() -> orders.create(new SaveOrderRequest(blocked, warehouse, null, null, List.of(new LineRequest(101L, 1,
                BigDecimal.ONE)), null))).hasMessageContaining("no está activo");
        assertThatThrownBy(() -> orders.create(new SaveOrderRequest(supplier, warehouse, null, null, List.of(new LineRequest(102L, 1, null)),
                null))).hasMessageContaining("costo");
        assertThatThrownBy(() -> orders.create(new SaveOrderRequest(supplier, 999999L, null, null, List.of(new LineRequest(101L, 1, null)),
                null))).isInstanceOf(NotFoundException.class);
    }

    @Test @Order(2)
    void sendRequiresSupplierMinimum() {
        OrderDetailDto draft = orders.get(orderId);
        assertThatThrownBy(() -> orders.send(orderId, new StatusRequest(null, draft.lockVersion()))).hasMessageContaining("mínimo");
        OrderDetailDto edited = orders.update(orderId, new SaveOrderRequest(supplier, warehouse, LocalDate.of(2026, 11, 7), "Temporada",
                List.of(new LineRequest(101L, 10, null), new LineRequest(102L, 5, new BigDecimal("12000"))), draft.lockVersion()));
        assertThat(edited.summary().total()).isEqualByComparingTo("310000");
        assertThatThrownBy(() -> orders.update(orderId, new SaveOrderRequest(supplier, warehouse, null, null,
                List.of(new LineRequest(101L, 1, null)), draft.lockVersion()))).isInstanceOf(ConflictException.class);
        OrderDetailDto sent = orders.send(orderId, new StatusRequest(null, edited.lockVersion()));
        assertThat(sent.summary().status()).isEqualTo("SENT");
        assertThat(sent.summary().overdue()).isTrue(); // se esperaba el 7
        assertThat(sent.actions()).containsExactly("CANCEL", "RECEIVE");
        assertThatThrownBy(() -> orders.update(orderId, new SaveOrderRequest(supplier, warehouse, null, null,
                List.of(new LineRequest(101L, 1, null)), sent.lockVersion()))).hasMessageContaining("borrador");
    }

    @Test @Order(3)
    void partialThenCompleteReceiptAddStock() {
        OrderDetailDto sent = orders.get(orderId);
        long lineA = sent.lines().get(0).id();
        long lineB = sent.lines().get(1).id();
        OrderDetailDto partial = orders.receive(orderId, new ReceiveRequest("REM-1", null, List.of(new ReceiveLine(lineA, 6)), sent.lockVersion()));
        assertThat(partial.summary().status()).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(onHand(101)).isEqualTo(6);
        assertThat(partial.receipts()).hasSize(1);
        assertThat(partial.receipts().get(0).number()).startsWith("REC-");
        assertThatThrownBy(() -> orders.receive(orderId, new ReceiveRequest(null, null, List.of(new ReceiveLine(lineA, 5)),
                partial.lockVersion()))).hasMessageContaining("quedan 4");
        assertThatThrownBy(() -> orders.cancel(orderId, new StatusRequest("Ya no", partial.lockVersion()))).hasMessageContaining("borrador o enviada");

        OrderDetailDto complete = orders.receive(orderId, new ReceiveRequest("REM-2", "Llegó todo", List.of(new ReceiveLine(lineA, 4),
                new ReceiveLine(lineB, 5)), partial.lockVersion()));
        assertThat(complete.summary().status()).isEqualTo("RECEIVED");
        assertThat(complete.summary().receivedUnits()).isEqualTo(15);
        assertThat(onHand(101)).isEqualTo(10);
        assertThat(onHand(102)).isEqualTo(5);
        assertThat(complete.actions()).isEmpty();
        assertThat(complete.events()).extracting(e -> e.action()).containsExactly("CREATE", "EDIT", "SEND", "RECEIVE", "RECEIVE");

        // Kardex: tres entradas PURCHASE_RECEIPT hacia la bodega destino.
        assertThat(KARDEX).extracting(InventoryMovementDto::getReferenceType).containsOnly("PURCHASE_RECEIPT");
        assertThat(KARDEX).extracting(InventoryMovementDto::getQuantity).containsExactly(6, 4, 5);
        // Costo promedio: 4 u. previas al costo del catálogo (20.000) + 6 a 25.000 = 23.000;
        // luego 10 u. a 23.000 + 4 a 25.000 = 23.571,4286. El 102 no tenía existencias: 12.000.
        assertThat(averageCost(101)).isEqualByComparingTo("23571.4286");
        assertThat(averageCost(102)).isEqualByComparingTo("12000");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inv_sku_cost_history", new MapSqlParameterSource(), Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT units_before FROM inv_sku_cost_history WHERE sku_id = 101 ORDER BY id LIMIT 1",
                new MapSqlParameterSource(), Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT cost_price FROM product_skus WHERE id_sku = 101", new MapSqlParameterSource(),
                BigDecimal.class)).isEqualByComparingTo("20000"); // el catálogo no se toca
    }

    @Test @Order(4)
    void closeAndCancelAndStats() {
        OrderDetailDto other = orders.create(new SaveOrderRequest(supplier, warehouse, null, null,
                List.of(new LineRequest(101L, 20, null), new LineRequest(102L, 10, new BigDecimal("30000"))), null));
        OrderDetailDto sent = orders.send(other.summary().id(), new StatusRequest(null, other.lockVersion()));
        OrderDetailDto partial = orders.receive(sent.summary().id(), new ReceiveRequest(null, null,
                List.of(new ReceiveLine(sent.lines().get(0).id(), 20)), sent.lockVersion()));
        OrderDetailDto closed = orders.close(partial.summary().id(), new StatusRequest("El proveedor agotó la referencia", partial.lockVersion()));
        assertThat(closed.summary().status()).isEqualTo("CLOSED");
        assertThat(closed.statusReason()).contains("agotó");

        OrderDetailDto draft = orders.create(new SaveOrderRequest(supplier, warehouse, null, null, List.of(new LineRequest(101L, 1, null)), null));
        assertThat(orders.cancel(draft.summary().id(), new StatusRequest("Duplicada", draft.lockVersion())).summary().status())
                .isEqualTo("CANCELLED");
        var stats = orders.stats();
        assertThat(stats.drafts()).isZero();
        assertThat(stats.receivedUnits30()).isEqualTo(35);
        assertThat(orders.search("RECEIVED", null, null, page()).getTotalElements()).isEqualTo(1);
        assertThat(orders.search(null, supplier, "andes", page()).getTotalElements()).isEqualTo(3);
        assertThatThrownBy(() -> orders.search("LOST", null, null, page())).isInstanceOf(PurchaseRuleViolation.class);
    }

    @Test @Order(5)
    void permissionsAndIsolation() {
        RequestActor previous = actor;
        try {
            actor = new RequestActor(COMPANY, "u-2", "bodega", Set.of("COMPRAS.RECIBIR"));
            assertThat(orders.get(orderId).actions()).isEmpty(); // recibida: nada que hacer
            assertThatThrownBy(() -> orders.create(new SaveOrderRequest(supplier, warehouse, null, null,
                    List.of(new LineRequest(101L, 1, null)), null))).isInstanceOf(ForbiddenException.class);
            actor = new RequestActor(COMPANY, "u-3", "sin permisos", Set.of());
            assertThatThrownBy(() -> orders.stats()).isInstanceOf(ForbiddenException.class);
            actor = new RequestActor(3, "u-9", "otra", ALL);
            assertThatThrownBy(() -> orders.get(orderId)).isInstanceOf(NotFoundException.class);
        } finally {
            actor = previous;
        }
    }

    @Test @Order(6)
    void suggestionsFromLowStock() {
        // Bodega destino: 101 tiene 30 (10 + 20) con mínimo 40; 102 tiene 5 con mínimo 5 (sin proveedor).
        // Bodega 2: 101 tiene 4 con mínimo 10. El proveedor bloqueado (preferido) no se sugiere.
        jdbc.update("UPDATE stock_items SET min_threshold = 40 WHERE sku_id = 101 AND warehouse_id = :w", new MapSqlParameterSource("w", warehouse));
        jdbc.update("UPDATE stock_items SET min_threshold = 5 WHERE sku_id = 102 AND warehouse_id = :w", new MapSqlParameterSource("w", warehouse));
        jdbc.update("UPDATE stock_items SET min_threshold = 10 WHERE sku_id = 101 AND warehouse_id = 2", new MapSqlParameterSource());
        jdbc.update("INSERT INTO supplier_products (supplier_id, product_id, sku_id, unit_cost, min_order_qty, preferred, active)"
                + " SELECT :s, product_id, id_sku, 1000, 1, 1, 1 FROM product_skus WHERE id_sku = 101", new MapSqlParameterSource("s", blocked));

        SuggestionsDto all = orders.suggestions(null);
        assertThat(all.lowStockSkus()).isEqualTo(3);
        assertThat(all.groups()).extracting(SuggestionGroupDto::warehouseId).containsExactlyInAnyOrder(warehouse, 2L);
        SuggestionGroupDto main = all.groups().stream().filter(g -> g.warehouseId() == warehouse).findFirst().orElseThrow();
        assertThat(main.supplierId()).isEqualTo(supplier);
        assertThat(main.lines()).singleElement().satisfies(l -> {
            assertThat(l.suggested()).isEqualTo(50); // hasta 80
            assertThat(l.unitCost()).isEqualByComparingTo("25000");
            assertThat(l.averageCost()).isNotNull();
        });
        assertThat(main.total()).isEqualByComparingTo("1250000");
        assertThat(main.belowMinimumAmount()).isFalse();
        SuggestionGroupDto other = all.groups().stream().filter(g -> g.warehouseId() == 2L).findFirst().orElseThrow();
        assertThat(other.lines().get(0).suggested()).isEqualTo(16);
        assertThat(all.withoutSupplier()).singleElement().satisfies(g -> assertThat(g.lines().get(0).skuId()).isEqualTo(102L));

        // Un borrador de 45 hacia la bodega destino descuenta: faltan 5, pero el mínimo del proveedor es 10.
        orders.create(new SaveOrderRequest(supplier, warehouse, null, null, List.of(new LineRequest(101L, 45, null)), null));
        SuggestionsDto filtered = orders.suggestions(warehouse);
        assertThat(filtered.groups()).singleElement().satisfies(g -> {
            assertThat(g.lines().get(0).onOrder()).isEqualTo(45);
            assertThat(g.lines().get(0).suggested()).isEqualTo(10);
            assertThat(g.belowMinimumAmount()).isTrue(); // 250.000 < 300.000
        });
        orders.create(new SaveOrderRequest(supplier, warehouse, null, null, List.of(new LineRequest(101L, 10, null)), null));
        SuggestionsDto covered = orders.suggestions(warehouse);
        assertThat(covered.groups()).isEmpty();
        assertThat(covered.coveredByOpenOrders()).isEqualTo(1);

        RequestActor previous = actor;
        try {
            actor = new RequestActor(COMPANY, "u-3", "sin permisos", Set.of());
            assertThatThrownBy(() -> orders.suggestions(null)).isInstanceOf(ForbiddenException.class);
            actor = new RequestActor(3, "u-9", "otra", ALL); // las órdenes abiertas de otra empresa no descuentan
            assertThat(orders.suggestions(warehouse).groups()).singleElement()
                    .satisfies(g -> assertThat(g.lines().get(0).suggested()).isEqualTo(50));
        } finally {
            actor = previous;
        }
    }

    static org.springframework.data.domain.Pageable page() {
        return PageableFactory.of(0, 20, null, Set.of("createdAt"), Sort.by(Sort.Order.desc("createdAt")));
    }
}
