package com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc;

import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.date;
import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.instant;
import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.utc;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
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

import com.essenza.draco.modules.marketplace.application.port.CommissionStore;
import com.essenza.draco.modules.marketplace.domain.billing.CommissionRules.Status;
import com.essenza.draco.modules.marketplace.domain.plan.Money;

/** Comisiones en MySQL. Las consultas de pantalla filtran por company_id. */
@Repository
public class CommissionJdbcStore implements CommissionStore {

    private static final Map<String, String> SORTS = Map.of("id", "c.id", "paidOn", "c.paid_on", "accrueOn", "c.accrue_on",
            "amount", "c.amount", "status", "c.status", "sellerName", "seller_name", "createdAt", "c.created_at");

    private static final String SELECT = """
            SELECT c.*, COALESCE(s.trade_name, s.legal_name) AS seller_name
            FROM mp_commissions c JOIN mp_sellers s ON s.id = c.seller_id""";

    private final NamedParameterJdbcTemplate jdbc;

    public CommissionJdbcStore(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public Optional<SellerRef> seller(long sellerId) {
        return jdbc.query("SELECT id, company_id, COALESCE(trade_name, legal_name) AS name, kind, status FROM mp_sellers WHERE id = :id",
                new MapSqlParameterSource("id", sellerId),
                (rs, i) -> new SellerRef(rs.getLong("id"), rs.getLong("company_id"), rs.getString("name"), rs.getString("kind"),
                        rs.getString("status"))).stream().findFirst();
    }

    @Override
    public Optional<CommissionRow> byOrder(long orderId, boolean forUpdate) {
        return jdbc.query(SELECT + " WHERE c.order_id = :order" + (forUpdate ? " FOR UPDATE" : ""),
                new MapSqlParameterSource("order", orderId), (rs, i) -> row(rs)).stream().findFirst();
    }

    @Override
    public Optional<CommissionRow> find(long companyId, long commissionId, boolean forUpdate) {
        return jdbc.query(SELECT + " WHERE c.id = :id AND c.company_id = :company" + (forUpdate ? " FOR UPDATE" : ""),
                new MapSqlParameterSource("id", commissionId).addValue("company", companyId), (rs, i) -> row(rs))
                .stream().findFirst();
    }

    @Override
    public Optional<CommissionRow> lockById(long commissionId) {
        return jdbc.query(SELECT + " WHERE c.id = :id FOR UPDATE", new MapSqlParameterSource("id", commissionId),
                (rs, i) -> row(rs)).stream().findFirst();
    }

    @Override
    public long insert(CommissionRow c, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO mp_commissions (company_id, seller_id, order_id, purchase_id, order_number, assignment_id, plan_code,
                  currency, items_subtotal, discount, shipping, taxes, base, percent, percent_part, fixed_part, amount,
                  reversed_amount, accrual_event, return_window_days, paid_on, delivered_on, accrue_on, status, review_reason,
                  accrued_at, voided_at, created_at, updated_at)
                VALUES (:company, :seller, :order, :purchase, :orderNumber, :assignment, :planCode, :currency, :items, :discount,
                  :shipping, :taxes, :base, :percent, :percentPart, :fixedPart, :amount, :reversed, :accrual, :window, :paidOn,
                  :deliveredOn, :accrueOn, :status, :review, :accruedAt, :voidedAt, :now, :now)""",
                params(c).addValue("now", utc(now)), keys, new String[] {"id"});
        return keys.getKey().longValue();
    }

    @Override
    public void update(CommissionRow c, Instant now) {
        jdbc.update("""
                UPDATE mp_commissions SET delivered_on = :deliveredOn, accrue_on = :accrueOn, status = :status,
                  reversed_amount = :reversed, accrued_at = :accruedAt, voided_at = :voidedAt, updated_at = :now
                WHERE id = :id""", params(c).addValue("id", c.id()).addValue("now", utc(now)));
    }

    @Override
    public void recalculated(CommissionRow c, Instant now) {
        jdbc.update("""
                UPDATE mp_commissions SET assignment_id = :assignment, plan_code = :planCode, base = :base, percent = :percent,
                  percent_part = :percentPart, fixed_part = :fixedPart, amount = :amount, accrual_event = :accrual,
                  return_window_days = :window, accrue_on = :accrueOn, status = :status, review_reason = :review,
                  updated_at = :now
                WHERE id = :id AND status = 'REVIEW'""", params(c).addValue("id", c.id()).addValue("now", utc(now)));
    }

    @Override
    public List<Long> dueIds(LocalDate today, int limit) {
        return jdbc.queryForList("""
                SELECT id FROM mp_commissions WHERE status = 'PENDING' AND accrue_on IS NOT NULL AND accrue_on <= :today
                ORDER BY accrue_on, id LIMIT :limit""",
                new MapSqlParameterSource("today", today).addValue("limit", limit), Long.class);
    }

    @Override
    public Page<CommissionRow> search(long companyId, CommissionFilter filter, Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE c.company_id = :company");
        MapSqlParameterSource params = new MapSqlParameterSource("company", companyId);
        if (filter.q() != null && !filter.q().isBlank()) {
            where.append(" AND (LOWER(COALESCE(c.order_number, '')) LIKE :q OR LOWER(s.legal_name) LIKE :q")
                    .append(" OR LOWER(COALESCE(s.trade_name, '')) LIKE :q)");
            params.addValue("q", JdbcSupport.like(filter.q()));
        }
        if (filter.sellerId() != null) {
            where.append(" AND c.seller_id = :seller");
            params.addValue("seller", filter.sellerId());
        }
        if (filter.status() != null && !filter.status().isBlank()) {
            where.append(" AND c.status = :status");
            params.addValue("status", filter.status().trim().toUpperCase());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM mp_commissions c JOIN mp_sellers s ON s.id = c.seller_id" + where,
                params, Long.class);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<CommissionRow> rows = jdbc.query(SELECT + where + JdbcSupport.orderBy(pageable, SORTS, "c.id DESC")
                + " LIMIT :limit OFFSET :offset", params, (rs, i) -> row(rs));
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    @Override
    public Map<String, Totals> totalsByStatus(long companyId, Long sellerId) {
        Map<String, Totals> result = new LinkedHashMap<>();
        MapSqlParameterSource params = new MapSqlParameterSource("company", companyId);
        String sellerClause = "";
        if (sellerId != null) {
            sellerClause = " AND seller_id = :seller";
            params.addValue("seller", sellerId);
        }
        jdbc.query("SELECT status, COUNT(*) n, COALESCE(SUM(amount - reversed_amount), 0) net FROM mp_commissions"
                + " WHERE company_id = :company" + sellerClause + " GROUP BY status", params,
                rs -> {
                    result.put(rs.getString("status"), new Totals(rs.getLong("n"), rs.getBigDecimal("net")));
                });
        return result;
    }

    @Override
    public boolean insertAdjustment(long companyId, long commissionId, String kind, String sourceKey, BigDecimal refundedBase,
                                    BigDecimal amount, boolean posted, String note, Instant now) {
        try {
            jdbc.update("""
                    INSERT INTO mp_commission_adjustments (company_id, commission_id, kind, source_key, refunded_base, amount,
                      posted, note, created_at)
                    VALUES (:company, :commission, :kind, :source, :base, :amount, :posted, :note, :now)""",
                    new MapSqlParameterSource("company", companyId).addValue("commission", commissionId).addValue("kind", kind)
                            .addValue("source", sourceKey).addValue("base", refundedBase).addValue("amount", amount)
                            .addValue("posted", posted ? 1 : 0).addValue("note", note).addValue("now", utc(now)));
            return true;
        } catch (DataIntegrityViolationException ex) {
            if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains("uk_mp_commission_adjustments_source")) {
                return false;
            }
            throw ex;
        }
    }

    private static MapSqlParameterSource params(CommissionRow c) {
        return new MapSqlParameterSource("company", c.companyId()).addValue("seller", c.sellerId())
                .addValue("order", c.orderId()).addValue("purchase", c.purchaseId()).addValue("orderNumber", c.orderNumber())
                .addValue("assignment", c.assignmentId()).addValue("planCode", c.planCode()).addValue("currency", c.currency())
                .addValue("items", c.itemsSubtotal()).addValue("discount", c.discount()).addValue("shipping", c.shipping())
                .addValue("taxes", c.taxes()).addValue("base", c.base()).addValue("percent", c.percent())
                .addValue("percentPart", c.percentPart()).addValue("fixedPart", c.fixedPart()).addValue("amount", c.amount())
                .addValue("reversed", c.reversedAmount()).addValue("accrual", c.accrualEvent())
                .addValue("window", c.returnWindowDays()).addValue("paidOn", c.paidOn()).addValue("deliveredOn", c.deliveredOn())
                .addValue("accrueOn", c.accrueOn()).addValue("status", c.status().name()).addValue("review", c.reviewReason())
                .addValue("accruedAt", utc(c.accruedAt())).addValue("voidedAt", utc(c.voidedAt()));
    }

    private static CommissionRow row(ResultSet rs) throws SQLException {
        String currency = rs.getString("currency");
        long purchase = rs.getLong("purchase_id");
        Long purchaseId = rs.wasNull() ? null : purchase;
        long assignment = rs.getLong("assignment_id");
        Long assignmentId = rs.wasNull() ? null : assignment;
        return new CommissionRow(rs.getLong("id"), rs.getLong("company_id"), rs.getLong("seller_id"), rs.getString("seller_name"),
                rs.getLong("order_id"), purchaseId, rs.getString("order_number"), assignmentId, rs.getString("plan_code"),
                currency, money(rs, "items_subtotal", currency), money(rs, "discount", currency), money(rs, "shipping", currency),
                money(rs, "taxes", currency), money(rs, "base", currency), rs.getBigDecimal("percent").stripTrailingZeros(),
                money(rs, "percent_part", currency), money(rs, "fixed_part", currency), money(rs, "amount", currency),
                money(rs, "reversed_amount", currency), rs.getString("accrual_event"), rs.getInt("return_window_days"),
                date(rs, "paid_on"), date(rs, "delivered_on"), date(rs, "accrue_on"), Status.valueOf(rs.getString("status")),
                rs.getString("review_reason"), instant(rs, "accrued_at"), instant(rs, "voided_at"), instant(rs, "created_at"));
    }

    private static BigDecimal money(ResultSet rs, String column, String currency) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : Money.round(value, currency);
    }
}
