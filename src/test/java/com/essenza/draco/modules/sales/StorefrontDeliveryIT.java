package com.essenza.draco.modules.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReadStore.DeliveryRow;
import com.essenza.draco.modules.sales.infrastructure.outbound.repositories.storefront.StorefrontJdbcAdapter;

/**
 * Datos de entrega que ve el comprador en su pedido, contra MySQL/MariaDB. Crea sus tablas mínimas: SOLO en una base
 * cuyo nombre termine en {@code _test} (STOREFRONT_IT_DB_URL; usuario y clave como MARKETPLACE_IT_DB_*).
 */
@EnabledIfEnvironmentVariable(named = "STOREFRONT_IT_DB_URL", matches = "jdbc:.*_test(\\?.*)?")
class StorefrontDeliveryIT {

    static StorefrontJdbcAdapter store;

    @BeforeAll
    static void setUp() {
        String url = System.getenv("STOREFRONT_IT_DB_URL");
        assertThat(url).matches("jdbc:.*_test(\\?.*)?");
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(url,
                System.getenv("MARKETPLACE_IT_DB_USER"), System.getenv().getOrDefault("MARKETPLACE_IT_DB_PASSWORD", "")));
        var sql = jdbc.getJdbcOperations();
        for (String t : List.of("orders", "carriers", "stores")) {
            sql.execute("DROP TABLE IF EXISTS " + t);
        }
        sql.execute("CREATE TABLE carriers (id_carrier BIGINT PRIMARY KEY, name VARCHAR(255) NOT NULL)");
        sql.execute("""
                CREATE TABLE stores (id_store BIGINT PRIMARY KEY, name VARCHAR(120) NOT NULL, address VARCHAR(200), city VARCHAR(80),
                  opening_hours VARCHAR(255))""");
        sql.execute("""
                CREATE TABLE orders (id_order BIGINT PRIMARY KEY, purchase_id BIGINT, order_number VARCHAR(30), carrier_id BIGINT,
                  tracking_number VARCHAR(80), delivery_method VARCHAR(12), pickup_store_id BIGINT)""");
        sql.execute("INSERT INTO carriers VALUES (3, 'Envía')");
        sql.execute("INSERT INTO stores VALUES (7, 'Tienda Centro', 'Calle 10 # 5-20', 'Bogotá', 'L-S 9:00-19:00')");
        sql.execute("""
                INSERT INTO orders VALUES (10, 1, 'ORD-10', 3, 'GU-77', 'SHIPPING', NULL), (11, 1, 'ORD-11', NULL, '  ', 'SHIPPING', NULL),
                  (12, 1, 'ORD-12', 3, 'GU-X', 'PICKUP', 7), (13, 1, 'ORD-13', NULL, NULL, NULL, NULL)""");
        store = new StorefrontJdbcAdapter(jdbc);
    }

    @Test
    void envioConGuiaYRetiroConTienda() {
        Map<Long, DeliveryRow> rows = store.deliveries(List.of(10L, 11L, 12L, 13L, 99L));
        assertThat(rows).containsOnlyKeys(10L, 11L, 12L, 13L);
        assertThat(rows.get(10L)).isEqualTo(new DeliveryRow(10, false, "Envía", "GU-77", null, null, null));
        assertThat(rows.get(11L)).isEqualTo(new DeliveryRow(11, false, null, null, null, null, null)); // guía en blanco
        // Retiro: la tienda y su horario; la guía no aplica aunque exista.
        assertThat(rows.get(12L)).isEqualTo(new DeliveryRow(12, true, null, null, "Tienda Centro", "Calle 10 # 5-20, Bogotá",
                "L-S 9:00-19:00"));
        assertThat(rows.get(13L).pickup()).isFalse();
        assertThat(store.deliveries(List.of())).isEmpty();
    }
}
