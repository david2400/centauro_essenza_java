package com.essenza.draco.modules.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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

import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.AdjustmentRequest;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.DocumentDto;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.LineRequest;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.MovementDto;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.MovementFilter;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.SaveDocumentRequest;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.StockFilter;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.StockRowDto;
import com.essenza.draco.modules.marketplace.application.dto.OfferDtos.SaveOfferRequest;
import com.essenza.draco.modules.marketplace.application.service.ConsignmentService;
import com.essenza.draco.modules.marketplace.application.service.OfferService;
import com.essenza.draco.modules.marketplace.application.service.OfferStockService;
import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.ConsignmentJdbcStore;
import com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.OfferJdbcStore;
import com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.OfferSaleJdbcStore;
import com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.SellerJdbcStore;
import com.essenza.draco.shared.common.catalog.SkuPricing;
import com.essenza.draco.shared.common.marketplace.ConsignmentReturns.ReturnedLine;
import com.essenza.draco.shared.common.web.PageableFactory;
import com.essenza.draco.shared.exceptions.ForbiddenException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Consignación contra MySQL/MariaDB con los adaptadores JDBC reales: recepción, oferta con
 * despacho de la plataforma, venta, cancelación, devolución de cliente, ajuste y devolución al
 * vendedor. Solo contra una base *_test.
 */
