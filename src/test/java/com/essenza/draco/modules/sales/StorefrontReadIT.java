package com.essenza.draco.modules.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.essenza.draco.modules.sales.domain.model.storefront.StorefrontRules;
import com.essenza.draco.modules.sales.domain.model.storefront.ShippingRates;
import com.essenza.draco.modules.sales.infrastructure.outbound.repositories.storefront.ShippingRateJdbcStore;
import com.essenza.draco.modules.sales.infrastructure.outbound.repositories.storefront.StorefrontJdbcAdapter;

/**
 * Lecturas de la tienda pública contra MySQL/MariaDB. Crea sus propias tablas mínimas: SOLO en una base dedicada
 * cuyo nombre termine en {@code _test} (variable STOREFRONT_IT_DB_URL; usuario y clave como MARKETPLACE_IT_DB_*).
 */
@EnabledIfEnvironmentVariable(named = "STOREFRONT_IT_DB_URL", matches = "jdbc:.*_test(\\?.*)?")
class StorefrontReadIT {

    static final long COMPANY = 5;
    static NamedParameterJdbcTemplate jdbc;
    static StorefrontJdbcAdapter store;

    static void sql(String statement) {
        jdbc.getJdbcOperations().execute(statement);
    }

    @BeforeAll
    static void setUp() {
        String url = System.getenv("STOREFRONT_IT_DB_URL");
        assertThat(url).matches("jdbc:.*_test(\\?.*)?");
        jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(url, System.getenv("MARKETPLACE_IT_DB_USER"),
                System.getenv().getOrDefault("MARKETPLACE_IT_DB_PASSWORD", "")));
        for (String t : List.of("storefront_shipping_rates", "storefront_access", "payments", "product_orders", "payment_types", "mp_offers", "mp_sellers",
                "stock_items", "warehouses", "product_images", "product_skus", "products", "categories", "brands")) {
            sql("DROP TABLE IF EXISTS " + t);
        }
        sql("CREATE TABLE categories (id_category BIGINT PRIMARY KEY, name VARCHAR(80), slug VARCHAR(80), deleted TINYINT(1) DEFAULT 0)");
        sql("CREATE TABLE brands (id_brand BIGINT PRIMARY KEY, name VARCHAR(80))");
        sql("""
                CREATE TABLE products (id_product BIGINT PRIMARY KEY, name VARCHAR(150), description VARCHAR(500), slug VARCHAR(160),
                  status VARCHAR(20), image_url VARCHAR(255), category_id BIGINT, brand_id BIGINT, deleted TINYINT(1) DEFAULT 0)""");
        sql("""
                CREATE TABLE product_skus (id_sku BIGINT PRIMARY KEY, product_id BIGINT, code VARCHAR(64), name VARCHAR(150),
                  image_url VARCHAR(255), is_default TINYINT(1), price DECIMAL(15,2), compare_at_price DECIMAL(15,2),
                  active TINYINT(1), deleted TINYINT(1) DEFAULT 0)""");
        sql("CREATE TABLE product_images (id_image BIGINT PRIMARY KEY, product_id BIGINT, url VARCHAR(255), position INT, deleted TINYINT(1) DEFAULT 0)");
        sql("""
                CREATE TABLE warehouses (id_warehouse BIGINT PRIMARY KEY, active TINYINT(1), allow_online TINYINT(1),
                  warehouse_type VARCHAR(15), deleted TINYINT(1) DEFAULT 0)""");
        sql("""
                CREATE TABLE stock_items (id_stock_item BIGINT AUTO_INCREMENT PRIMARY KEY, sku_id BIGINT, warehouse_id BIGINT,
                  on_hand INT, reserved INT, deleted TINYINT(1) DEFAULT 0)""");
        sql("""
                CREATE TABLE mp_sellers (id BIGINT PRIMARY KEY, company_id BIGINT, kind VARCHAR(10), legal_name VARCHAR(200),
                  trade_name VARCHAR(200), status VARCHAR(20))""");
        sql("""
                CREATE TABLE mp_offers (id BIGINT PRIMARY KEY, company_id BIGINT, seller_id BIGINT, sku_id BIGINT, currency CHAR(3),
                  price DECIMAL(19,4), available_quantity INT, handling_days INT, item_condition VARCHAR(20), status VARCHAR(20))""");
        sql("""
                CREATE TABLE payment_types (id_payment_type BIGINT PRIMARY KEY, name VARCHAR(80), provider VARCHAR(20), kind VARCHAR(20),
                  active TINYINT(1), position INT, deleted TINYINT(1) DEFAULT 0)""");
        sql("""
                CREATE TABLE product_orders (id_product_order BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT, product_name VARCHAR(255),
                  sku_code VARCHAR(64), quantity INT, unit_price DECIMAL(15,2), total DECIMAL(15,2), deleted TINYINT(1) DEFAULT 0)""");
        sql("""
                CREATE TABLE payments (id_payment BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT, status VARCHAR(20),
                  checkout_url VARCHAR(500), expires_at DATETIME(6), deleted TINYINT(1) DEFAULT 0)""");
        sql("""
                CREATE TABLE storefront_access (purchase_id BIGINT PRIMARY KEY, company_id BIGINT NOT NULL, token_hash CHAR(64) NOT NULL,
                  email VARCHAR(160), created_at DATETIME(6) NOT NULL, UNIQUE KEY uk_storefront_access_token (token_hash))""");
        sql("""
                CREATE TABLE storefront_shipping_rates (id BIGINT AUTO_INCREMENT PRIMARY KEY, company_id BIGINT NOT NULL,
                  state VARCHAR(80), city VARCHAR(120), state_key VARCHAR(80) NOT NULL DEFAULT '', city_key VARCHAR(120) NOT NULL DEFAULT '',
                  amount DECIMAL(15,2) NOT NULL, free_from DECIMAL(15,2), min_days INT, max_days INT, active TINYINT(1) NOT NULL DEFAULT 1,
                  updated_by VARCHAR(64), updated_by_name VARCHAR(120), updated_at DATETIME(6) NOT NULL,
                  UNIQUE KEY uk_storefront_shipping_rates_place (company_id, state_key, city_key))""");
        store = new StorefrontJdbcAdapter(jdbc);

