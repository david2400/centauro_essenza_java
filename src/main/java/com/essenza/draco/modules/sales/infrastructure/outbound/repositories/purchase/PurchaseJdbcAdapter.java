package com.essenza.draco.modules.sales.infrastructure.outbound.repositories.purchase;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.sales.application.output.repository.PurchaseLedger;

import jakarta.persistence.EntityManager;
import com.essenza.draco.modules.sales.domain.model.purchase.PurchaseNumber;
import com.essenza.draco.modules.sales.domain.model.purchase.PurchaseStatus;

/**
 * Compras en MySQL con JDBC (mismo DataSource y transacción que JPA). El estado y el total se
 * calculan siempre desde las órdenes: la expresión SQL de estado replica {@link PurchaseStatus#derive}.
 */
@Repository
public class PurchaseJdbcAdapter implements PurchaseLedger {

    private static final Map<String, String> SORTS = Map.of("id", "x.id_purchase", "number", "x.purchase_number",
            "createdAt", "x.created_at", "total", "x.amount", "status", "x.derived_status");

    /** Conteos por compra; {@code :company} limita la agregación a las compras de la empresa. */
    private static final String AGGREGATE = """
            SELECT p.*, COALESCE(a.n, 0) AS n, COALESCE(a.cancelled, 0) AS cancelled, COALESCE(a.pending, 0) AS pending,
                   COALESCE(a.shipped, 0) AS shipped, COALESCE(a.delivered, 0) AS delivered,
                   COALESCE(a.amount, 0) AS amount, COALESCE(a.sellers, 0) AS sellers,
                   CASE
                     WHEN COALESCE(a.n, 0) = 0 THEN 'PENDING'
                     WHEN a.n - a.cancelled = 0 THEN 'CANCELLED'
                     WHEN a.pending = a.n - a.cancelled THEN 'PENDING'
                     WHEN a.pending > 0 THEN 'PARTIALLY_PAID'
                     WHEN a.delivered = a.n - a.cancelled THEN 'DELIVERED'
                     WHEN a.shipped > 0 THEN 'IN_DELIVERY'
                     ELSE 'PAID'
                   END AS derived_status
            FROM purchases p
            LEFT JOIN (
              SELECT o.purchase_id, COUNT(*) AS n,
                     SUM(o.state = 'CANCELLED') AS cancelled,
                     SUM(o.state = 'PENDING') AS pending,
                     SUM(o.state IN ('SHIPPED', 'DELIVERED')) AS shipped,
                     SUM(o.state = 'DELIVERED') AS delivered,
                     SUM(CASE WHEN o.state <> 'CANCELLED' THEN o.total ELSE 0 END) AS amount,
                     COUNT(DISTINCT COALESCE(o.seller_id, 0)) AS sellers
              FROM orders o
              WHERE o.purchase_id IN (SELECT id_purchase FROM purchases WHERE company_id = :company)
              GROUP BY o.purchase_id
            ) a ON a.purchase_id = p.id_purchase
            WHERE p.company_id = :company""";

    private final NamedParameterJdbcTemplate jdbc;
    private final EntityManager em;

    public PurchaseJdbcAdapter(NamedParameterJdbcTemplate marketplaceJdbc, EntityManager em) {
        this.jdbc = marketplaceJdbc;
        this.em = em;
    }

    @Override
    public void flush() {
        if (em != null) {
            em.flush();
        }
    }

