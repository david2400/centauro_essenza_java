package com.essenza.draco.modules.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.essenza.draco.modules.operations.application.dto.AlertDtos.AlertDto;
import com.essenza.draco.modules.operations.application.dto.AlertDtos.AlertsDto;
import com.essenza.draco.modules.operations.application.service.OperationsService;
import com.essenza.draco.modules.operations.domain.AlertCatalog;
import com.essenza.draco.modules.operations.infrastructure.outbound.jdbc.OperationsJdbcReadModel;
import com.essenza.draco.shared.exceptions.ForbiddenException;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Centro de operaciones contra MySQL/MariaDB con el adaptador JDBC real: cada consulta corre sobre
 * el esquema y las alertas respetan permisos y empresa. Solo contra una base *_test.
 */
@EnabledIfEnvironmentVariable(named = "MARKETPLACE_IT_DB_URL", matches = "jdbc:.*_test(\\?.*)?")
class OperationsFlowIT {

    static final long COMPANY = 2;
    static final Set<String> ALL = Set.of("VENTAS.VER", "COMISIONES.VER", "OFERTAS.VER", "VENDEDORES.VER", "CONSIGNACION.VER",
            "FACTURACION.VER", "POSTVENTA.VER", "DISPUTAS.VER",
            "COMPRAS.VER");
    static RequestActor actor = new RequestActor(COMPANY, "u-1", "Operaciones", ALL);
    static NamedParameterJdbcTemplate jdbc;
    static OperationsService operations;
    static long owes, owed, warehouse;

