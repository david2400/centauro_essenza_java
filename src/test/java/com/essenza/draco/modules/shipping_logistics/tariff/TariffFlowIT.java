package com.essenza.draco.modules.shipping_logistics.tariff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.BracketDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.ItemDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.PieceDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.PlaceDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.QuoteRequestDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.RateCardDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.SaveRateCardRequest;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.SaveZoneRateRequest;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.SaveZoneRequest;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.ZoneDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.services.ShippingQuoteService;
import com.essenza.draco.modules.shipping_logistics.tariff.application.services.TariffAdminService;
import com.essenza.draco.modules.shipping_logistics.tariff.infrastructure.outbound.jdbc.TariffJdbcStore;
import com.essenza.draco.shared.common.logistics.ShippingQuotes;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.ForbiddenException;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Tarifario contra MySQL/MariaDB con las tablas que crea JPA. SOLO en una base dedicada cuyo nombre termine en
 * {@code _test} (variable STOREFRONT_IT_DB_URL; usuario y clave como MARKETPLACE_IT_DB_*).
 */
@EnabledIfEnvironmentVariable(named = "STOREFRONT_IT_DB_URL", matches = "jdbc:.*_test(\\?.*)?")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TariffFlowIT {

    static final long COMPANY = 7;
    // Colombia 1; Antioquia 10 (Medellín 100, Envigado 101); Cundinamarca 20 (Bogotá 200); San Andrés 30 (300)
    static NamedParameterJdbcTemplate jdbc;
    static TariffAdminService admin;
    static TariffAdminService otherCompany;
    static TariffAdminService readOnly;
    static ShippingQuoteService quotes;
    static long cardId;
    static long zoneId;

    static void sql(String statement) {
        jdbc.getJdbcOperations().execute(statement);
    }

    static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @BeforeAll
    static void setUp() {
        String url = System.getenv("STOREFRONT_IT_DB_URL");
        assertThat(url).matches("jdbc:.*_test(\\?.*)?");
        DriverManagerDataSource ds = new DriverManagerDataSource(url, System.getenv("MARKETPLACE_IT_DB_USER"),
                System.getenv().getOrDefault("MARKETPLACE_IT_DB_PASSWORD", ""));
        jdbc = new NamedParameterJdbcTemplate(ds);
        for (String t : List.of("shipping_quote_misses", "shipping_rate_brackets", "shipping_rates", "shipping_rate_cards",
                "shipping_zone_places", "shipping_zones", "carriers", "warehouses", "product_skus", "products")) {
            sql("DROP TABLE IF EXISTS " + t);
        }
        // Mismas definiciones que las entidades de solo esquema (persistence/schema).
        sql("""
                CREATE TABLE shipping_rate_cards (id BIGINT AUTO_INCREMENT PRIMARY KEY, company_id bigint not null, carrier_id bigint not null,
                  name varchar(120) not null, volumetric_divisor int default 5000 not null, default_weight_kg decimal(10,3) default 1.000 not null,
                  min_charge decimal(15,2) default 0.00 not null, insurance_pct decimal(5,2) default 0.00 not null,
                  insurance_min decimal(15,2) default 0.00 not null, active tinyint(1) default 1 not null, version bigint default 0 not null,
                  updated_by varchar(64), updated_by_name varchar(120), created_at datetime(6) not null, updated_at datetime(6) not null,
                  CONSTRAINT uk_shipping_rate_cards_carrier UNIQUE (company_id, carrier_id),
                  CONSTRAINT ck_shipping_rate_cards_divisor CHECK (volumetric_divisor between 1000 and 10000))""");
        sql("""
                CREATE TABLE shipping_zones (id BIGINT AUTO_INCREMENT PRIMARY KEY, company_id bigint not null, code varchar(30) not null,
                  name varchar(120) not null, description varchar(300), active tinyint(1) default 1 not null, version bigint default 0 not null,
                  updated_by varchar(64), updated_by_name varchar(120), created_at datetime(6) not null, updated_at datetime(6) not null,
                  CONSTRAINT uk_shipping_zones_code UNIQUE (company_id, code))""");
        sql("""
                CREATE TABLE shipping_zone_places (id BIGINT AUTO_INCREMENT PRIMARY KEY, company_id bigint not null, zone_id bigint not null,
                  country_id bigint not null, state_id bigint, city_id bigint, place_key varchar(64) not null, label varchar(250),
                  CONSTRAINT uk_shipping_zone_places_place UNIQUE (company_id, place_key),
                  CONSTRAINT fk_shipping_zone_places_zone FOREIGN KEY (zone_id) REFERENCES shipping_zones (id))""");
        sql("""
                CREATE TABLE shipping_rates (id BIGINT AUTO_INCREMENT PRIMARY KEY, rate_card_id bigint not null, zone_key varchar(24) not null,
                  min_days int default 1 not null, max_days int default 1 not null, free_from decimal(15,2), active tinyint(1) default 1 not null,
                  CONSTRAINT uk_shipping_rates_zone UNIQUE (rate_card_id, zone_key),
                  CONSTRAINT fk_shipping_rates_card FOREIGN KEY (rate_card_id) REFERENCES shipping_rate_cards (id))""");
        sql("""
                CREATE TABLE shipping_rate_brackets (id BIGINT AUTO_INCREMENT PRIMARY KEY, rate_id bigint not null, min_kg decimal(10,3) not null,
                  max_kg decimal(10,3), price decimal(15,2) not null, per_kg_over decimal(15,2),
                  CONSTRAINT uk_shipping_rate_brackets_min UNIQUE (rate_id, min_kg),
                  CONSTRAINT fk_shipping_rate_brackets_rate FOREIGN KEY (rate_id) REFERENCES shipping_rates (id))""");
        sql("""
                CREATE TABLE shipping_quote_misses (id BIGINT AUTO_INCREMENT PRIMARY KEY, company_id bigint not null, place_key varchar(64) not null,
                  country_id bigint not null, state_id bigint, city_id bigint, zone_key varchar(24), label varchar(250),
                  misses int default 0 not null, last_at datetime(6) not null,
                  CONSTRAINT uk_shipping_quote_misses_place UNIQUE (company_id, place_key))""");
        sql("CREATE TABLE carriers (id_carrier BIGINT PRIMARY KEY, name VARCHAR(255), is_active TINYINT(1), deleted TINYINT(1) DEFAULT 0)");
        sql("""
                CREATE TABLE warehouses (id_warehouse BIGINT PRIMARY KEY, country_id BIGINT, state_id BIGINT, city_id BIGINT,
                  is_main TINYINT(1), active TINYINT(1), fulfillment_priority INT, deleted TINYINT(1) DEFAULT 0)""");
        sql("""
                CREATE TABLE products (id_product BIGINT PRIMARY KEY, weight DOUBLE, length DOUBLE, width DOUBLE, height DOUBLE,
                  deleted TINYINT(1) DEFAULT 0)""");
        sql("""
                CREATE TABLE product_skus (id_sku BIGINT PRIMARY KEY, product_id BIGINT, weight DOUBLE, length DOUBLE, width DOUBLE,
                  height DOUBLE, deleted TINYINT(1) DEFAULT 0)""");
        sql("INSERT INTO carriers VALUES (1, 'Coordinadora', 1, 0), (2, 'Servientrega', 1, 0), (3, 'Borrada', 1, 1)");
        sql("INSERT INTO warehouses VALUES (1, 1, 10, 100, 1, 1, 10, 0), (2, 1, 20, 200, 0, 1, 20, 0)");
        sql("INSERT INTO products VALUES (1, 2.0, 30, 20, 10, 0), (2, NULL, NULL, NULL, NULL, 0)");
        // SKU 11 sin medidas propias: toma las del producto 1; SKU 12 trae 4 kg propios; SKU 21 sin peso en ningún lado.
        sql("INSERT INTO product_skus VALUES (11, 1, NULL, NULL, NULL, NULL, 0), (12, 1, 4.0, NULL, NULL, NULL, 0), (21, 2, NULL, NULL, NULL, NULL, 0)");

        Clock clock = Clock.fixed(LocalDate.of(2026, 11, 13).atTime(15, 0).toInstant(ZoneOffset.UTC), ZoneId.of("America/Bogota"));
        TariffJdbcStore store = new TariffJdbcStore(jdbc);
        quotes = new ShippingQuoteService(store, clock, new DataSourceTransactionManager(ds));
        admin = new TariffAdminService(store, quotes,
                () -> new RequestActor(COMPANY, "u1", "Laura", Set.of("LOGISTICA.GESTIONAR")), clock);
        otherCompany = new TariffAdminService(store, quotes,
                () -> new RequestActor(99, "u9", "Otra", Set.of("LOGISTICA.GESTIONAR")), clock);
        readOnly = new TariffAdminService(store, quotes,
                () -> new RequestActor(COMPANY, "u2", "Ana", Set.of("LOGISTICA.VER")), clock);
    }

    static List<BracketDto> brackets() {
        return List.of(new BracketDto(d("0"), d("3"), d("9000"), null), new BracketDto(d("3"), null, d("14000"), d("1200")));
    }

    @Test
    @Order(1)
    void cardsAndRatesAreManagedWithVersions() {
        assertThatThrownBy(() -> readOnly.saveCard(null, new SaveRateCardRequest(1L, "x", null, null, null, null, null, true, null)))
                .isInstanceOf(ForbiddenException.class);
        RateCardDto card = admin.saveCard(null, new SaveRateCardRequest(1L, "Coordinadora paquetería", 5000, d("1"), d("0"),
                d("1"), d("2000"), true, null));
        cardId = card.id();
        assertThat(card.carrierName()).isEqualTo("Coordinadora");
        assertThat(card.updatedByName()).isEqualTo("Laura");
        assertThatThrownBy(() -> admin.saveCard(null, new SaveRateCardRequest(1L, "Otro", null, null, null, null, null, true, null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("ya tiene tarifario");
        assertThatThrownBy(() -> admin.saveCard(null, new SaveRateCardRequest(3L, "Borrada", null, null, null, null, null, true, null)))
                .hasMessageContaining("no existe");

        RateCardDto withNational = admin.saveRate(cardId, "NATIONAL", new SaveZoneRateRequest(card.version(), 2, 4, d("200000"), true, brackets()));
        RateCardDto withLocal = admin.saveRate(cardId, "local", new SaveZoneRateRequest(withNational.version(), 0, 1, null, true,
                List.of(new BracketDto(d("0"), null, d("6000"), d("500")))));
        assertThat(withLocal.rates()).extracting(r -> r.zoneKey()).containsExactlyInAnyOrder("NATIONAL", "LOCAL");
        // Versión vieja: otra persona ya lo cambió.
        assertThatThrownBy(() -> admin.saveRate(cardId, "REGIONAL", new SaveZoneRateRequest(card.version(), 1, 2, null, true, brackets())))
                .isInstanceOf(ConflictException.class);
        // Reemplazar franjas de una tarifa existente no duplica filas.
        RateCardDto replaced = admin.saveRate(cardId, "NATIONAL", new SaveZoneRateRequest(withLocal.version(), 2, 4, d("200000"), true, brackets()));
        assertThat(replaced.rates().stream().filter(r -> r.zoneKey().equals("NATIONAL")).findFirst().orElseThrow().brackets()).hasSize(2);
        // Otra empresa no lo ve.
        assertThat(otherCompany.cards()).isEmpty();
    }

    @Test
    @Order(2)
    void zonesOwnTheirPlaces() {
        ZoneDto island = admin.saveZone(null, new SaveZoneRequest("especial", "Trayecto especial", null, true, null,
                List.of(new PlaceDto(1L, 30L, null, "San Andrés"), new PlaceDto(1L, 30L, null, "repetido"))));
        zoneId = island.id();
        assertThat(island.code()).isEqualTo("ESPECIAL");
        assertThat(island.places()).hasSize(1);
        assertThatThrownBy(() -> admin.saveZone(null, new SaveZoneRequest("OTRA", "Otra", null, true, null,
                List.of(new PlaceDto(1L, 30L, null, "San Andrés")))))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Trayecto especial");
        RateCardDto card = admin.cards().get(0);
        admin.saveRate(cardId, island.zoneKey(), new SaveZoneRateRequest(card.version(), 5, 8, null, true,
                List.of(new BracketDto(d("0"), null, d("30000"), d("3000")))));
        assertThatThrownBy(() -> admin.deleteZone(zoneId)).isInstanceOf(ConflictException.class);
        assertThat(admin.zones().get(0).ratesCount()).isEqualTo(1);
        assertThat(admin.cards().get(0).rates()).anySatisfy(r -> assertThat(r.zoneName()).isEqualTo("Trayecto especial"));
    }

    @Test
    @Order(3)
    void quotesUseCatalogWeightsOriginAndZones() {
        // Desde la bodega principal (Medellín) a Medellín: local. SKU 11 = 2 kg (del producto) × 2; vol. 30×20×10/5000 = 1,2 × 2.
        var local = admin.simulate(new QuoteRequestDto(null, 1L, 10L, 100L, "Medellín", List.of(new ItemDto(11L, null, 2)), null,
                null, d("50000"), null, null));
        assertThat(local.originWarehouseId()).isEqualTo(1L);
        assertThat(local.zoneKey()).isEqualTo("LOCAL");
        var o = local.options().get(0);
        assertThat(o.chargeableKg()).isEqualByComparingTo("4");
        assertThat(o.freight()).isEqualByComparingTo("8000"); // 6000 + 4 kg × 500
        assertThat(o.deliveryTo()).isEqualTo(LocalDate.of(2026, 11, 16));

        // A Bogotá: no hay regional ni nacional distinto: nacional con el SKU 12 (4 kg) + SKU 21 sin peso (1 kg por defecto).
        var national = admin.simulate(new QuoteRequestDto(null, 1L, 20L, 200L, "Bogotá",
                List.of(new ItemDto(12L, null, 1), new ItemDto(21L, null, 1)), null, d("100000"), d("50000"), null, null));
        var n = national.options().get(0);
        assertThat(national.zoneKey()).isEqualTo("NATIONAL");
        assertThat(n.chargeableKg()).isEqualByComparingTo("5");
        assertThat(n.weightEstimated()).isTrue();
        assertThat(n.freight()).isEqualByComparingTo("16400"); // 14000 + 2 × 1200
        assertThat(n.insurance()).isEqualByComparingTo("2000"); // 1 % = 1000 → mínimo 2000
        assertThat(n.total()).isEqualByComparingTo("18400");

        // Desde la bodega de Bogotá con envío gratis por monto.
        var free = admin.simulate(new QuoteRequestDto(2L, 1L, 10L, 100L, null, null,
                List.of(new PieceDto(d("1"), null, null, null, 1)), null, d("250000"), null, null));
        assertThat(free.options().get(0).free()).isTrue();
        assertThat(free.options().get(0).total()).isEqualByComparingTo("0");

        // San Andrés: zona propia.
        var island = admin.simulate(new QuoteRequestDto(null, 1L, 30L, 300L, "San Andrés", null, null, null, null, null, null));
        assertThat(island.zoneName()).isEqualTo("Trayecto especial");
        assertThat(island.options().get(0).freight()).isEqualByComparingTo("33000"); // 30000 + 1 kg × 3000

        // Interfaz compartida (la usan la orden y la tienda): misma respuesta.
        ShippingQuotes.QuoteResult shared = quotes.quote(COMPANY, new ShippingQuotes.QuoteRequest(null, 1L, 10L, 100L, null,
                List.of(new ShippingQuotes.Item(11L, null, 2)), null, d("50000"), null, null));
        assertThat(shared.options().get(0).total()).isEqualByComparingTo("8000");
    }

    @Test
    @Order(4)
    void uncoveredDestinationsAreListedUntilARateCoversThem() {
        admin.saveZone(zoneId, new SaveZoneRequest("ESPECIAL", "Trayecto especial", null, true, admin.zones().get(0).version(),
                List.of(new PlaceDto(1L, 30L, null, "San Andrés"), new PlaceDto(2L, null, null, "Ecuador"))));
        // Ecuador está en la zona; desactivo la tarifa de la zona para que no haya cobertura.
        RateCardDto card = admin.cards().get(0);
        admin.saveRate(cardId, "Z" + zoneId, new SaveZoneRateRequest(card.version(), 5, 8, null, false,
                List.of(new BracketDto(d("0"), null, d("30000"), null))));
        var none = admin.simulate(new QuoteRequestDto(null, 2L, 40L, 400L, "Quito, Pichincha", null, null, null, null, null, null));
        assertThat(none.covered()).isFalse();
        admin.simulate(new QuoteRequestDto(null, 2L, 40L, 400L, "Quito, Pichincha", null, null, null, null, null, null));
        assertThat(admin.gaps()).singleElement().satisfies(g -> {
            assertThat(g.label()).isEqualTo("Quito, Pichincha");
            assertThat(g.misses()).isEqualTo(2);
        });
        card = admin.cards().get(0);
        admin.saveRate(cardId, "Z" + zoneId, new SaveZoneRateRequest(card.version(), 5, 8, null, true,
                List.of(new BracketDto(d("0"), null, d("30000"), null))));
        assertThat(admin.gaps()).isEmpty();
    }

    @Test
    @Order(5)
    void deletingCleansUp() {
        RateCardDto card = admin.cards().get(0);
        admin.deleteRate(cardId, "Z" + zoneId, card.version());
        admin.deleteZone(zoneId);
        admin.deleteCard(cardId);
        assertThat(admin.cards()).isEmpty();
        assertThat(admin.zones()).isEmpty();
        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT COUNT(*) FROM shipping_rate_brackets", Integer.class)).isZero();
    }
}