    @Override
    public long insert(long companyId, NewPurchase purchase, String actorId, String actorName, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO purchases (company_id, channel, currency, customer_name, customer_doc_type, customer_doc_number,
                  customer_email, customer_phone, created_by, created_by_name, created_at)
                VALUES (:company, :channel, :currency, :name, :docType, :docNumber, :email, :phone, :actor, :actorName, :now)""",
                new MapSqlParameterSource("company", companyId).addValue("channel", purchase.channel())
                        .addValue("currency", purchase.currency()).addValue("name", purchase.customerName())
                        .addValue("docType", purchase.customerDocType()).addValue("docNumber", purchase.customerDocNumber())
                        .addValue("email", purchase.customerEmail()).addValue("phone", purchase.customerPhone())
                        .addValue("actor", actorId).addValue("actorName", actorName).addValue("now", utc(now)),
                keys, new String[] {"id_purchase"});
        long id = keys.getKey().longValue();
        jdbc.update("UPDATE purchases SET purchase_number = :number WHERE id_purchase = :id",
                new MapSqlParameterSource("number", PurchaseNumber.of(id)).addValue("id", id));
        return id;
    }

    @Override
    public void linkOrder(long purchaseId, long orderId, Long sellerId) {
        jdbc.update("UPDATE orders SET purchase_id = :purchase, seller_id = :seller WHERE id_order = :order",
                new MapSqlParameterSource("purchase", purchaseId).addValue("seller", sellerId).addValue("order", orderId));
    }

    @Override
    public void setLineOffer(long lineId, long offerId) {
        jdbc.update("UPDATE product_orders SET offer_id = :offer WHERE id_product_order = :line",
                new MapSqlParameterSource("offer", offerId).addValue("line", lineId));
    }

    @Override
    public Optional<PurchaseRow> find(long companyId, long purchaseId) {
        return jdbc.query(AGGREGATE + " AND p.id_purchase = :id",
                new MapSqlParameterSource("company", companyId).addValue("id", purchaseId), (rs, i) -> row(rs))
                .stream().findFirst();
    }

    @Override
    public Page<PurchaseRow> search(long companyId, PurchaseFilter filter, Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource("company", companyId);
        if (filter.q() != null && !filter.q().isBlank()) {
            where.append(" AND (LOWER(COALESCE(x.purchase_number, '')) LIKE :q OR LOWER(COALESCE(x.customer_name, '')) LIKE :q")
                    .append(" OR LOWER(COALESCE(x.customer_doc_number, '')) LIKE :q OR LOWER(COALESCE(x.customer_email, '')) LIKE :q)");
            params.addValue("q", like(filter.q()));
        }
        if (filter.status() != null && !filter.status().isBlank()) {
            where.append(" AND x.derived_status = :status");
            params.addValue("status", filter.status().trim().toUpperCase(Locale.ROOT));
        }
        String from = " FROM (" + AGGREGATE + ") x" + where;
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, params, Long.class);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<PurchaseRow> rows = jdbc.query("SELECT x.*" + from + orderBy(pageable) + " LIMIT :limit OFFSET :offset",
                params, (rs, i) -> row(rs));
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    @Override
    public List<SubOrderRow> subOrders(long purchaseId) {
        return jdbc.query("""
                SELECT o.id_order, o.order_number, o.state, o.total, o.seller_id, o.paid_at,
                       COALESCE(s.trade_name, s.legal_name) AS seller_name, s.kind AS seller_kind,
                       (SELECT COUNT(*) FROM product_orders l WHERE l.order_id = o.id_order) AS items,
                       (SELECT COALESCE(SUM(l.quantity), 0) FROM product_orders l WHERE l.order_id = o.id_order) AS units
                FROM orders o LEFT JOIN mp_sellers s ON s.id = o.seller_id
                WHERE o.purchase_id = :purchase
                ORDER BY CASE WHEN s.kind = 'EXTERNAL' THEN 1 ELSE 0 END, o.id_order""",
                new MapSqlParameterSource("purchase", purchaseId),
                (rs, i) -> new SubOrderRow(rs.getLong("id_order"), rs.getString("order_number"), rs.getString("state"),
                        rs.getBigDecimal("total"), nullableLong(rs, "seller_id"), rs.getString("seller_name"),
                        rs.getString("seller_kind"), rs.getInt("items"), rs.getInt("units"), instant(rs, "paid_at")));
    }

    @Override
    public Optional<OrderLink> linkOf(long companyId, long orderId) {
        return jdbc.query("""
                SELECT p.id_purchase, p.purchase_number, o.seller_id, COALESCE(s.trade_name, s.legal_name) AS seller_name,
                       s.kind AS seller_kind, (SELECT COUNT(*) FROM orders x WHERE x.purchase_id = p.id_purchase) AS sub_orders
                FROM orders o
                JOIN purchases p ON p.id_purchase = o.purchase_id AND p.company_id = :company
                LEFT JOIN mp_sellers s ON s.id = o.seller_id
                WHERE o.id_order = :order""",
                new MapSqlParameterSource("company", companyId).addValue("order", orderId),
                (rs, i) -> new OrderLink(rs.getLong("id_purchase"), rs.getString("purchase_number"),
                        nullableLong(rs, "seller_id"), rs.getString("seller_name"), rs.getString("seller_kind"),
                        rs.getInt("sub_orders"))).stream().findFirst();
    }

    private static PurchaseRow row(ResultSet rs) throws SQLException {
        PurchaseStatus.Counts counts = new PurchaseStatus.Counts(rs.getInt("n"), rs.getInt("cancelled"), rs.getInt("pending"),
                rs.getInt("shipped"), rs.getInt("delivered"));
        BigDecimal amount = rs.getBigDecimal("amount");
        return new PurchaseRow(rs.getLong("id_purchase"), rs.getString("purchase_number"), rs.getString("channel"),
                rs.getString("currency"), rs.getString("customer_name"), rs.getString("customer_doc_type"),
                rs.getString("customer_doc_number"), rs.getString("customer_email"), rs.getString("customer_phone"), counts,
                amount == null ? BigDecimal.ZERO.setScale(2) : amount.setScale(2, java.math.RoundingMode.HALF_UP),
                rs.getInt("sellers"), rs.getString("created_by"), rs.getString("created_by_name"), instant(rs, "created_at"));
    }

    private static String orderBy(Pageable pageable) {
        StringJoiner joiner = new StringJoiner(", ");
        for (Sort.Order order : pageable.getSort()) {
            String column = SORTS.get(order.getProperty());
            if (column != null) {
                joiner.add(column + (order.isAscending() ? " ASC" : " DESC"));
            }
        }
        return " ORDER BY " + (joiner.length() == 0 ? "" : joiner + ", ") + "x.id_purchase DESC";
    }

    private static LocalDateTime utc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String like(String q) {
        String value = q.trim().toLowerCase(Locale.ROOT);
        return "%" + value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
