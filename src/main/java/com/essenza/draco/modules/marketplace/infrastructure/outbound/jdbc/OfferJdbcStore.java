package com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc;

import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.instant;
import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.utc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.marketplace.application.dto.OfferDtos.OfferEventDto;
import com.essenza.draco.modules.marketplace.application.port.OfferStore;
import com.essenza.draco.modules.marketplace.domain.offer.OfferEnums.Condition;
import com.essenza.draco.modules.marketplace.domain.offer.OfferEnums.Fulfillment;
import com.essenza.draco.modules.marketplace.domain.offer.OfferEnums.Status;
import com.essenza.draco.modules.marketplace.domain.offer.OfferTerms;
import com.essenza.draco.modules.marketplace.domain.plan.Money;

/** Ofertas en MySQL. Toda sentencia filtra por company_id. */
@Repository
public class OfferJdbcStore implements OfferStore {

    private static final Map<String, String> DUPLICATES = Map.of(
            "uk_mp_offers_open", "Este vendedor ya tiene una oferta abierta para ese SKU; edítala o ciérrala primero.");

    private static final Map<String, String> SORTS = Map.of(
            "id", "o.id", "price", "o.price", "status", "o.status", "productName", "o.product_name",
            "sellerName", "seller_name", "availableQuantity", "o.available_quantity", "updatedAt", "o.updated_at",
            "publishedAt", "o.published_at");

    private static final String SELECT = """
            SELECT o.*, COALESCE(s.trade_name, s.legal_name) AS seller_name, s.status AS seller_status
            FROM mp_offers o JOIN mp_sellers s ON s.id = o.seller_id AND s.company_id = o.company_id""";

    private final NamedParameterJdbcTemplate jdbc;

    public OfferJdbcStore(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public long insert(long companyId, NewOffer offer, String actorId, Instant now) {
        MapSqlParameterSource params = terms(offer.terms()).addValue("company", companyId).addValue("seller", offer.sellerId())
                .addValue("sku", offer.skuId()).addValue("product", offer.catalog().productId())
                .addValue("skuCode", offer.catalog().skuCode()).addValue("productName", offer.catalog().productName())
                .addValue("actor", actorId).addValue("now", utc(now));
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update("""
                    INSERT INTO mp_offers (company_id, seller_id, sku_id, product_id, sku_code, product_name, currency, price,
                      available_quantity, handling_days, item_condition, fulfillment, seller_sku, status, status_changed_at,
                      lock_version, created_at, created_by, updated_at, updated_by)
                    VALUES (:company, :seller, :sku, :product, :skuCode, :productName, :currency, :price, :qty, :days,
                      :condition, :fulfillment, :sellerSku, 'DRAFT', :now, 0, :now, :actor, :now, :actor)""",
                    params, keys, new String[] {"id"});
        } catch (DataIntegrityViolationException ex) {
            throw JdbcSupport.duplicate(ex, DUPLICATES);
        }
        return keys.getKey().longValue();
    }

    @Override
    public Optional<OfferRow> find(long companyId, long offerId, boolean forUpdate) {
        return jdbc.query(SELECT + " WHERE o.id = :id AND o.company_id = :company" + (forUpdate ? " FOR UPDATE" : ""),
                new MapSqlParameterSource("id", offerId).addValue("company", companyId), (rs, i) -> row(rs)).stream().findFirst();
    }

    @Override
    public boolean updateTerms(long companyId, long offerId, int lockVersion, OfferTerms t, CatalogCopy catalog,
                               String actorId, Instant now) {
        return jdbc.update("""
                UPDATE mp_offers SET currency = :currency, price = :price, available_quantity = :qty, handling_days = :days,
                  item_condition = :condition, fulfillment = :fulfillment, seller_sku = :sellerSku, product_id = :product,
                  sku_code = :skuCode, product_name = :productName, lock_version = lock_version + 1, updated_at = :now,
                  updated_by = :actor
                WHERE id = :id AND company_id = :company AND lock_version = :lock""",
                terms(t).addValue("product", catalog.productId()).addValue("skuCode", catalog.skuCode())
                        .addValue("productName", catalog.productName()).addValue("now", utc(now)).addValue("actor", actorId)
                        .addValue("id", offerId).addValue("company", companyId).addValue("lock", lockVersion)) == 1;
    }

    @Override
    public boolean updateStatus(long companyId, long offerId, int lockVersion, Status status, String reason,
                                boolean publishing, CatalogCopy catalog, String actorId, Instant now) {
        try {
            return jdbc.update("""
                    UPDATE mp_offers SET status = :status, status_reason = :reason, status_changed_at = :now,
                      published_at = CASE WHEN :publishing = 1 THEN :now ELSE published_at END,
                      product_id = :product, sku_code = :skuCode, product_name = :productName,
                      lock_version = lock_version + 1, updated_at = :now, updated_by = :actor
                    WHERE id = :id AND company_id = :company AND lock_version = :lock""",
                    new MapSqlParameterSource("status", status.name()).addValue("reason", reason).addValue("now", utc(now))
                            .addValue("publishing", publishing ? 1 : 0).addValue("product", catalog.productId())
                            .addValue("skuCode", catalog.skuCode()).addValue("productName", catalog.productName())
                            .addValue("actor", actorId).addValue("id", offerId).addValue("company", companyId)
                            .addValue("lock", lockVersion)) == 1;
        } catch (DataIntegrityViolationException ex) {
            throw JdbcSupport.duplicate(ex, DUPLICATES);
        }
    }

