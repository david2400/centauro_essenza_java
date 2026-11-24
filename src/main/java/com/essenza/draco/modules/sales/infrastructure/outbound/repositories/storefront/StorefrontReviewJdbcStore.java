package com.essenza.draco.modules.sales.infrastructure.outbound.repositories.storefront;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReviewStore;

/** product_reviews con JDBC: lecturas de lo aprobado y alta de reseñas pendientes (la moderación es del panel). */
@Repository
public class StorefrontReviewJdbcStore implements StorefrontReviewStore {

    private static final String APPROVED = "deleted = 0 AND is_approved = 1";

    private final NamedParameterJdbcTemplate jdbc;

    public StorefrontReviewJdbcStore(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public Map<Long, Summary> summaries(Collection<Long> productIds) {
        Map<Long, Summary> result = new LinkedHashMap<>();
        if (productIds.isEmpty()) {
            return result;
        }
        jdbc.query("SELECT id_product, AVG(rating), COUNT(*) FROM product_reviews WHERE id_product IN (:ids) AND " + APPROVED
                        + " GROUP BY id_product", new MapSqlParameterSource("ids", productIds), rs -> {
            BigDecimal avg = rs.getBigDecimal(2);
            result.put(rs.getLong(1), new Summary(rs.getLong(1), avg == null ? null : avg.setScale(1, RoundingMode.HALF_UP),
                    rs.getLong(3)));
        });
        return result;
    }

    @Override
    public Map<Integer, Long> distribution(long productId) {
        Map<Integer, Long> result = new LinkedHashMap<>();
        jdbc.query("SELECT rating, COUNT(*) FROM product_reviews WHERE id_product = :id AND " + APPROVED + " GROUP BY rating",
                new MapSqlParameterSource("id", productId), rs -> {
                    result.put(rs.getInt(1), rs.getLong(2));
                });
        return result;
    }

    @Override
    public List<ReviewRow> approved(long productId, int offset, int limit) {
        return jdbc.query("""
                SELECT id_review, customer_name, rating, title, comment, COALESCE(review_date, created_at), is_verified_purchase,
                       store_response, store_response_at
                  FROM product_reviews WHERE id_product = :id AND """ + " " + APPROVED + " " + """
                 ORDER BY COALESCE(review_date, created_at) DESC, id_review DESC LIMIT :limit OFFSET :offset""",
                new MapSqlParameterSource("id", productId).addValue("limit", limit).addValue("offset", offset),
                (rs, i) -> {
                    Timestamp date = rs.getTimestamp(6);
                    Timestamp responded = rs.getTimestamp(9);
                    return new ReviewRow(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5),
                            date == null ? null : date.toLocalDateTime(), rs.getBoolean(7), rs.getString(8),
                            responded == null ? null : responded.toLocalDateTime());
                });
    }

    @Override
    public Map<Long, String> purchasedProducts(long purchaseId) {
        Map<Long, String> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT po.product_id, MIN(po.product_name) FROM product_orders po JOIN orders o ON o.id_order = po.order_id
                 WHERE o.purchase_id = :purchase AND po.deleted = 0 AND o.deleted = 0
                 GROUP BY po.product_id ORDER BY MIN(po.id_product_order)""", new MapSqlParameterSource("purchase", purchaseId), rs -> {
            result.put(rs.getLong(1), rs.getString(2));
        });
        return result;
    }

    @Override
    public Optional<Buyer> buyer(long purchaseId) {
        return jdbc.query("""
                SELECT customer_id, customer_name, customer_email FROM orders
                 WHERE purchase_id = :purchase AND deleted = 0 AND customer_email IS NOT NULL ORDER BY id_order LIMIT 1""",
                new MapSqlParameterSource("purchase", purchaseId),
                (rs, i) -> new Buyer((Long) rs.getObject(1, Long.class), rs.getString(2), rs.getString(3))).stream().findFirst();
    }

    @Override
    public Set<Long> reviewedBy(String email, Collection<Long> productIds) {
        Set<Long> result = new HashSet<>();
        if (email == null || productIds.isEmpty()) {
            return result;
        }
        jdbc.query("SELECT DISTINCT id_product FROM product_reviews WHERE deleted = 0 AND LOWER(customer_email) = :email"
                        + " AND id_product IN (:ids)",
                new MapSqlParameterSource("email", email.toLowerCase(Locale.ROOT)).addValue("ids", productIds),
                rs -> {
                    result.add(rs.getLong(1));
                });
        return result;
    }

    @Override
    public Map<Long, String> reviewStatuses(String email, Collection<Long> productIds) {
        Map<Long, String> result = new LinkedHashMap<>();
        if (email == null || productIds.isEmpty()) {
            return result;
        }
        // La más reciente por producto (normalmente hay una sola).
        jdbc.query("SELECT id_product, is_approved, moderation_status FROM product_reviews WHERE deleted = 0"
                        + " AND LOWER(customer_email) = :email AND id_product IN (:ids) ORDER BY id_review",
                new MapSqlParameterSource("email", email.toLowerCase(Locale.ROOT)).addValue("ids", productIds), rs -> {
                    boolean approved = rs.getBoolean(2);
                    String status = rs.getString(3);
                    result.put(rs.getLong(1), approved ? "APPROVED" : "REJECTED".equalsIgnoreCase(status) ? "REJECTED" : "PENDING");
                });
        return result;
    }

    @Override
    public Map<Long, String> slugs(Collection<Long> productIds) {
        Map<Long, String> result = new LinkedHashMap<>();
        if (productIds.isEmpty()) {
            return result;
        }
        jdbc.query("SELECT id_product, slug FROM products WHERE id_product IN (:ids) AND slug IS NOT NULL",
                new MapSqlParameterSource("ids", productIds), rs -> {
                    result.put(rs.getLong(1), rs.getString(2));
                });
        return result;
    }

    @Override
    public void insert(long productId, Buyer buyer, int rating, String title, String comment, Instant now) {
        LocalDateTime at = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO product_reviews (id_product, id_customer, customer_name, customer_email, rating, title, comment,
                  is_verified_purchase, is_approved, helpful_count, not_helpful_count, moderation_status, moderation_notes,
                  review_date, usr_crea, created_at, deleted)
                VALUES (:product, :customer, :name, :email, :rating, :title, :comment, 1, 0, 0, 0, 'PENDING',
                  'Enviada desde la tienda en línea', :at, 1, :at, 0)""",
                new MapSqlParameterSource("product", productId).addValue("customer", buyer.customerId() == null ? 0L : buyer.customerId())
                        .addValue("name", buyer.name()).addValue("email", buyer.email()).addValue("rating", rating)
                        .addValue("title", title).addValue("comment", comment).addValue("at", at));
    }
}
