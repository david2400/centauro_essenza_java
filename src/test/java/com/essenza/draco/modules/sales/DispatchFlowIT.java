package com.essenza.draco.modules.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
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

import com.essenza.draco.modules.sales.application.dto.dispatch.DispatchDtos.DispatchRowDto;
import com.essenza.draco.modules.sales.application.dto.dispatch.DispatchDtos.DispatchStatsDto;
import com.essenza.draco.modules.sales.application.services.dispatch.DispatchService;
import com.essenza.draco.modules.sales.infrastructure.outbound.repositories.dispatch.DispatchJdbcAdapter;
import com.essenza.draco.shared.common.web.PageableFactory;
import com.essenza.draco.shared.exceptions.ForbiddenException;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Despachos por subpedido contra MySQL/MariaDB con el adaptador JDBC real. Solo contra una base
 * *_test (borra órdenes, compras y datos del marketplace de prueba).
 */
@EnabledIfEnvironmentVariable(named = "MARKETPLACE_IT_DB_URL", matches = "jdbc:.*_test(\\?.*)?")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DispatchFlowIT {

    static final long COMPANY = 2;
    static final Set<String> MANAGE = Set.of("VENTAS.VER", "VENTAS.GESTIONAR");
    /** Lunes 9 de noviembre de 2026, 10:00 en Bogotá. */
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneId.of("America/Bogota"));
    static RequestActor actor = new RequestActor(COMPANY, "u-1", "demo", MANAGE);
    static NamedParameterJdbcTemplate jdbc;
    static DispatchService dispatch;
    static long vendor;
    static long foreign;
    static long carrier;
    static long ownLate, vendorNew, shipped, delivered, pos, foreignOrder, legacy;

    @BeforeAll
    static void setUp() {
        String url = System.getenv("MARKETPLACE_IT_DB_URL");
        assertThat(url).matches("jdbc:.*_test(\\?.*)?");
        jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(url, System.getenv("MARKETPLACE_IT_DB_USER"),
                System.getenv().getOrDefault("MARKETPLACE_IT_DB_PASSWORD", "")));
        for (String t : List.of("mp_seller_ledger", "mp_commission_adjustments", "mp_commissions", "mp_offer_reservations",
                "product_orders", "orders", "purchases", "mp_offer_events", "mp_offers", "mp_plan_assignments", "mp_plan_events",
                "mp_seller_events", "mp_seller_documents", "mp_seller_contacts", "mp_sellers", "mp_plans")) {
            jdbc.update("DELETE FROM " + t, new MapSqlParameterSource());
        }
        dispatch = new DispatchService(new DispatchJdbcAdapter(jdbc), () -> actor, 1, CLOCK);
        vendor = seller(COMPANY, "Moda Andina");
        foreign = seller(3, "Otra empresa");
        jdbc.update("INSERT INTO carriers (name) VALUES ('Envía IT')", new MapSqlParameterSource());
        carrier = jdbc.queryForObject("SELECT MAX(id_carrier) FROM carriers", new MapSqlParameterSource(), Long.class);
        long offer = offer(vendor, 3);

        ownLate = order("ORD-1", "PAID", null, "2026-11-05T15:00:00Z", null, null, null, null, "ONLINE");
        vendorNew = order("ORD-2", "PAID", vendor, "2026-11-09T14:00:00Z", null, null, null, null, "ONLINE");
        line(vendorNew, offer);
        shipped = order("ORD-3", "SHIPPED", null, "2026-11-04T15:00:00Z", LocalDate.of(2026, 11, 6), "2026-11-05T20:00:00Z",
                null, "GUIA-777", "ONLINE");
        delivered = order("ORD-4", "DELIVERED", vendor, "2026-11-02T15:00:00Z", LocalDate.of(2026, 11, 3),
                "2026-11-04T20:00:00Z", "2026-11-06T20:00:00Z", "GUIA-888", "ONLINE");
        pos = order("ORD-5", "PAID", null, "2026-11-09T14:00:00Z", null, null, null, null, "POS");
        foreignOrder = order("ORD-6", "PAID", foreign, "2026-11-05T15:00:00Z", LocalDate.of(2026, 11, 6), null, null, null, "ONLINE");
        legacy = order("ORD-7", "PROCESSING", null, "2026-11-06T15:00:00Z", null, null, null, null, "BACKOFFICE");
    }

    static long seller(long company, String name) {
        jdbc.update("""
                INSERT INTO mp_sellers (company_id, kind, legal_name, trade_name, doc_type, doc_number, status, created_at,
                  created_by, updated_at, updated_by)
                VALUES (:company, 'EXTERNAL', :name, :name, 'NIT', CONCAT('900', FLOOR(RAND() * 1000000)), 'APPROVED', NOW(6),
                  'it', NOW(6), 'it')""", new MapSqlParameterSource("company", company).addValue("name", name));
        return jdbc.queryForObject("SELECT MAX(id) FROM mp_sellers", new MapSqlParameterSource(), Long.class);
    }

    static long offer(long seller, int handlingDays) {
        jdbc.update("""
                INSERT INTO mp_offers (company_id, seller_id, sku_id, currency, price, available_quantity, handling_days,
                  item_condition, fulfillment, status, lock_version, created_at, created_by, updated_at, updated_by)
                VALUES (:company, :seller, 101, 'COP', 95000, 5, :days, 'NEW', 'SELLER', 'ACTIVE', 0, NOW(6), 'it', NOW(6), 'it')""",
                new MapSqlParameterSource("company", COMPANY).addValue("seller", seller).addValue("days", handlingDays));
        return jdbc.queryForObject("SELECT MAX(id) FROM mp_offers", new MapSqlParameterSource(), Long.class);
    }

    static long order(String number, String state, Long seller, String paidAt, LocalDate shipBy, String shippedAt,
                      String deliveredAt, String tracking, String channel) {
        jdbc.update("""
                INSERT INTO orders (order_number, state, total, stock_managed, seller_id, paid_at, ship_by, shipped_at,
                  delivered_at, carrier_id, tracking_number, customer_name, delivery_method, ship_city, channel, created_at)
                VALUES (:number, :state, 100000, 1, :seller, :paid, :shipBy, :shipped, :delivered, :carrier, :tracking,
                  'Ana Pérez', 'SHIPPING', 'Medellín', :channel, :paid)""",
                new MapSqlParameterSource("number", number).addValue("state", state).addValue("seller", seller)
                        .addValue("paid", utc(paidAt)).addValue("shipBy", shipBy).addValue("shipped", utc(shippedAt))
                        .addValue("delivered", utc(deliveredAt)).addValue("carrier", tracking == null ? null : carrier)
                        .addValue("tracking", tracking).addValue("channel", channel));
        return jdbc.queryForObject("SELECT MAX(id_order) FROM orders", new MapSqlParameterSource(), Long.class);
    }

    static void line(long orderId, long offerId) {
        jdbc.update("INSERT INTO product_orders (order_id, quantity, total, offer_id) VALUES (:o, 1, 95000, :f)",
                new MapSqlParameterSource("o", orderId).addValue("f", offerId));
    }

    static LocalDateTime utc(String at) {
        return at == null ? null : LocalDateTime.ofInstant(Instant.parse(at), ZoneOffset.UTC);
    }

    static LocalDate shipBy(long orderId) {
        return jdbc.queryForObject("SELECT ship_by FROM orders WHERE id_order = :id", new MapSqlParameterSource("id", orderId),
                LocalDate.class);
    }

    static List<DispatchRowDto> search(String view, String seller, String q) {
        return dispatch.search(view, seller, q, PageableFactory.of(0, 50, null, Set.of("shipBy"), Sort.by(Sort.Order.asc("shipBy"))))
                .getContent();
    }

    @Test @Order(1)
    void promiseUsesOfferHandlingDaysOrStoreDefault() {
        assertThat(dispatch.promise(ownLate)).isEqualTo(LocalDate.of(2026, 11, 6)); // jueves + 1 día hábil
        assertThat(dispatch.promise(vendorNew)).isEqualTo(LocalDate.of(2026, 11, 12)); // lunes + 3 (oferta)
        assertThat(shipBy(vendorNew)).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(dispatch.promise(pos)).isNull(); // caja: no se despacha
        assertThat(shipBy(pos)).isNull();
    }

    @Test @Order(2)
    void boardShowsPendingByUrgencyAndHidesOtherCompanies() {
        List<DispatchRowDto> toShip = search(null, null, null);
        assertThat(toShip).extracting(DispatchRowDto::orderNumber).containsExactly("ORD-1", "ORD-2", "ORD-7");
        DispatchRowDto late = toShip.get(0);
        assertThat(late.urgency()).isEqualTo("OVERDUE");
        assertThat(late.daysLate()).isEqualTo(3);
        assertThat(late.thirdParty()).isFalse();
        DispatchRowDto vendorRow = toShip.get(1);
        assertThat(vendorRow.urgency()).isEqualTo("ON_TIME");
        assertThat(vendorRow.thirdParty()).isTrue();
        assertThat(vendorRow.sellerName()).isEqualTo("Moda Andina");
        assertThat(toShip.get(2).urgency()).isEqualTo("NONE"); // sin fecha todavía

        assertThat(search("OVERDUE", null, null)).extracting(DispatchRowDto::orderNumber).containsExactly("ORD-1");
        assertThat(search("TO_SHIP", String.valueOf(vendor), null)).extracting(DispatchRowDto::orderNumber).containsExactly("ORD-2");
        assertThat(search("TO_SHIP", "own", null)).extracting(DispatchRowDto::orderNumber).containsExactly("ORD-1", "ORD-7");
        assertThat(search("IN_TRANSIT", null, null)).extracting(DispatchRowDto::carrierName).containsExactly("Envía IT");
        assertThat(search("ALL", null, "guia-888")).extracting(DispatchRowDto::orderNumber).containsExactly("ORD-4");
        assertThat(search("ALL", null, null)).extracting(DispatchRowDto::orderNumber).doesNotContain("ORD-5", "ORD-6");
        assertThatThrownBy(() -> search("LATER", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> search(null, "1 OR 1=1", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @Order(3)
    void statsAndOnTimeRate() {
        DispatchStatsDto stats = dispatch.stats();
        assertThat(stats.toShip()).isEqualTo(3);
        assertThat(stats.overdue()).isEqualTo(1);
        assertThat(stats.dueToday()).isZero();
        assertThat(stats.withoutShipBy()).isEqualTo(1);
        assertThat(stats.inTransit()).isEqualTo(1);
        assertThat(stats.delivered30()).isEqualTo(1);
        assertThat(stats.shipped30()).isEqualTo(2);
        assertThat(stats.onTime30()).isEqualTo(1);
        assertThat(stats.onTimeRate()).isEqualByComparingTo("50.0");
        assertThat(stats.bySeller()).extracting(l -> l.sellerName() == null ? "own" : l.sellerName())
                .containsExactly("own", "Moda Andina");
        assertThat(stats.bySeller().get(0).toShip()).isEqualTo(2);
        assertThat(stats.bySeller().get(0).overdue()).isEqualTo(1);
    }

    @Test @Order(4)
    void backfillFillsOpenOrdersOnlyOnce() {
        assertThat(dispatch.backfill().updated()).isEqualTo(1);
        assertThat(shipBy(legacy)).isEqualTo(LocalDate.of(2026, 11, 9)); // viernes + 1 día hábil = lunes
        assertThat(dispatch.backfill().updated()).isZero();
        assertThat(shipBy(foreignOrder)).isEqualTo(LocalDate.of(2026, 11, 6)); // otra empresa: no se toca
        assertThat(search("DUE_TODAY", null, null)).extracting(DispatchRowDto::orderNumber).containsExactly("ORD-7");
    }

    @Test @Order(5)
    void permissions() {
        RequestActor previous = actor;
        try {
            actor = new RequestActor(COMPANY, "u-2", "lector", Set.of("VENTAS.VER"));
            assertThat(search(null, null, null)).hasSize(3);
            assertThatThrownBy(() -> dispatch.backfill()).isInstanceOf(ForbiddenException.class);
            actor = new RequestActor(COMPANY, "u-3", "sin permisos", Set.of());
            assertThatThrownBy(() -> dispatch.stats()).isInstanceOf(ForbiddenException.class);
            actor = new RequestActor(3, "u-9", "otra", MANAGE);
            assertThat(search(null, null, null)).extracting(DispatchRowDto::orderNumber).containsExactly("ORD-1", "ORD-6", "ORD-7"); // propias + su vendedor
        } finally {
            actor = previous;
        }
    }
}