@EnabledIfEnvironmentVariable(named = "MARKETPLACE_IT_DB_URL", matches = "jdbc:.*_test(\\?.*)?")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConsignmentFlowIT {

    static final long COMPANY = 2;
    static final Set<String> ALL = Set.of("CONSIGNACION.VER", "CONSIGNACION.GESTIONAR", "OFERTAS.VER", "OFERTAS.GESTIONAR",
            "COMISIONES.VER", "COMISIONES.GESTIONAR");
    static final List<String> CONSIGNMENT = List.of("mp_consignment_movements", "mp_consignment_documents", "mp_consignment_stock");
    static RequestActor actor = new RequestActor(COMPANY, "u-1", "Bodeguero", ALL);
    static NamedParameterJdbcTemplate jdbc;
    static ConsignmentService consignment;
    static OfferService offers;
    static OfferStockService stock;
    static com.essenza.draco.modules.marketplace.application.service.SellerAccountService accounts;
    static long vendor, north, south, closed, offerId, order1, line1, order2, line2;

    @BeforeAll
    static void setUp() {
        String url = System.getenv("MARKETPLACE_IT_DB_URL");
        assertThat(url).matches("jdbc:.*_test(\\?.*)?");
        jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(url, System.getenv("MARKETPLACE_IT_DB_USER"),
                System.getenv().getOrDefault("MARKETPLACE_IT_DB_PASSWORD", "")));
        cleanConsignment();
        for (String t : List.of("mp_seller_ledger", "mp_commission_adjustments", "mp_commissions", "mp_offer_reservations",
                "product_orders", "orders", "purchases", "mp_offer_events", "mp_offers", "mp_plan_assignments", "mp_plan_events",
                "mp_seller_events", "mp_seller_documents", "mp_seller_contacts", "mp_sellers", "mp_plans")) {
            jdbc.update("DELETE FROM " + t, new MapSqlParameterSource());
        }
        Clock clock = Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneId.of("America/Bogota"));
        var ledgerStore = new com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.SellerLedgerJdbcStore(jdbc);
        consignment = new ConsignmentService(new ConsignmentJdbcStore(jdbc), () -> actor, clock, ledgerStore);
        accounts = new com.essenza.draco.modules.marketplace.application.service.SellerAccountService(new SellerJdbcStore(jdbc),
                ledgerStore, new com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.CommissionJdbcStore(jdbc),
                new com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.AssignmentJdbcStore(jdbc), () -> actor, clock,
                consignment);
        SkuPricing pricing = (productId, skuId) -> jdbc.query(
                "SELECT id_sku, product_id, code, name, price FROM product_skus WHERE id_sku = :id",
                new MapSqlParameterSource("id", skuId),
                (rs, i) -> new SkuPricing.SkuSnapshot(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getBigDecimal(5), true))
                .stream().findFirst().orElseThrow(() -> new NotFoundException("SKU no encontrado: " + skuId));
        offers = new OfferService(new OfferJdbcStore(jdbc), new SellerJdbcStore(jdbc), pricing, () -> actor, clock, "COP", false,
                consignment);
        stock = new OfferStockService(new OfferSaleJdbcStore(jdbc), clock, consignment);
        vendor = seller(COMPANY, "Moda Andina");
        seller(3, "Otra empresa");
        north = warehouse("Bodega Norte");
        south = warehouse("Bodega Sur");
        closed = warehouse("Bodega cerrada");
    }

    @AfterAll
    static void tearDown() {
        // Los demás IT borran vendedores sin conocer la consignación.
        cleanConsignment();
    }

    static void cleanConsignment() {
        for (String t : CONSIGNMENT) {
            jdbc.update("DELETE FROM " + t, new MapSqlParameterSource());
        }
    }

    static long seller(long company, String name) {
        jdbc.update("""
                INSERT INTO mp_sellers (company_id, kind, legal_name, trade_name, doc_type, doc_number, status, created_at,
                  created_by, updated_at, updated_by)
                VALUES (:company, 'EXTERNAL', :name, :name, 'NIT', CONCAT('900', FLOOR(RAND() * 1000000)), 'APPROVED', NOW(6),
                  'it', NOW(6), 'it')""", new MapSqlParameterSource("company", company).addValue("name", name));
        return jdbc.queryForObject("SELECT MAX(id) FROM mp_sellers", new MapSqlParameterSource(), Long.class);
    }

    static long warehouse(String name) {
        return jdbc.queryForObject("SELECT MIN(id_warehouse) FROM warehouses WHERE name = :name",
                new MapSqlParameterSource("name", name), Long.class);
    }

    static long[] orderWithLine(long offer, String number) {
        return orderWithLine(offer, number, 1);
    }

    static java.math.BigDecimal ledger(String type) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM mp_seller_ledger WHERE seller_id = :s AND entry_type = :t",
                new MapSqlParameterSource("s", vendor).addValue("t", type), java.math.BigDecimal.class);
    }

    static long[] orderWithLine(long offer, String number, int quantity) {
        jdbc.update("""
                INSERT INTO orders (order_number, state, total, stock_managed, seller_id, channel, created_at)
                VALUES (:number, 'PENDING', 95000, 1, :seller, 'ONLINE', NOW(6))""",
                new MapSqlParameterSource("number", number).addValue("seller", vendor));
        long order = jdbc.queryForObject("SELECT MAX(id_order) FROM orders", new MapSqlParameterSource(), Long.class);
        jdbc.update("INSERT INTO product_orders (order_id, quantity, total, offer_id) VALUES (:o, :q, :total, :f)",
                new MapSqlParameterSource("o", order).addValue("f", offer).addValue("q", quantity)
                        .addValue("total", new BigDecimal(95000L * quantity)));
        long line = jdbc.queryForObject("SELECT MAX(id_product_order) FROM product_orders", new MapSqlParameterSource(), Long.class);
        return new long[] {order, line};
    }

    static DocumentDto document(String kind, long warehouse, long... skuQty) {
        List<LineRequest> lines = new java.util.ArrayList<>();
        for (int i = 0; i < skuQty.length; i += 2) {
            lines.add(new LineRequest(skuQty[i], (int) skuQty[i + 1]));
        }
        return consignment.saveDocument(new SaveDocumentRequest(kind, vendor, warehouse, "REM-77", null, lines));
    }

    static int onHand(long warehouse, long sku) {
        Integer value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(on_hand), 0) FROM mp_consignment_stock
                WHERE seller_id = :seller AND warehouse_id = :w AND sku_id = :sku""",
                new MapSqlParameterSource("seller", vendor).addValue("w", warehouse).addValue("sku", sku), Integer.class);
        return value == null ? 0 : value;
    }

    static int offerAvailable() {
        return jdbc.queryForObject("SELECT available_quantity FROM mp_offers WHERE id = :id",
                new MapSqlParameterSource("id", offerId), Integer.class);
    }

    static List<StockRowDto> rows() {
        return consignment.stock(new StockFilter(vendor, null, null, true),
                PageableFactory.of(0, 50, null, Set.of("onHand"), Sort.by(Sort.Order.desc("onHand")))).getContent();
    }

    @Test @Order(1)
    void receiptAddsSellerStockSeparatedByWarehouse() {
        DocumentDto receipt = document("RECEIPT", north, 101, 6, 102, 2);
        assertThat(receipt.number()).startsWith("CSG-");
        assertThat(receipt.units()).isEqualTo(8);
        assertThat(receipt.lines()).extracting(MovementDto::kind).containsOnly("RECEIPT");
        document("RECEIPT", south, 101, 4);
        assertThat(onHand(north, 101)).isEqualTo(6);
        assertThat(onHand(south, 101)).isEqualTo(4);
        assertThat(rows()).hasSize(3);
        var stats = consignment.stats();
        assertThat(stats.units()).isEqualTo(12);
        assertThat(stats.withoutPlatformOffer()).isEqualTo(2);

        assertThatThrownBy(() -> document("RECEIPT", closed, 101, 1)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> document("RECEIPT", north, 101, 1, 101, 2)).hasMessageContaining("repetido");
        assertThatThrownBy(() -> document("SALE", north, 101, 1)).isInstanceOf(MarketplaceRuleViolation.class);
    }

    @Test @Order(2)
    void platformOfferTakesItsAvailabilityFromConsignment() {
        var created = offers.create(new SaveOfferRequest(vendor, 101L, "COP", new BigDecimal("95000"), 999, 1, null, "PLATFORM",
                null, null));
        offerId = created.id();
        assertThat(created.availableQuantity()).isEqualTo(10); // 6 + 4, no 999
        jdbc.update("UPDATE mp_offers SET status = 'ACTIVE' WHERE id = :id", new MapSqlParameterSource("id", offerId));
        document("RECEIPT", north, 101, 2);
        assertThat(offerAvailable()).isEqualTo(12);
        assertThat(consignment.stats().withoutPlatformOffer()).isEqualTo(1); // queda el SKU 102
    }

    @Test @Order(3)
    void paidSaleDiscountsTheFullestWarehouseAndCancellationRestoresIt() {
        long[] o = orderWithLine(offerId, "ORD-C1", 7);
        order1 = o[0];
        line1 = o[1];
        stock.reserveLine(COMPANY, offerId, order1, line1, 7);
        assertThat(offerAvailable()).isEqualTo(5);
        // Reservado: no se puede devolver al vendedor más de lo libre.
        assertThatThrownBy(() -> document("RETURN_TO_SELLER", north, 101, 6)).hasMessageContaining("libres");

        stock.commitOrder(order1);
        assertThat(onHand(north, 101)).isEqualTo(1); // salió de la Norte (tenía 8)
        assertThat(onHand(south, 101)).isEqualTo(4);
        assertThat(offerAvailable()).isEqualTo(5);
        // La tienda cobró (todo el subpedido es de consignación): se le abona al vendedor lo que pagó el comprador.
        assertThat(ledger("CONSIGNMENT_SALE")).isEqualByComparingTo("-665000");

        stock.restockOrder(order1);
        assertThat(ledger("CONSIGNMENT_REVERSAL")).isEqualByComparingTo("665000");
        assertThat(onHand(north, 101)).isEqualTo(8);
        assertThat(offerAvailable()).isEqualTo(12);
        List<MovementDto> moves = consignment.movements(new MovementFilter(null, null, null, order1, null),
                PageableFactory.of(0, 20, null, Set.of("createdAt"), Sort.by(Sort.Order.asc("createdAt")))).getContent();
        assertThat(moves).extracting(MovementDto::kind).containsExactlyInAnyOrder("SALE", "SALE_CANCEL");
        assertThat(moves).extracting(MovementDto::orderNumber).containsOnly("ORD-C1");
    }

    @Test @Order(4)
    void customerReturnGoesBackToTheSeller() {
        long[] o = orderWithLine(offerId, "ORD-C2", 2);
        order2 = o[0];
        line2 = o[1];
        stock.reserveLine(COMPANY, offerId, order2, line2, 2);
        stock.commitOrder(order2);
        assertThat(onHand(north, 101)).isEqualTo(6);
        Set<Long> taken = consignment.receiveReturn(order2, 99L, south, List.of(new ReturnedLine(line2, 101L, 2),
                new ReturnedLine(424242L, 101L, 1)));
        assertThat(taken).containsExactly(line2); // la otra línea no es de consignación: va al inventario propio
        assertThat(ledger("CONSIGNMENT_SALE")).isEqualByComparingTo("-855000");
        assertThat(ledger("CONSIGNMENT_REVERSAL")).isEqualByComparingTo("855000"); // se devolvieron las 2 unidades
        assertThat(onHand(south, 101)).isEqualTo(6);
        assertThat(offerAvailable()).isEqualTo(12);
    }

    @Test @Order(5)
    void adjustmentAndReturnToSeller() {
        DocumentDto adjustment = consignment.adjust(new AdjustmentRequest(vendor, south, 101L, 5, "Conteo mensual"));
        assertThat(adjustment.kind()).isEqualTo("ADJUSTMENT");
        assertThat(adjustment.units()).isEqualTo(1);
        assertThat(adjustment.lines()).extracting(MovementDto::quantity).containsExactly(-1);
        assertThat(onHand(south, 101)).isEqualTo(5);
        assertThat(offerAvailable()).isEqualTo(11);

        DocumentDto back = document("RETURN_TO_SELLER", north, 102, 2);
        assertThat(back.lines()).extracting(MovementDto::balanceAfter).containsExactly(0);
        assertThatThrownBy(() -> document("RETURN_TO_SELLER", north, 102, 1)).hasMessageContaining("hay 0 unidades");
        assertThat(rows()).extracting(StockRowDto::skuId).containsOnly(101L);
        StockRowDto row = rows().get(0);
        assertThat(row.available()).isEqualTo(11);
        assertThat(row.offerFulfillment()).isEqualTo("PLATFORM");
        assertThat(consignment.documents(vendor, null, PageableFactory.of(0, 20, null, Set.of("createdAt"),
                Sort.by(Sort.Order.desc("createdAt")))).getTotalElements()).isEqualTo(5); // 3 recepciones, 1 ajuste, 1 devolución
        assertThatThrownBy(() -> consignment.movements(new MovementFilter(null, null, null, null, "LOST"),
                PageableFactory.of(0, 20, null, Set.of("createdAt"), Sort.by(Sort.Order.desc("createdAt")))))
                .isInstanceOf(MarketplaceRuleViolation.class);
    }

    @Test @Order(6)
    void settlementAndStorageUnitDays() {
        long[] o = orderWithLine(offerId, "ORD-C3", 1);
        stock.reserveLine(COMPANY, offerId, o[0], o[1], 1);
        stock.commitOrder(o[0]);
        var account = accounts.statement(vendor, null);
        assertThat(account.balance()).isEqualByComparingTo("-95000"); // la tienda le debe al vendedor
        var tooMuch = new com.essenza.draco.modules.marketplace.application.dto.BillingDtos.SellerPaymentRequest(
                new BigDecimal("100000"), null, "TRF-1", null, "settle-key-1");
        assertThatThrownBy(() -> accounts.registerSettlement(vendor, tooMuch)).hasMessageContaining("no se puede liquidar más");
        var settled = accounts.registerSettlement(vendor, new com.essenza.draco.modules.marketplace.application.dto.BillingDtos
                .SellerPaymentRequest(new BigDecimal("95000"), null, "TRF-1", "Liquidación noviembre", "settle-key-2"));
        assertThat(settled.balance()).isEqualByComparingTo("0");
        assertThat(ledger("SETTLEMENT")).isEqualByComparingTo("95000");

        // Unidades-día: todo se movió el 9 de noviembre; los días siguientes conservan el saldo.
        int units = jdbc.queryForObject("SELECT SUM(on_hand) FROM mp_consignment_stock WHERE seller_id = :s",
                new MapSqlParameterSource("s", vendor), Integer.class);
        assertThat(consignment.unitDays(COMPANY, vendor, java.time.LocalDate.of(2026, 11, 9), java.time.LocalDate.of(2026, 11, 11)))
                .isEqualTo(3L * units);
        assertThat(consignment.unitDays(COMPANY, vendor, java.time.LocalDate.of(2026, 11, 1), java.time.LocalDate.of(2026, 11, 8)))
                .isZero();
    }

    @Test @Order(7)
    void permissionsAndIsolation() {
        long documentId = consignment.documents(vendor, "RECEIPT", PageableFactory.of(0, 1, null, Set.of("createdAt"),
                Sort.by(Sort.Order.desc("createdAt")))).getContent().get(0).id();
        RequestActor previous = actor;
        try {
            actor = new RequestActor(COMPANY, "u-2", "lector", Set.of("CONSIGNACION.VER"));
            assertThat(rows()).isNotEmpty();
            assertThatThrownBy(() -> document("RECEIPT", north, 101, 1)).isInstanceOf(ForbiddenException.class);
            actor = new RequestActor(COMPANY, "u-3", "sin permisos", Set.of());
            assertThatThrownBy(ConsignmentFlowIT::rows).isInstanceOf(ForbiddenException.class);
            actor = new RequestActor(3, "u-9", "otra", ALL);
            assertThat(consignment.stats().units()).isZero();
            assertThatThrownBy(() -> consignment.document(documentId)).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> document("RECEIPT", north, 101, 1)).isInstanceOf(NotFoundException.class); // vendedor ajeno
        } finally {
            actor = previous;
        }
    }
}
