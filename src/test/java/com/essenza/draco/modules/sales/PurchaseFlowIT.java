package com.essenza.draco.modules.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

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

import com.essenza.draco.modules.marketplace.application.service.OfferStockService;
import com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.OfferSaleJdbcStore;
import com.essenza.draco.modules.sales.application.output.repository.PurchaseLedger;
import com.essenza.draco.modules.sales.application.output.repository.PurchaseLedger.NewPurchase;
import com.essenza.draco.modules.sales.application.output.repository.PurchaseLedger.PurchaseFilter;
import com.essenza.draco.modules.sales.application.output.repository.PurchaseLedger.PurchaseRow;
import com.essenza.draco.modules.sales.domain.model.OrderStatus;
import com.essenza.draco.modules.sales.domain.model.purchase.PurchaseStatus;
import com.essenza.draco.modules.sales.infrastructure.outbound.repositories.purchase.PurchaseJdbcAdapter;
import com.essenza.draco.shared.common.marketplace.OfferStock;
import com.essenza.draco.shared.common.web.PageableFactory;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;

/**
 * Compras multivendedor contra MySQL/MariaDB: reservas de ofertas (marketplace) y compras con su
 * estado derivado en SQL (ventas), con los adaptadores JDBC reales. Sin contenedor Spring.
 *
 * <p>NO corre con {@code mvn test}. Borra las filas de purchases, orders, product_orders y mp_*:
 * solo contra una base cuyo nombre termine en {@code _test} con V17–V19 aplicadas. Variables
 * MARKETPLACE_IT_DB_URL, MARKETPLACE_IT_DB_USER y MARKETPLACE_IT_DB_PASSWORD.
 */