        sql("INSERT INTO categories VALUES (1, 'Ropa', 'ropa', 0), (2, 'Hogar', 'hogar', 0)");
        sql("INSERT INTO brands VALUES (1, 'Andes')");
        // 10: stock propio; 20: solo oferta externa; 30: sin nada que vender; 40: borrador; 50: oferta de otra empresa
        sql("""
                INSERT INTO products VALUES (10, 'Camisa de lino', 'Fresca 100% lino', 'camisa-lino', 'ACTIVE', NULL, 1, 1, 0),
                  (20, 'Mesa auxiliar', 'Madera', 'mesa-auxiliar', 'ACTIVE', NULL, 2, 1, 0),
                  (30, 'Lámpara', NULL, 'lampara', 'ACTIVE', NULL, 2, 1, 0),
                  (40, 'Borrador', NULL, 'borrador', 'DRAFT', NULL, 1, 1, 0),
                  (50, 'Silla', NULL, 'silla', 'ACTIVE', NULL, 2, 1, 0)""");
        sql("""
                INSERT INTO product_skus VALUES (11, 10, 'CAM-M', 'Talla M', NULL, 1, 120000, 150000, 1, 0),
                  (12, 10, 'CAM-L', 'Talla L', NULL, 0, 120000, NULL, 1, 0),
                  (21, 20, 'MES-1', 'Única', NULL, 1, 300000, NULL, 1, 0),
                  (31, 30, 'LAM-1', 'Única', NULL, 1, 90000, NULL, 1, 0),
                  (41, 40, 'BOR-1', 'Única', NULL, 1, 10000, NULL, 1, 0),
                  (51, 50, 'SIL-1', 'Única', NULL, 1, 80000, NULL, 1, 0)""");
        sql("INSERT INTO product_images VALUES (1, 10, 'https://img/b.jpg', 2, 0), (2, 10, 'https://img/a.jpg', 1, 0), (3, 10, 'https://img/x.jpg', 0, 1)");
        sql("INSERT INTO warehouses VALUES (1, 1, 1, 'ALMACEN', 0), (2, 1, 0, 'ALMACEN', 0), (3, 1, 1, 'CONSIGNACION', 0)");
        sql("""
                INSERT INTO stock_items (sku_id, warehouse_id, on_hand, reserved) VALUES (11, 1, 8, 3), (11, 2, 50, 0), (12, 3, 9, 0),
                  (31, 2, 4, 0), (41, 1, 5, 0)""");
        sql("""
                INSERT INTO mp_sellers VALUES (1, 5, 'EXTERNAL', 'Moda Andina SAS', 'Moda Andina', 'APPROVED'),
                  (2, 5, 'EXTERNAL', 'Suspendido SAS', NULL, 'SUSPENDED'), (3, 6, 'EXTERNAL', 'Otra', NULL, 'APPROVED')""");
        sql("""
                INSERT INTO mp_offers VALUES (90, 5, 1, 11, 'COP', 99000, 4, 2, 'NEW', 'ACTIVE'),
                  (91, 5, 1, 21, 'COP', 280000, 1, 3, 'USED', 'ACTIVE'),
                  (92, 5, 2, 31, 'COP', 85000, 5, 1, 'NEW', 'ACTIVE'),
                  (93, 6, 3, 51, 'COP', 70000, 5, 1, 'NEW', 'ACTIVE'),
                  (94, 5, 1, 12, 'COP', 110000, 0, 1, 'NEW', 'ACTIVE')""");
        sql("""
                INSERT INTO payment_types VALUES (5, 'Wompi', 'WOMPI', 'GATEWAY', 1, 60, 0), (6, 'Efectivo', NULL, 'CASH', 1, 10, 0),
                  (7, 'Mercado Pago', 'MERCADO_PAGO', 'GATEWAY', 0, 70, 0)""");
    }

    @Test
    void onlyProductsWithSomethingToSellAreVisible() {
        var page = store.search(COMPANY, "COP", null, null, "NEWEST", 0, 10);
        assertThat(page.ids()).containsExactly(20L, 10L);
        assertThat(page.total()).isEqualTo(2);
        assertThat(store.search(COMPANY, "COP", StorefrontRules.likePattern("lino"), null, "NAME", 0, 10).ids()).containsExactly(10L);
        assertThat(store.search(COMPANY, "COP", StorefrontRules.likePattern("MES-1"), null, "NAME", 0, 10).ids()).containsExactly(20L);
        // Los comodines del comprador se buscan literalmente.
        assertThat(store.search(COMPANY, "COP", StorefrontRules.likePattern("100%"), null, "NAME", 0, 10).ids()).containsExactly(10L);
        assertThat(store.search(COMPANY, "COP", StorefrontRules.likePattern("10_"), null, "NAME", 0, 10).ids()).isEmpty();
        assertThat(store.search(COMPANY, "COP", null, 2L, "NAME", 0, 10).ids()).containsExactly(20L);
        assertThat(store.search(6, "COP", null, null, "NAME", 0, 10).ids()).containsExactly(10L, 50L);
        assertThat(store.categories(COMPANY, "COP")).extracting(c -> c.name() + ":" + c.products()).containsExactly("Hogar:1", "Ropa:1");
    }

    @Test
    void skusOffersImagesAndSlug() {
        assertThat(store.productBySlug("camisa-lino")).contains(10L);
        assertThat(store.productBySlug("10")).contains(10L);
        assertThat(store.productBySlug("borrador")).isEmpty();
        var skus = store.skus(List.of(10L));
        assertThat(skus).extracting(s -> s.code() + ":" + s.ownAvailable()).containsExactly("CAM-M:5", "CAM-L:0");
        assertThat(store.offers(COMPANY, "COP", List.of(11L, 12L, 21L, 31L))).extracting(o -> o.offerId() + ":" + o.sellerName())
                .containsExactly("90:Moda Andina", "91:Moda Andina");
        assertThat(store.images(List.of(10L)).get(10L)).containsExactly("https://img/a.jpg", "https://img/b.jpg");
        assertThat(store.products(List.of(10L)).get(10L).category()).isEqualTo("Ropa");
        assertThat(store.gateways()).extracting(g -> g.name()).containsExactly("Wompi");
    }

    @Test
    void accessLinesAndOpenCheckout() {
        String hash = StorefrontRules.hash(StorefrontRules.newToken());
        store.insert(44, COMPANY, hash, "ana@x.co", Instant.parse("2026-11-09T15:00:00Z"));
        assertThat(store.find(hash)).hasValueSatisfying(a -> assertThat(a.purchaseId()).isEqualTo(44));
        assertThat(store.find(StorefrontRules.hash("otro"))).isEmpty();
        sql("INSERT INTO product_orders (order_id, product_name, sku_code, quantity, unit_price, total) VALUES (100, 'Camisa', 'CAM-M', 2, 50000, 100000)");
        assertThat(store.orderLines(List.of(100L))).singleElement().satisfies(l -> assertThat(l.total()).isEqualByComparingTo("100000"));
        jdbc.update("INSERT INTO payments (order_id, status, checkout_url, expires_at) VALUES (100, 'PENDING', 'https://old', :old),"
                        + " (100, 'PENDING', 'https://new', :later)",
                new MapSqlParameterSource("old", "2026-11-09 14:00:00").addValue("later", "2026-11-09 16:00:00"));
        assertThat(store.openCheckout(List.of(100L), Instant.parse("2026-11-09T15:00:00Z"))).contains("https://new");
        assertThat(store.openCheckout(List.of(100L), Instant.parse("2026-11-09T17:00:00Z"))).isEmpty();
        assertThat(new BigDecimal("1")).isNotNull();
    }

    @Test
    void pricesAndShippingRatesByCompany() {
        assertThat(store.ownPrices(List.of(11L, 41L))).containsOnlyKeys(11L); // 41 es de un producto en borrador
        assertThat(store.offerPrices(COMPANY, "COP", List.of(90L, 93L))).containsOnlyKeys(90L); // 93 es de otra empresa

        ShippingRateJdbcStore rates = new ShippingRateJdbcStore(jdbc);
        Instant now = Instant.parse("2026-11-09T15:00:00Z");
        var medellin = ShippingRates.check("Antioquia", "Medellín", new BigDecimal("6000"), new BigDecimal("200000"), 1, 2, true);
        long id = rates.insert(COMPANY, medellin, "u-1", "Ana", now);
        rates.insert(6, ShippingRates.check(null, null, new BigDecimal("9000"), null, null, null, true), "u-9", "Otra", now);
        assertThat(rates.exists(COMPANY, "antioquia", "medellin", null)).isTrue();
        assertThat(rates.exists(COMPANY, "antioquia", "medellin", id)).isFalse();
        assertThat(rates.rates(COMPANY)).singleElement().satisfies(r -> {
            assertThat(r.city()).isEqualTo("Medellín");
            assertThat(r.freeFrom()).isEqualByComparingTo("200000");
            assertThat(r.minDays()).isEqualTo(1);
        });
        rates.update(COMPANY, id, ShippingRates.check("Antioquia", "Medellín", new BigDecimal("7000"), null, null, null, false), "u-1",
                "Ana", now);
        assertThat(rates.rate(COMPANY, id)).hasValueSatisfying(r -> {
            assertThat(r.amount()).isEqualByComparingTo("7000");
            assertThat(r.active()).isFalse();
            assertThat(r.freeFrom()).isNull();
        });
        assertThat(rates.rate(6, id)).isEmpty();
        assertThat(rates.audits(COMPANY).get(id).updatedByName()).isEqualTo("Ana");
    }
}
