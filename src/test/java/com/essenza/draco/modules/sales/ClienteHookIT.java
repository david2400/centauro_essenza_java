package com.essenza.draco.modules.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.essenza.draco.modules.sales.application.output.storefront.ClienteHookOutbox;
import com.essenza.draco.modules.sales.infrastructure.outbound.repositories.storefront.ClienteHookJdbcStore;

/**
 * Outbox {@code cliente_hooks} contra MySQL/MariaDB. Crea sus tablas mínimas: SOLO en una base cuyo nombre termine en
 * {@code _test} (STOREFRONT_IT_DB_URL; usuario y clave como MARKETPLACE_IT_DB_*).
 */
@EnabledIfEnvironmentVariable(named = "STOREFRONT_IT_DB_URL", matches = "jdbc:.*_test(\\?.*)?")
class ClienteHookIT {

    static NamedParameterJdbcTemplate jdbc;
    static ClienteHookJdbcStore store;
    static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");

    static void sql(String statement) {
        jdbc.getJdbcOperations().execute(statement);
    }

    @BeforeAll
    static void setUp() {
        String url = System.getenv("STOREFRONT_IT_DB_URL");
        assertThat(url).matches("jdbc:.*_test(\\?.*)?");
        jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(url, System.getenv("MARKETPLACE_IT_DB_USER"),
                System.getenv().getOrDefault("MARKETPLACE_IT_DB_PASSWORD", "")));
        for (String t : List.of("cliente_hooks", "storefront_access", "orders", "purchases", "carriers")) {
            sql("DROP TABLE IF EXISTS " + t);
        }
        sql("CREATE TABLE purchases (id_purchase BIGINT PRIMARY KEY, company_id BIGINT NOT NULL, purchase_number VARCHAR(20))");
        sql("CREATE TABLE carriers (id_carrier BIGINT PRIMARY KEY, name VARCHAR(255) NOT NULL)");
        sql("""
                CREATE TABLE orders (id_order BIGINT PRIMARY KEY, purchase_id BIGINT, order_number VARCHAR(30), carrier_id BIGINT,
                  tracking_number VARCHAR(80))""");
        sql("""
                CREATE TABLE storefront_access (purchase_id BIGINT PRIMARY KEY, company_id BIGINT NOT NULL, token_hash CHAR(64) NOT NULL,
                  email VARCHAR(160), created_at DATETIME(6) NOT NULL)""");
        // Igual que ClienteHookEntity.
        sql("""
                CREATE TABLE cliente_hooks (id BIGINT AUTO_INCREMENT PRIMARY KEY, company_id BIGINT NOT NULL, event VARCHAR(30) NOT NULL,
                  event_key VARCHAR(80) NOT NULL, purchase_number VARCHAR(20) NOT NULL, order_number VARCHAR(30),
                  tracking_number VARCHAR(80), carrier_name VARCHAR(120), status VARCHAR(10) NOT NULL, attempts INT NOT NULL,
                  next_attempt_at DATETIME(6) NOT NULL, last_error VARCHAR(300), created_at DATETIME(6) NOT NULL, sent_at DATETIME(6),
                  UNIQUE KEY uk_cliente_hooks_event (event_key), KEY ix_cliente_hooks_due (status, next_attempt_at))""");
        sql("INSERT INTO purchases VALUES (1, 5, 'CMP-000001'), (2, 5, 'CMP-000002')");
        sql("INSERT INTO carriers VALUES (3, 'Envía Rápido')");
        sql("INSERT INTO orders VALUES (10, 1, 'ORD-10', 3, 'GUIA-77'), (11, 1, 'ORD-11', NULL, NULL), (20, 2, 'ORD-20', NULL, NULL)");
        // Solo la compra 1 es de la tienda pública; la 2 es de mostrador.
        sql("INSERT INTO storefront_access VALUES (1, 5, REPEAT('a', 64), 'ana@x.co', NOW(6))");
        store = new ClienteHookJdbcStore(jdbc);
    }

    @BeforeEach
    void clean() {
        sql("DELETE FROM cliente_hooks");
    }

    static int count(String where) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM cliente_hooks WHERE " + where, new MapSqlParameterSource(), Integer.class);
    }

    @Test
    void flujoCompleto() {
        store.enqueue(10, "ORDER_PAID", NOW);
        store.enqueue(10, "ORDER_PAID", NOW); // repetido: sin duplicar
        store.enqueue(11, "ORDER_PAID", NOW); // otra orden de la misma compra: su propio aviso
        store.enqueue(20, "ORDER_PAID", NOW); // mostrador: no se avisa
        store.enqueue(99, "ORDER_PAID", NOW); // no existe
        assertThat(count("1=1")).isEqualTo(2);
        assertThat(count("purchase_number = 'CMP-000001' AND company_id = 5 AND status = 'PENDING'")).isEqualTo(2);

        assertThat(store.due(NOW.minusSeconds(1), 10)).isEmpty();
        List<ClienteHookOutbox.Hook> due = store.due(NOW, 10);
        assertThat(due).hasSize(2);
        ClienteHookOutbox.Hook first = due.get(0);
        assertThat(first.eventKey()).isEqualTo("ORDER_PAID:10");
        assertThat(first.nextAttemptAt()).isEqualTo(NOW);
        assertThat(first.orderNumber()).isEqualTo("ORD-10");

        assertThat(store.claim(first, NOW.plusSeconds(120))).isTrue();
        assertThat(store.claim(first, NOW.plusSeconds(120))).isFalse(); // otra instancia
        assertThat(store.due(NOW, 10)).hasSize(1);

        store.markSent(first.id(), NOW);
        assertThat(count("event_key = 'ORDER_PAID:10' AND status = 'SENT' AND attempts = 1 AND sent_at IS NOT NULL")).isEqualTo(1);

        ClienteHookOutbox.Hook second = due.get(1);
        store.markFailed(second.id(), "503", NOW.plusSeconds(60));
        assertThat(count("event_key = 'ORDER_PAID:11' AND status = 'PENDING' AND attempts = 1 AND last_error = '503'")).isEqualTo(1);
        assertThat(store.due(NOW.plusSeconds(60), 10)).hasSize(1);
        store.markFailed(second.id(), "503", null);
        assertThat(count("event_key = 'ORDER_PAID:11' AND status = 'FAILED' AND attempts = 2")).isEqualTo(1);
        assertThat(store.due(NOW.plusSeconds(3600), 10)).isEmpty();
    }

    @Test
    void despachoLlevaGuiaYTransportadora() {
        store.enqueue(10, "ORDER_SHIPPED", NOW);
        store.enqueue(10, "ORDER_SHIPPED", NOW);
        assertThat(count("event_key = 'ORDER_SHIPPED:10' AND order_number = 'ORD-10' AND tracking_number = 'GUIA-77'"
                + " AND carrier_name = 'Envía Rápido'")).isEqualTo(1);
        ClienteHookOutbox.Hook hook = store.due(NOW, 50).stream().filter(h -> h.eventKey().equals("ORDER_SHIPPED:10")).findFirst().orElseThrow();
        assertThat(hook.trackingNumber()).isEqualTo("GUIA-77");
        assertThat(hook.carrierName()).isEqualTo("Envía Rápido");
    }
}