@EnabledIfEnvironmentVariable(named = "MARKETPLACE_IT_DB_URL", matches = "jdbc:.*_test(\\?.*)?")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PurchaseFlowIT {

    static final long COMPANY = 2;
    static NamedParameterJdbcTemplate jdbc;
    static OfferStock stock;
    static PurchaseLedger purchases;
    static long own, vendor, suspended, foreign;
    static long offer, offerSuspended, offerDraft, offerForeign;

    @BeforeAll
    static void setUp() {
        String url = System.getenv("MARKETPLACE_IT_DB_URL");
        assertThat(url).as("solo bases de prueba (*_test)").matches("jdbc:.*_test(\\?.*)?");
        jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(url, System.getenv("MARKETPLACE_IT_DB_USER"),
                System.getenv().getOrDefault("MARKETPLACE_IT_DB_PASSWORD", "")));
        for (String t : List.of("mp_seller_ledger", "mp_commission_adjustments", "mp_commissions", "mp_offer_reservations", "product_orders", "orders", "purchases", "mp_offer_events",
                "mp_offers", "mp_plan_assignments", "mp_plan_events", "mp_seller_events", "mp_seller_documents",
                "mp_seller_contacts", "mp_sellers", "mp_plans")) {
            jdbc.update("DELETE FROM " + t, new MapSqlParameterSource());
        }
        Clock clock = Clock.fixed(Instant.parse("2026-11-07T15:00:00Z"), ZoneId.of("America/Bogota"));
        stock = new OfferStockService(new OfferSaleJdbcStore(jdbc), clock);
        purchases = new PurchaseJdbcAdapter(jdbc, null);
        own = seller(COMPANY, "OWN", "Tienda propia", "APPROVED");
        vendor = seller(COMPANY, "EXTERNAL", "Moda Andina", "APPROVED");
        suspended = seller(COMPANY, "EXTERNAL", "Ropa Sur", "SUSPENDED");
        foreign = seller(3, "EXTERNAL", "Otra empresa", "APPROVED");
        offer = offer(COMPANY, vendor, 101, "95000", 5, "ACTIVE");
        offerSuspended = offer(COMPANY, suspended, 101, "90000", 5, "ACTIVE");
        offerDraft = offer(COMPANY, vendor, 102, "70000", 5, "DRAFT");
        offerForeign = offer(3, foreign, 101, "80000", 5, "ACTIVE");
    }

    static long seller(long company, String kind, String name, String status) {
        jdbc.update("""
                INSERT INTO mp_sellers (company_id, kind, legal_name, trade_name, doc_type, doc_number, status, created_at,
                  created_by, updated_at, updated_by)
                VALUES (:company, :kind, :name, :name, 'NIT', CONCAT('900', FLOOR(RAND() * 1000000)), :status, NOW(6), 'it',
                  NOW(6), 'it')""",
                new MapSqlParameterSource("company", company).addValue("kind", kind).addValue("name", name)
                        .addValue("status", status));
        return jdbc.queryForObject("SELECT MAX(id) FROM mp_sellers", new MapSqlParameterSource(), Long.class);
    }

    static long offer(long company, long seller, long sku, String price, int quantity, String status) {
        jdbc.update("""
                INSERT INTO mp_offers (company_id, seller_id, sku_id, currency, price, available_quantity, handling_days,
                  item_condition, fulfillment, status, lock_version, created_at, created_by, updated_at, updated_by)
                VALUES (:company, :seller, :sku, 'COP', :price, :qty, 1, 'NEW', 'SELLER', :status, 0, NOW(6), 'it', NOW(6), 'it')""",
                new MapSqlParameterSource("company", company).addValue("seller", seller).addValue("sku", sku)
                        .addValue("price", new BigDecimal(price)).addValue("qty", quantity).addValue("status", status));
        return jdbc.queryForObject("SELECT MAX(id) FROM mp_offers", new MapSqlParameterSource(), Long.class);
    }

    static long order(long purchase, Long seller, String state, String total) {
        jdbc.update("INSERT INTO orders (state, total, stock_managed) VALUES (:state, :total, 1)",
                new MapSqlParameterSource("state", state).addValue("total", new BigDecimal(total)));
        long id = jdbc.queryForObject("SELECT MAX(id_order) FROM orders", new MapSqlParameterSource(), Long.class);
        jdbc.update("UPDATE orders SET order_number = CONCAT('ORD-', id_order) WHERE id_order = :id",
                new MapSqlParameterSource("id", id));
        purchases.linkOrder(purchase, id, seller);
        return id;
    }

    static long line(long order, int quantity) {
        jdbc.update("INSERT INTO product_orders (order_id, quantity) VALUES (:order, :qty)",
                new MapSqlParameterSource("order", order).addValue("qty", quantity));
        return jdbc.queryForObject("SELECT MAX(id_product_order) FROM product_orders", new MapSqlParameterSource(), Long.class);
    }

    static int available(long offerId) {
        return jdbc.queryForObject("SELECT available_quantity FROM mp_offers WHERE id = :id",
                new MapSqlParameterSource("id", offerId), Integer.class);
    }

    static String reservation(long lineId) {
        return jdbc.queryForObject("SELECT status FROM mp_offer_reservations WHERE order_line_id = :id",
                new MapSqlParameterSource("id", lineId), String.class);
    }

    static long newPurchase(long company) {
        return purchases.insert(company, new NewPurchase("BACKOFFICE", "COP", "Ana Pérez", "CC", "1020", "ana@x.co", null),
                "u-1", "demo", Instant.parse("2026-11-07T15:00:00Z"));
    }

    @Test @Order(1)
    void offersForSaleAndValidation() {
        assertThat(stock.forSale(COMPANY, 101L)).extracting(OfferStock.OfferQuote::offerId).containsExactly(offer);
        assertThat(stock.forSale(3, 101L)).extracting(OfferStock.OfferQuote::offerId).containsExactly(offerForeign);
        assertThat(stock.ownSeller(COMPANY)).map(OfferStock.SellerRef::id).contains(own);
        assertThat(stock.lockForSale(COMPANY, offer).price()).isEqualByComparingTo("95000");
        assertThatThrownBy(() -> stock.lockForSale(COMPANY, offerSuspended)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("aprobado");
        assertThatThrownBy(() -> stock.lockForSale(COMPANY, offerDraft)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("activa");
        // Aislamiento: la oferta de otra empresa no existe para esta.
        assertThatThrownBy(() -> stock.lockForSale(COMPANY, offerForeign)).isInstanceOf(NotFoundException.class);
    }

    @Test @Order(2)
    void reservationsFollowTheOrder() {
        long purchase = newPurchase(COMPANY);
        long paid = order(purchase, vendor, "PENDING", "190000");
        long paidLine = line(paid, 2);
        stock.reserveLine(COMPANY, offer, paid, paidLine, 2);
        assertThat(available(offer)).isEqualTo(3);
        assertThat(stock.forSale(COMPANY, 101L).get(0).availableQuantity()).isEqualTo(3);

        long cancelled = order(purchase, vendor, "PENDING", "285000");
        long cancelledLine = line(cancelled, 3);
        stock.reserveLine(COMPANY, offer, cancelled, cancelledLine, 3);
        assertThat(available(offer)).isZero();
        assertThat(stock.forSale(COMPANY, 101L)).isEmpty();
        long third = order(purchase, vendor, "PENDING", "95000");
        assertThatThrownBy(() -> stock.reserveLine(COMPANY, offer, third, line(third, 1), 1))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Solo quedan 0");

        stock.commitOrder(paid);
        stock.commitOrder(paid); // repetible
        assertThat(reservation(paidLine)).isEqualTo("COMMITTED");
        assertThat(available(offer)).isZero();

        stock.releaseOrder(cancelled, false);
        stock.releaseOrder(cancelled, false);
        assertThat(reservation(cancelledLine)).isEqualTo("RELEASED");
        assertThat(available(offer)).isEqualTo(3);

        stock.restockOrder(paid);
        assertThat(reservation(paidLine)).isEqualTo("RETURNED");
        assertThat(available(offer)).isEqualTo(5);
        stock.restockOrder(paid);
        assertThat(available(offer)).isEqualTo(5);

        long expiring = order(purchase, vendor, "PENDING", "95000");
        long expiringLine = line(expiring, 1);
        stock.reserveLine(COMPANY, offer, expiring, expiringLine, 1);
        stock.releaseOrder(expiring, true);
        assertThat(reservation(expiringLine)).isEqualTo("EXPIRED");
        assertThat(available(offer)).isEqualTo(5);
        assertThat(stock.reservations(paid)).extracting(OfferStock.Reservation::status).containsExactly("RETURNED");

        // Una reserva sube lock_version: una edición con datos viejos de la oferta no la pisa.
        int lock = jdbc.queryForObject("SELECT lock_version FROM mp_offers WHERE id = :id",
                new MapSqlParameterSource("id", offer), Integer.class);
        assertThat(lock).isGreaterThanOrEqualTo(6);
    }

    @Test @Order(3)
    void purchaseStatusIsDerivedTheSameInSqlAndJava() {
        Random random = new Random(42);
        OrderStatus[] states = OrderStatus.values();
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            long purchase = newPurchase(COMPANY);
            ids.add(purchase);
            int n = 1 + random.nextInt(4);
            List<OrderStatus> mine = new ArrayList<>();
            for (int k = 0; k < n; k++) {
                OrderStatus state = states[random.nextInt(states.length)];
                mine.add(state);
                order(purchase, k == 0 ? Long.valueOf(own) : Long.valueOf(vendor), state.name(), "1000.50");
            }
            PurchaseRow row = purchases.find(COMPANY, purchase).orElseThrow();
            assertThat(row.status()).as("compra %s con %s", purchase, mine).isEqualTo(PurchaseStatus.derive(mine));
            long active = mine.stream().filter(s -> s != OrderStatus.CANCELLED).count();
            assertThat(row.total()).isEqualByComparingTo(new BigDecimal("1000.50").multiply(BigDecimal.valueOf(active)));
            assertThat(row.number()).isEqualTo(String.format("CMP-%06d", purchase));
        }
        // El filtro por estado usa la misma expresión.
        for (PurchaseStatus status : PurchaseStatus.values()) {
            long expected = ids.stream().filter(id -> purchases.find(COMPANY, id).orElseThrow().status() == status).count();
            long found = purchases.search(COMPANY, new PurchaseFilter(null, status.name()),
                    PageableFactory.of(0, 200, null, Set.of(), Sort.by(Sort.Order.desc("createdAt"))))
                    .getContent().stream().filter(row -> ids.contains(row.id())).count();
            assertThat(found).as(status.name()).isEqualTo(expected);
        }
    }

    @Test @Order(4)
    void subOrdersLinksAndIsolation() {
        long purchase = newPurchase(COMPANY);
        long mine = order(purchase, own, "PAID", "50000");
        line(mine, 2);
        long theirs = order(purchase, vendor, "PENDING", "95000");
        long l = line(theirs, 1);
        purchases.setLineOffer(l, offer);
        PurchaseRow row = purchases.find(COMPANY, purchase).orElseThrow();
        assertThat(row.status()).isEqualTo(PurchaseStatus.PARTIALLY_PAID);
        assertThat(row.sellers()).isEqualTo(2);
        assertThat(purchases.subOrders(purchase)).extracting(PurchaseLedger.SubOrderRow::sellerKind)
                .containsExactly("OWN", "EXTERNAL");
        assertThat(purchases.subOrders(purchase).get(0).units()).isEqualTo(2);
        assertThat(purchases.linkOf(COMPANY, theirs)).hasValueSatisfying(link -> {
            assertThat(link.purchaseNumber()).isEqualTo(row.number());
            assertThat(link.sellerKind()).isEqualTo("EXTERNAL");
            assertThat(link.subOrders()).isEqualTo(2);
        });
        assertThat(jdbc.queryForObject("SELECT offer_id FROM product_orders WHERE id_product_order = :id",
                new MapSqlParameterSource("id", l), Long.class)).isEqualTo(offer);

        // Otra empresa no ve la compra ni sus subpedidos.
        assertThat(purchases.find(3, purchase)).isEmpty();
        assertThat(purchases.linkOf(3, theirs)).isEmpty();
        assertThat(purchases.search(3, new PurchaseFilter(null, null), PageableFactory.of(0, 10, null, Set.of(),
                Sort.by(Sort.Order.desc("createdAt")))).getTotalElements()).isZero();
        assertThat(purchases.search(COMPANY, new PurchaseFilter(row.number().toLowerCase(), null), PageableFactory.of(0, 10,
                null, Set.of(), Sort.by(Sort.Order.desc("createdAt")))).getContent()).extracting(PurchaseRow::id)
                .containsExactly(purchase);
    }
}
