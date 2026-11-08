package com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc;

import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.instant;
import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.like;
import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.orderBy;
import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.utc;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.DisputeEventDto;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.DisputeStatsDto;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.DisputeSummaryDto;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.OrderDisputeDto;
import com.essenza.draco.modules.marketplace.application.port.DisputeStore;

/** Reclamos en MySQL (JDBC, misma transacción que JPA). Todo se filtra por la empresa del token. */
@Repository
public class DisputeJdbcStore implements DisputeStore {

    private static final String SELECT = """
            SELECT d.*, o.order_number, o.total AS order_total, o.state AS order_state, o.customer_name,
                   COALESCE(s.trade_name, s.legal_name) AS seller_name
            FROM mp_disputes d JOIN mp_sellers s ON s.id = d.seller_id LEFT JOIN orders o ON o.id_order = d.order_id
            WHERE d.company_id = :company""";
    private static final Map<String, String> SORTS = Map.of("createdAt", "d.created_at", "respondBy", "d.respond_by");

    private final NamedParameterJdbcTemplate jdbc;

    public DisputeJdbcStore(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public Optional<OrderRef> order(long companyId, long orderId) {
        return jdbc.query("""
                SELECT o.id_order, o.order_number, o.state, o.total, o.seller_id, o.purchase_id, o.customer_name,
                       COALESCE(s.trade_name, s.legal_name) AS seller_name, s.kind
                FROM orders o JOIN mp_sellers s ON s.id = o.seller_id
                WHERE o.id_order = :id AND s.company_id = :company""",
                new MapSqlParameterSource("id", orderId).addValue("company", companyId), (rs, i) -> {
                    long purchase = rs.getLong("purchase_id");
                    Long purchaseId = rs.wasNull() ? null : purchase;
                    return new OrderRef(rs.getLong("id_order"), rs.getString("order_number"), rs.getString("state"),
                            zero(rs.getBigDecimal("total")), rs.getLong("seller_id"), rs.getString("seller_name"),
                            rs.getString("kind"), purchaseId, rs.getString("customer_name"));
                }).stream().findFirst();
    }

    @Override
    public boolean hasOpen(long orderId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM mp_disputes WHERE order_id = :id AND status IN ('OPEN', 'SELLER_RESPONDED')",
                new MapSqlParameterSource("id", orderId), Integer.class);
        return n != null && n > 0;
    }

