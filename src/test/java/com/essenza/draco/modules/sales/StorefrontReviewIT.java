package com.essenza.draco.modules.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReviewStore;
import com.essenza.draco.modules.sales.infrastructure.outbound.repositories.storefront.StorefrontReviewJdbcStore;

/**
 * Reseñas de la tienda contra MySQL/MariaDB. Crea sus propias tablas mínimas: SOLO en una base dedicada cuyo nombre
 * termine en {@code _test} (variable STOREFRONT_IT_DB_URL).
 */
@EnabledIfEnvironmentVariable(named = "STOREFRONT_IT_DB_URL", matches = "jdbc:.*_test(\\?.*)?")
class StorefrontReviewIT {

    static NamedParameterJdbcTemplate jdbc;
    static StorefrontReviewJdbcStore store;

    static void sql(String statement) {
        jdbc.getJdbcOperations().execute(statement);
    }

    @BeforeAll
    static void setUp() {
        String url = System.getenv("STOREFRONT_IT_DB_URL");
        assertThat(url).matches("jdbc:.*_test(\\?.*)?");
        jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(url, System.getenv("MARKETPLACE_IT_DB_USER"),
                System.getenv().getOrDefault("MARKETPLACE_IT_DB_PASSWORD", "")));
        for (String t : List.of("product_reviews", "rv_product_orders", "rv_orders")) {
            sql("DROP TABLE IF EXISTS " + t);
        }
        sql("""
                CREATE TABLE product_reviews (id_review BIGINT AUTO_INCREMENT PRIMARY KEY, id_product BIGINT NOT NULL,
                  id_customer BIGINT NOT NULL, customer_name VARCHAR(255) NOT NULL, customer_email VARCHAR(255) NOT NULL,
                  rating INT NOT NULL, title VARCHAR(255), comment VARCHAR(2000) NOT NULL, is_verified_purchase BIT NOT NULL,
                  is_approved BIT, is_helpful BIT, helpful_count INT, not_helpful_count INT, moderation_status VARCHAR(50),
                  moderation_notes VARCHAR(1000), review_date DATETIME(6), usr_crea INT NOT NULL, usr_mod INT,
                  created_at DATETIME(6), updated_at DATETIME(6), deleted TINYINT(1) NOT NULL DEFAULT 0,
                  store_response VARCHAR(1000), store_response_at DATETIME(6))""");
        store = new StorefrontReviewJdbcStore(jdbc);
        sql("""
                INSERT INTO product_reviews (id_product, id_customer, customer_name, customer_email, rating, comment,
                  is_verified_purchase, is_approved, review_date, usr_crea) VALUES
                  (3, 0, 'Ana Pérez', 'ana@x.co', 5, 'Muy buena', 1, 1, '2026-11-01 10:00:00', 1),
                  (3, 0, 'Luis Gómez', 'luis@x.co', 4, 'Buena', 1, 1, '2026-11-02 10:00:00', 1),
                  (3, 0, 'Spam', 'spam@x.co', 1, 'Pendiente', 0, 0, '2026-11-03 10:00:00', 1),
                  (7, 0, 'Eva', 'eva@x.co', 3, 'Regular', 1, 1, '2026-11-03 10:00:00', 1)""");
    }

    @Test
    void approvedReviewsSummariesAndInsert() {
        var summaries = store.summaries(List.of(3L, 7L, 99L));
        assertThat(summaries.get(3L).count()).isEqualTo(2); // la pendiente no cuenta
        assertThat(summaries.get(3L).average()).isEqualByComparingTo("4.5");
        assertThat(summaries).doesNotContainKey(99L);
        assertThat(store.distribution(3L)).containsEntry(5, 1L).containsEntry(4, 1L).doesNotContainKey(1);
        assertThat(store.approved(3L, 0, 10)).extracting(StorefrontReviewStore.ReviewRow::customerName)
                .containsExactly("Luis Gómez", "Ana Pérez"); // más reciente primero
        assertThat(store.approved(3L, 1, 10)).hasSize(1);

        assertThat(store.reviewedBy("ANA@x.co", List.of(3L, 7L))).isEqualTo(Set.of(3L));
        assertThat(store.reviewStatuses("spam@x.co", List.of(3L))).containsEntry(3L, "PENDING");
        assertThat(store.reviewStatuses("ana@x.co", List.of(3L))).containsEntry(3L, "APPROVED");
        sql("UPDATE product_reviews SET store_response = 'Gracias, Ana', store_response_at = '2026-11-04 10:00:00' WHERE customer_email = 'ana@x.co'");
        assertThat(store.approved(3L, 0, 10)).filteredOn(r -> r.customerName().equals("Ana Pérez")).singleElement()
                .satisfies(r -> assertThat(r.storeResponse()).isEqualTo("Gracias, Ana"));
        store.insert(7L, new StorefrontReviewStore.Buyer(null, "Ana Pérez", "ana@x.co"), 5, null, "Excelente producto",
                Instant.parse("2026-11-20T10:00:00Z"));
        assertThat(store.reviewedBy("ana@x.co", List.of(7L))).containsExactly(7L);
        assertThat(store.summaries(List.of(7L)).get(7L).count()).isEqualTo(1); // la nueva queda pendiente
        Integer pending = jdbc.getJdbcOperations().queryForObject(
                "SELECT COUNT(*) FROM product_reviews WHERE id_product = 7 AND moderation_status = 'PENDING' AND is_approved = 0",
                Integer.class);
        assertThat(pending).isEqualTo(1);
    }
}