    @Override
    public Page<OfferRow> search(long companyId, OfferFilter filter, Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE o.company_id = :company");
        MapSqlParameterSource params = new MapSqlParameterSource("company", companyId);
        if (filter.q() != null && !filter.q().isBlank()) {
            where.append(" AND (LOWER(COALESCE(o.product_name, '')) LIKE :q OR LOWER(COALESCE(o.sku_code, '')) LIKE :q")
                    .append(" OR LOWER(COALESCE(o.seller_sku, '')) LIKE :q OR LOWER(s.legal_name) LIKE :q")
                    .append(" OR LOWER(COALESCE(s.trade_name, '')) LIKE :q)");
            params.addValue("q", JdbcSupport.like(filter.q()));
        }
        if (filter.status() != null && !filter.status().isBlank()) {
            where.append(" AND o.status = :status");
            params.addValue("status", filter.status().trim().toUpperCase());
        }
        if (filter.sellerId() != null) {
            where.append(" AND o.seller_id = :seller");
            params.addValue("seller", filter.sellerId());
        }
        if (filter.skuId() != null) {
            where.append(" AND o.sku_id = :sku");
            params.addValue("sku", filter.skuId());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM mp_offers o JOIN mp_sellers s ON s.id = o.seller_id"
                + " AND s.company_id = o.company_id" + where, params, Long.class);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<OfferRow> rows = jdbc.query(SELECT + where + JdbcSupport.orderBy(pageable, SORTS, "o.id DESC")
                + " LIMIT :limit OFFSET :offset", params, (rs, i) -> row(rs));
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    @Override
    public Map<String, Long> countByStatus(long companyId, Long sellerId) {
        Map<String, Long> result = new LinkedHashMap<>();
        MapSqlParameterSource params = new MapSqlParameterSource("company", companyId);
        String sellerClause = "";
        if (sellerId != null) {
            sellerClause = " AND seller_id = :seller";
            params.addValue("seller", sellerId);
        }
        jdbc.query("SELECT status, COUNT(*) n FROM mp_offers WHERE company_id = :company" + sellerClause + " GROUP BY status",
                params, rs -> {
                    result.put(rs.getString("status"), rs.getLong("n"));
                });
        return result;
    }

    @Override
    public void addEvent(long companyId, long offerId, String type, Status from, Status to, String reason, String detail,
                         String actorId, String actorName, Instant now) {
        jdbc.update("""
                INSERT INTO mp_offer_events (company_id, offer_id, event_type, from_status, to_status, reason, detail,
                  actor_id, actor_name, created_at)
                VALUES (:company, :offer, :type, :from, :to, :reason, :detail, :actor, :actorName, :now)""",
                new MapSqlParameterSource("company", companyId).addValue("offer", offerId).addValue("type", type)
                        .addValue("from", from == null ? null : from.name()).addValue("to", to == null ? null : to.name())
                        .addValue("reason", reason).addValue("detail", detail).addValue("actor", actorId)
                        .addValue("actorName", actorName).addValue("now", utc(now)));
    }

    @Override
    public List<OfferEventDto> events(long companyId, long offerId, int limit) {
        return jdbc.query("""
                SELECT id, event_type, from_status, to_status, reason, detail, actor_id, actor_name, created_at
                FROM mp_offer_events WHERE company_id = :company AND offer_id = :offer
                ORDER BY created_at DESC, id DESC LIMIT :limit""",
                new MapSqlParameterSource("company", companyId).addValue("offer", offerId).addValue("limit", limit),
                (rs, i) -> new OfferEventDto(rs.getLong("id"), rs.getString("event_type"), rs.getString("from_status"),
                        rs.getString("to_status"), rs.getString("reason"), rs.getString("detail"), rs.getString("actor_id"),
                        rs.getString("actor_name"), instant(rs, "created_at")));
    }

    private static MapSqlParameterSource terms(OfferTerms t) {
        return new MapSqlParameterSource("currency", t.currency()).addValue("price", t.price())
                .addValue("qty", t.availableQuantity()).addValue("days", t.handlingDays())
                .addValue("condition", t.condition().name()).addValue("fulfillment", t.fulfillment().name())
                .addValue("sellerSku", t.sellerSku());
    }

    private static OfferRow row(ResultSet rs) throws SQLException {
        // Se reconstruye sin revalidar reglas de alta (p. ej. PLATFORM futuro): los datos ya pasaron por OfferTerms.
        OfferTerms terms = new OfferTerms(rs.getString("currency"),
                Money.round(rs.getBigDecimal("price"), rs.getString("currency")), rs.getInt("available_quantity"),
                rs.getInt("handling_days"), Condition.valueOf(rs.getString("item_condition")),
                Fulfillment.valueOf(rs.getString("fulfillment")), rs.getString("seller_sku"));
        long product = rs.getLong("product_id");
        Long productId = rs.wasNull() ? null : product;
        return new OfferRow(rs.getLong("id"), rs.getLong("seller_id"), rs.getString("seller_name"), rs.getString("seller_status"),
                rs.getLong("sku_id"), new CatalogCopy(productId, rs.getString("sku_code"),
                rs.getString("product_name")), terms, Status.valueOf(rs.getString("status")), rs.getString("status_reason"),
                instant(rs, "status_changed_at"), instant(rs, "published_at"), rs.getInt("lock_version"),
                instant(rs, "created_at"), rs.getString("created_by"), instant(rs, "updated_at"), rs.getString("updated_by"));
    }
}