    @BeforeAll
    static void setUp() {
        String url = System.getenv("MARKETPLACE_IT_DB_URL");
        assertThat(url).matches("jdbc:.*_test(\\?.*)?");
        jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(url, System.getenv("MARKETPLACE_IT_DB_USER"),
                System.getenv().getOrDefault("MARKETPLACE_IT_DB_PASSWORD", "")));
        clean();
        for (String t : List.of("mp_seller_ledger", "mp_commission_adjustments", "mp_commissions", "mp_offer_reservations",
                "product_orders", "orders", "purchases", "mp_offer_events", "mp_offers", "mp_plan_assignments", "mp_plan_events",
                "mp_seller_events", "mp_seller_documents", "mp_seller_contacts", "mp_sellers", "mp_plans")) {
            jdbc.update("DELETE FROM " + t, new MapSqlParameterSource());
        }
        operations = new OperationsService(new OperationsJdbcReadModel(jdbc), () -> actor, new AlertCatalog.Thresholds(90, 5, 1),
                Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneId.of("America/Bogota")));
        owes = seller("Debe SAS", "APPROVED");
        owed = seller("Consigna SAS", "IN_REVIEW");
        warehouse = jdbc.queryForObject("SELECT MIN(id_warehouse) FROM warehouses", new MapSqlParameterSource(), Long.class);
        order("ORD-A", LocalDate.of(2026, 11, 5));
        order("ORD-B", LocalDate.of(2026, 11, 9));
        order("ORD-C", null);
        // Debe: comisión vencida de 30.000 con un pago de 10.000 → 20.000 vencidos.
        ledger(owes, "COMMISSION", "30000", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15), "c1");
        ledger(owes, "PAYMENT", "-10000", LocalDate.of(2026, 10, 20), null, "p1");
        // La tienda le debe 50.000 (venta en consignación).
        ledger(owed, "CONSIGNMENT_SALE", "-50000", LocalDate.of(2026, 11, 2), null, "s1");
        jdbc.update("INSERT INTO stock_items (sku_id, product_id, warehouse_id, on_hand, reserved, min_threshold) VALUES (101, 1, :w, 3, 1, 5)",
                new MapSqlParameterSource("w", warehouse));
        jdbc.update("""
                INSERT INTO mp_consignment_stock (company_id, seller_id, sku_id, warehouse_id, on_hand, first_received_at, version)
                VALUES (:c, :s, 101, :w, 4, :at, 0)""", new MapSqlParameterSource("c", COMPANY).addValue("s", owed)
                .addValue("w", warehouse).addValue("at", LocalDateTime.of(2026, 6, 1, 10, 0)));
    }

    @AfterAll
    static void tearDown() {
        clean();
    }

    static void clean() {
        for (String sql : List.of("DELETE FROM stock_items", "DELETE FROM mp_consignment_movements", "DELETE FROM mp_consignment_documents",
                "DELETE FROM mp_consignment_stock")) {
            jdbc.update(sql, new MapSqlParameterSource());
        }
    }

    static long seller(String name, String status) {
        jdbc.update("""
                INSERT INTO mp_sellers (company_id, kind, legal_name, trade_name, doc_type, doc_number, status, created_at,
                  created_by, updated_at, updated_by)
                VALUES (:c, 'EXTERNAL', :n, :n, 'NIT', CONCAT('900', FLOOR(RAND() * 1000000)), :st, NOW(6), 'it', NOW(6), 'it')""",
                new MapSqlParameterSource("c", COMPANY).addValue("n", name).addValue("st", status));
        return jdbc.queryForObject("SELECT MAX(id) FROM mp_sellers", new MapSqlParameterSource(), Long.class);
    }

    static void order(String number, LocalDate shipBy) {
        jdbc.update("""
                INSERT INTO orders (order_number, state, total, stock_managed, paid_at, ship_by, customer_name, channel, created_at)
                VALUES (:n, 'PAID', 100000, 1, NOW(6), :shipBy, 'Ana', 'ONLINE', NOW(6))""",
                new MapSqlParameterSource("n", number).addValue("shipBy", shipBy));
    }

    static void ledger(long seller, String type, String amount, LocalDate date, LocalDate due, String key) {
        jdbc.update("""
                INSERT INTO mp_seller_ledger (company_id, seller_id, entry_type, amount, currency, entry_date, due_on,
                  interest_monthly_pct, idempotency_key, created_by, created_at)
                VALUES (:c, :s, :t, :a, 'COP', :d, :due, 0, :k, 'it', NOW(6))""",
                new MapSqlParameterSource("c", COMPANY).addValue("s", seller).addValue("t", type)
                        .addValue("a", new BigDecimal(amount)).addValue("d", date).addValue("due", due).addValue("k", key));
    }

    static Map<String, AlertDto> byKey(AlertsDto dto) {
        return dto.alerts().stream().collect(Collectors.toMap(AlertDto::key, Function.identity()));
    }

    @Test
    void everyAlertRunsAndCountsWhatNeedsAttention() {
        AlertsDto dto = operations.alerts();
        Map<String, AlertDto> alerts = byKey(dto);
        assertThat(alerts).hasSize(AlertCatalog.ALL.size());
        assertThat(alerts.values()).allMatch(AlertDto::available); // ninguna consulta falla contra el esquema
        assertThat(alerts.get("DISPATCH_OVERDUE").count()).isEqualTo(1);
        assertThat(alerts.get("DISPATCH_OVERDUE").items().get(0).days()).isEqualTo(4);
        assertThat(alerts.get("DISPATCH_DUE_TODAY").count()).isEqualTo(1);
        assertThat(alerts.get("DISPATCH_WITHOUT_DATE").count()).isEqualTo(1);
        assertThat(alerts.get("SELLERS_OVERDUE").amount()).isEqualByComparingTo("20000");
        assertThat(alerts.get("SELLERS_OVERDUE").items().get(0).refId()).isEqualTo(owes);
        assertThat(alerts.get("SELLERS_PAYABLE").amount()).isEqualByComparingTo("50000");
        assertThat(alerts.get("SELLERS_IN_REVIEW").count()).isEqualTo(1);
        assertThat(alerts.get("LOW_STOCK").count()).isEqualTo(1);
        assertThat(alerts.get("CONSIGNMENT_WITHOUT_OFFER").count()).isEqualTo(1);
        assertThat(alerts.get("CONSIGNMENT_AGED").items().get(0).days()).isGreaterThan(90);
        assertThat(dto.critical()).isEqualTo(2); // 1 despacho vencido + 1 vendedor en mora
    }

    @Test
    void alertsFollowPermissionsAndCompany() {
        RequestActor previous = actor;
        try {
            actor = new RequestActor(COMPANY, "u-2", "solo ventas", Set.of("VENTAS.VER"));
            assertThat(byKey(operations.alerts())).containsOnlyKeys("DISPATCH_OVERDUE", "DISPATCH_DUE_TODAY",
                    "DISPATCH_WITHOUT_DATE", "LOW_STOCK");
            actor = new RequestActor(3, "u-9", "otra", ALL);
            Map<String, AlertDto> other = byKey(operations.alerts());
            assertThat(other.get("SELLERS_OVERDUE").count()).isZero();
            assertThat(other.get("CONSIGNMENT_AGED").count()).isZero();
            actor = new RequestActor(COMPANY, "u-3", "sin permisos", Set.of());
            assertThatThrownBy(() -> operations.alerts()).isInstanceOf(ForbiddenException.class);
        } finally {
            actor = previous;
        }
    }
}
