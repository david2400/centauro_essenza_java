package com.essenza.draco.modules.sales.infrastructure.outbound.repositories.dispatch;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.sales.application.output.repository.DispatchBoard;

/**
 * Tablero de despachos en MySQL (JDBC, misma transacción que JPA). Un subpedido de vendedor
 * externo solo se ve si el vendedor es de la empresa del token; las órdenes propias sin vendedor
 * son de la tienda (una sola empresa). Las ventas de caja (POS) no se despachan.
 */
@Repository
public class DispatchJdbcAdapter implements DispatchBoard {

    private static final String OPEN = "o.state IN ('PAID', 'PROCESSING', 'READY')";
    // Espacio inicial explícito: el bloque de texto quita la sangría común.
    private static final String FROM = " " + """
             FROM orders o
             LEFT JOIN mp_sellers s ON s.id = o.seller_id
             LEFT JOIN purchases p ON p.id_purchase = o.purchase_id
             LEFT JOIN carriers c ON c.id_carrier = o.carrier_id
             WHERE (o.seller_id IS NULL OR s.company_id = :company) AND COALESCE(o.channel, '') <> 'POS'""";

    private final NamedParameterJdbcTemplate jdbc;

    public DispatchJdbcAdapter(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public Optional<PromiseInput> promiseInput(long orderId) {
        return jdbc.query("""
                SELECT o.id_order, o.paid_at, s.kind AS seller_kind,
                       (SELECT MAX(f.handling_days) FROM product_orders l JOIN mp_offers f ON f.id = l.offer_id
                        WHERE l.order_id = o.id_order AND COALESCE(l.deleted, 0) = 0) AS handling_days
                FROM orders o LEFT JOIN mp_sellers s ON s.id = o.seller_id
                WHERE o.id_order = :id AND COALESCE(o.channel, '') <> 'POS'""",
                new MapSqlParameterSource("id", orderId), (rs, i) -> {
                    int days = rs.getInt("handling_days");
                    Integer handling = rs.wasNull() ? null : days;
                    return new PromiseInput(rs.getLong("id_order"), instant(rs, "paid_at"), handling,
                            "EXTERNAL".equals(rs.getString("seller_kind")));
                }).stream().findFirst();
    }

    @Override
    public void setShipBy(long orderId, LocalDate shipBy) {
        jdbc.update("UPDATE orders SET ship_by = :shipBy WHERE id_order = :id",
                new MapSqlParameterSource("id", orderId).addValue("shipBy", shipBy));
    }

    @Override
    public List<Long> openWithoutShipBy(long companyId, int limit) {
        return jdbc.query("SELECT o.id_order" + FROM + " AND " + OPEN + " AND o.ship_by IS NULL AND o.paid_at IS NOT NULL"
                        + " ORDER BY o.id_order LIMIT :limit",
                new MapSqlParameterSource("company", companyId).addValue("limit", limit), (rs, i) -> rs.getLong(1));
    }

    @Override
    public Page<DispatchRow> search(long companyId, Filter filter, LocalDate today, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource("company", companyId).addValue("today", today)
                .addValue("since", today.minusDays(30));
        StringBuilder where = new StringBuilder(" AND ").append(viewCondition(filter.view()));
        String seller = filter.seller();
        if ("own".equalsIgnoreCase(seller)) {
            where.append(" AND (o.seller_id IS NULL OR s.kind = 'OWN')");
        } else if (seller != null && !seller.isBlank()) {
            where.append(" AND o.seller_id = :seller");
            params.addValue("seller", Long.valueOf(seller.trim()));
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            where.append(" AND (LOWER(COALESCE(o.order_number, '')) LIKE :q OR LOWER(COALESCE(o.customer_name, '')) LIKE :q")
                    .append(" OR LOWER(COALESCE(o.tracking_number, '')) LIKE :q OR LOWER(COALESCE(p.purchase_number, '')) LIKE :q")
                    .append(" OR CAST(o.id_order AS CHAR) = :exact)");
            String value = filter.q().trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            params.addValue("q", "%" + value + "%").addValue("exact", filter.q().trim());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + FROM + where, params, Long.class);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<DispatchRow> rows = jdbc.query("""
                SELECT o.id_order, o.order_number, o.state, o.delivery_method, o.purchase_id, p.purchase_number, o.seller_id,
                       COALESCE(s.trade_name, s.legal_name) AS seller_name, s.kind AS seller_kind, o.customer_name, o.ship_city,
                       o.total, o.paid_at, o.ship_by, o.shipped_at, o.delivered_at, o.carrier_id, c.name AS carrier_name,
                       o.tracking_number,
                       EXISTS (SELECT 1 FROM product_orders l JOIN mp_offers f ON f.id = l.offer_id
                               WHERE l.order_id = o.id_order AND COALESCE(l.deleted, 0) = 0
                                 AND f.fulfillment = 'PLATFORM') AS consignment""" + FROM + where + orderBy(pageable) + " LIMIT :limit OFFSET :offset",
                params, (rs, i) -> row(rs));
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    @Override
    public Stats stats(long companyId, LocalDate today) {
        MapSqlParameterSource params = new MapSqlParameterSource("company", companyId).addValue("today", today)
                .addValue("since", today.minusDays(30));
        Stats head = jdbc.query("""
                SELECT COALESCE(SUM(%1$s), 0) AS to_ship,
                       COALESCE(SUM(%1$s AND o.ship_by < :today), 0) AS overdue,
                       COALESCE(SUM(%1$s AND o.ship_by = :today), 0) AS due_today,
                       COALESCE(SUM(%1$s AND o.ship_by IS NULL), 0) AS without_ship_by,
                       COALESCE(SUM(o.state = 'SHIPPED'), 0) AS in_transit,
                       COALESCE(SUM(o.state = 'DELIVERED' AND o.delivered_at >= :since), 0) AS delivered30,
                       COALESCE(SUM(o.shipped_at >= :since AND o.ship_by IS NOT NULL), 0) AS shipped30,
                       COALESCE(SUM(o.shipped_at >= :since AND o.ship_by IS NOT NULL AND DATE(o.shipped_at) <= o.ship_by), 0) AS on_time30
                """.formatted(OPEN) + FROM, params,
                (rs, i) -> new Stats(rs.getLong("to_ship"), rs.getLong("overdue"), rs.getLong("due_today"),
                        rs.getLong("in_transit"), rs.getLong("delivered30"), rs.getLong("shipped30"), rs.getLong("on_time30"),
                        rs.getLong("without_ship_by"), List.of())).get(0);
        List<SellerLoad> sellers = jdbc.query("""
                SELECT o.seller_id, COALESCE(s.trade_name, s.legal_name) AS seller_name, s.kind AS seller_kind,
                       COUNT(*) AS to_ship, COALESCE(SUM(o.ship_by < :today), 0) AS overdue""" + FROM + " AND " + OPEN + " " + """
                GROUP BY o.seller_id, seller_name, seller_kind ORDER BY overdue DESC, to_ship DESC LIMIT 8""", params,
                (rs, i) -> {
                    long id = rs.getLong("seller_id");
                    return new SellerLoad(rs.wasNull() ? null : id, rs.getString("seller_name"),
                            "EXTERNAL".equals(rs.getString("seller_kind")), rs.getLong("to_ship"), rs.getLong("overdue"));
                });
        return new Stats(head.toShip(), head.overdue(), head.dueToday(), head.inTransit(), head.delivered30(), head.shipped30(),
                head.onTime30(), head.withoutShipBy(), sellers);
    }

    static String viewCondition(String view) {
        return switch (view == null ? "TO_SHIP" : view.toUpperCase(Locale.ROOT)) {
            case "TO_SHIP" -> OPEN;
            case "OVERDUE" -> OPEN + " AND o.ship_by < :today";
            case "DUE_TODAY" -> OPEN + " AND o.ship_by = :today";
            case "IN_TRANSIT" -> "o.state = 'SHIPPED'";
            case "DELIVERED" -> "o.state = 'DELIVERED' AND o.delivered_at >= :since";
            case "ALL" -> "o.state IN ('PAID', 'PROCESSING', 'READY', 'SHIPPED', 'DELIVERED')";
            default -> throw new IllegalArgumentException("Vista no válida: " + view
                    + " (TO_SHIP, OVERDUE, DUE_TODAY, IN_TRANSIT, DELIVERED o ALL).");
        };
    }

    private static String orderBy(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            String dir = order.isAscending() ? " ASC" : " DESC";
            switch (order.getProperty()) {
                case "paidAt" -> {
                    return " ORDER BY o.paid_at" + dir + ", o.id_order" + dir;
                }
                case "shippedAt" -> {
                    return " ORDER BY o.shipped_at" + dir + ", o.id_order" + dir;
                }
                case "shipBy" -> {
                    return " ORDER BY o.ship_by IS NULL, o.ship_by" + dir + ", o.id_order ASC";
                }
                default -> {
                }
            }
        }
        return " ORDER BY o.ship_by IS NULL, o.ship_by ASC, o.id_order ASC";
    }

    private static DispatchRow row(ResultSet rs) throws SQLException {
        return new DispatchRow(rs.getLong("id_order"), rs.getString("order_number"), rs.getString("state"),
                rs.getString("delivery_method"), longOrNull(rs, "purchase_id"), rs.getString("purchase_number"),
                longOrNull(rs, "seller_id"), rs.getString("seller_name"), "EXTERNAL".equals(rs.getString("seller_kind")),
                rs.getString("customer_name"), rs.getString("ship_city"), money(rs, "total"), instant(rs, "paid_at"),
                rs.getObject("ship_by", LocalDate.class), instant(rs, "shipped_at"), instant(rs, "delivered_at"),
                longOrNull(rs, "carrier_id"), rs.getString("carrier_name"), rs.getString("tracking_number"),
                rs.getInt("consignment") == 1);
    }

    private static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static BigDecimal money(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return (value == null ? BigDecimal.ZERO : value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