    @Override
    public long insert(long companyId, OrderRef order, String reason, String description, BigDecimal claimed, LocalDate respondBy,
                       String actorId, String actorName, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO mp_disputes (company_id, order_id, purchase_id, seller_id, reason, description, claimed_amount, status,
                  respond_by, lock_version, created_by, created_by_name, created_at, updated_at)
                VALUES (:company, :order, :purchase, :seller, :reason, :description, :claimed, 'OPEN', :respondBy, 0, :actor,
                  :actorName, :now, :now)""",
                new MapSqlParameterSource("company", companyId).addValue("order", order.id()).addValue("purchase", order.purchaseId())
                        .addValue("seller", order.sellerId()).addValue("reason", reason).addValue("description", description)
                        .addValue("claimed", claimed).addValue("respondBy", respondBy).addValue("actor", actorId)
                        .addValue("actorName", actorName).addValue("now", utc(now)), keys, new String[] {"id"});
        return keys.getKey().longValue();
    }

    @Override
    public void setNumber(long id, String number) {
        jdbc.update("UPDATE mp_disputes SET dispute_number = :n WHERE id = :id", new MapSqlParameterSource("n", number).addValue("id", id));
    }

    @Override
    public Optional<DisputeRow> find(long companyId, long id, boolean forUpdate) {
        return jdbc.query(SELECT + " AND d.id = :id" + (forUpdate ? " FOR UPDATE" : ""),
                new MapSqlParameterSource("company", companyId).addValue("id", id).addValue("today", LocalDate.now()),
                (rs, i) -> new DisputeRow(summary(rs, null), rs.getString("description"), rs.getString("seller_response"),
                        instant(rs, "responded_at"), rs.getBigDecimal("seller_charge"), rs.getString("resolution_note"),
                        longOrNull(rs, "devolution_id"), instant(rs, "resolved_at"), rs.getString("resolved_by"),
                        zero(rs.getBigDecimal("order_total")), rs.getString("order_state"), rs.getInt("lock_version"),
                        rs.getString("created_by_name"))).stream().findFirst();
    }

    @Override
    public boolean respond(long id, int lockVersion, String response, Instant now) {
        return jdbc.update("""
                UPDATE mp_disputes SET status = 'SELLER_RESPONDED', seller_response = :response, responded_at = :now,
                  lock_version = lock_version + 1, updated_at = :now
                WHERE id = :id AND lock_version = :lock AND status = 'OPEN'""",
                new MapSqlParameterSource("response", response).addValue("now", utc(now)).addValue("id", id)
                        .addValue("lock", lockVersion)) == 1;
    }

    @Override
    public boolean resolve(long id, int lockVersion, String status, String resolution, BigDecimal amount, BigDecimal sellerCharge,
                           String note, Long devolutionId, String actorId, Instant now) {
        return jdbc.update("""
                UPDATE mp_disputes SET status = :status, resolution = :resolution, resolution_amount = :amount,
                  seller_charge = :charge, resolution_note = :note, devolution_id = :devolution, resolved_at = :now,
                  resolved_by = :actor, lock_version = lock_version + 1, updated_at = :now
                WHERE id = :id AND lock_version = :lock AND status IN ('OPEN', 'SELLER_RESPONDED')""",
                new MapSqlParameterSource("status", status).addValue("resolution", resolution).addValue("amount", amount)
                        .addValue("charge", sellerCharge).addValue("note", note).addValue("devolution", devolutionId)
                        .addValue("now", utc(now)).addValue("actor", actorId).addValue("id", id).addValue("lock", lockVersion)) == 1;
    }

    @Override
    public boolean cancel(long id, int lockVersion, String reason, String actorId, Instant now) {
        return jdbc.update("""
                UPDATE mp_disputes SET status = 'CANCELLED', resolution_note = :note, resolved_at = :now, resolved_by = :actor,
                  lock_version = lock_version + 1, updated_at = :now
                WHERE id = :id AND lock_version = :lock AND status IN ('OPEN', 'SELLER_RESPONDED')""",
                new MapSqlParameterSource("note", reason).addValue("now", utc(now)).addValue("actor", actorId).addValue("id", id)
                        .addValue("lock", lockVersion)) == 1;
    }

    @Override
    public void addEvent(long disputeId, String action, String from, String to, String note, String actorId, String actorName,
                         Instant now) {
        jdbc.update("""
                INSERT INTO mp_dispute_events (dispute_id, action, from_status, to_status, note, actor_id, actor_name, created_at)
                VALUES (:id, :action, :from, :to, :note, :actor, :actorName, :now)""",
                new MapSqlParameterSource("id", disputeId).addValue("action", action).addValue("from", from).addValue("to", to)
                        .addValue("note", note).addValue("actor", actorId).addValue("actorName", actorName).addValue("now", utc(now)));
    }

    @Override
    public List<DisputeEventDto> events(long disputeId) {
        return jdbc.query("SELECT * FROM mp_dispute_events WHERE dispute_id = :id ORDER BY id",
                new MapSqlParameterSource("id", disputeId),
                (rs, i) -> new DisputeEventDto(rs.getString("action"), rs.getString("from_status"), rs.getString("to_status"),
                        rs.getString("note"), rs.getString("actor_name") != null ? rs.getString("actor_name") : rs.getString("actor_id"),
                        instant(rs, "created_at")));
    }

    @Override
    public Page<DisputeSummaryDto> search(long companyId, String status, Long sellerId, String q, LocalDate today, Pageable pageable) {
        MapSqlParameterSource p = new MapSqlParameterSource("company", companyId).addValue("today", today);
        StringBuilder where = new StringBuilder();
        if ("OVERDUE".equals(status)) {
            where.append(" AND d.status = 'OPEN' AND d.respond_by < :today");
        } else if ("ACTIVE".equals(status)) {
            where.append(" AND d.status IN ('OPEN', 'SELLER_RESPONDED')");
        } else if (status != null) {
            where.append(" AND d.status = :status");
            p.addValue("status", status);
        }
        if (sellerId != null) {
            where.append(" AND d.seller_id = :seller");
            p.addValue("seller", sellerId);
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND (LOWER(COALESCE(d.dispute_number, '')) LIKE :q OR LOWER(COALESCE(o.order_number, '')) LIKE :q")
                    .append(" OR LOWER(COALESCE(o.customer_name, '')) LIKE :q OR LOWER(COALESCE(s.trade_name, s.legal_name)) LIKE :q)");
            p.addValue("q", like(q));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM mp_disputes d JOIN mp_sellers s ON s.id = d.seller_id"
                + " LEFT JOIN orders o ON o.id_order = d.order_id WHERE d.company_id = :company" + where, p, Long.class);
        p.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<DisputeSummaryDto> rows = jdbc.query(SELECT + where + orderBy(pageable, SORTS, "d.id DESC") + " LIMIT :limit OFFSET :offset",
                p, (rs, i) -> summary(rs, today));
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    @Override
    public DisputeStatsDto stats(long companyId, LocalDate today) {
        return jdbc.query("""
                SELECT COALESCE(SUM(status IN ('OPEN', 'SELLER_RESPONDED')), 0) AS open_n,
                       COALESCE(SUM(status = 'OPEN' AND respond_by < :today), 0) AS overdue,
                       COALESCE(SUM(status = 'SELLER_RESPONDED'), 0) AS awaiting,
                       COALESCE(SUM(status = 'RESOLVED_BUYER' AND resolved_at >= :since), 0) AS buyer,
                       COALESCE(SUM(status = 'RESOLVED_SELLER' AND resolved_at >= :since), 0) AS seller,
                       COALESCE(SUM(CASE WHEN status = 'RESOLVED_BUYER' AND resolved_at >= :since THEN seller_charge END), 0) AS charged
                FROM mp_disputes WHERE company_id = :company""",
                new MapSqlParameterSource("company", companyId).addValue("today", today).addValue("since", today.minusDays(30)),
                (rs, i) -> new DisputeStatsDto(rs.getLong("open_n"), rs.getLong("overdue"), rs.getLong("awaiting"), rs.getLong("buyer"),
                        rs.getLong("seller"), zero(rs.getBigDecimal("charged")))).get(0);
    }

    @Override
    public Optional<OrderDisputeDto> latestForOrder(long companyId, long orderId) {
        return jdbc.query("""
                SELECT id, dispute_number, status FROM mp_disputes WHERE company_id = :company AND order_id = :order
                ORDER BY (status IN ('OPEN', 'SELLER_RESPONDED')) DESC, id DESC LIMIT 1""",
                new MapSqlParameterSource("company", companyId).addValue("order", orderId),
                (rs, i) -> new OrderDisputeDto(rs.getLong("id"), rs.getString("dispute_number"), rs.getString("status")))
                .stream().findFirst();
    }

    @Override
    public boolean devolutionOfOrder(long devolutionId, long orderId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM order_devolutions WHERE id_order_devolution = :d AND order_id = :o",
                new MapSqlParameterSource("d", devolutionId).addValue("o", orderId), Integer.class);
        return n != null && n > 0;
    }

    private static DisputeSummaryDto summary(ResultSet rs, LocalDate today) throws SQLException {
        LocalDate respondBy = rs.getObject("respond_by", LocalDate.class);
        LocalDate now = today == null ? LocalDate.now() : today;
        String status = rs.getString("status");
        return new DisputeSummaryDto(rs.getLong("id"), rs.getString("dispute_number"), rs.getLong("order_id"),
                rs.getString("order_number"), longOrNull(rs, "purchase_id"), rs.getLong("seller_id"), rs.getString("seller_name"),
                rs.getString("customer_name"), rs.getString("reason"), status, rs.getBigDecimal("claimed_amount"), respondBy,
                "OPEN".equals(status) && respondBy != null && now.isAfter(respondBy), rs.getString("resolution"),
                rs.getBigDecimal("resolution_amount"), instant(rs, "created_at"));
    }

    private static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
