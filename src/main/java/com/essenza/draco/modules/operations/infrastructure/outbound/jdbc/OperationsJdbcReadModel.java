package com.essenza.draco.modules.operations.infrastructure.outbound.jdbc;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.operations.application.dto.AlertDtos.AlertItemDto;
import com.essenza.draco.modules.operations.application.port.OperationsReadModel;
import com.essenza.draco.modules.operations.domain.AlertCatalog.Thresholds;

/**
 * Consultas de solo lectura del centro de operaciones (JDBC). Las tablas del marketplace se
 * filtran por la empresa del token; las órdenes propias, facturas, devoluciones e inventario son
 * de la tienda (una sola empresa), igual que en sus módulos.
 */
@Repository
public class OperationsJdbcReadModel implements OperationsReadModel {

    // Espacio inicial explícito: el bloque de texto quita la sangría común.
    private static final String OPEN_ORDERS = " " + """
             FROM orders o LEFT JOIN mp_sellers s ON s.id = o.seller_id
             WHERE (o.seller_id IS NULL OR s.company_id = :company) AND COALESCE(o.channel, '') <> 'POS'
               AND o.state IN ('PAID', 'PROCESSING', 'READY')""";
    private static final String ORDER_ITEM = """
            SELECT o.id_order AS ref_id, COALESCE(o.order_number, CONCAT('#', o.id_order)) AS label,
                   CONCAT_WS(' · ', COALESCE(s.trade_name, s.legal_name), o.customer_name) AS detail, o.total AS amount,
                   o.paid_at AS at, DATEDIFF(:today, o.ship_by) AS days""";
    /** Abonos y cargos del vendedor hasta hoy: vencido = cargos vencidos − abonos (se aplican a lo más antiguo). */
    private static final String SELLER_BALANCES = """
            SELECT l.seller_id, COALESCE(s.trade_name, s.legal_name) AS name,
                   SUM(l.amount) AS balance,
                   GREATEST(0, SUM(CASE WHEN l.amount > 0 AND COALESCE(l.due_on, l.entry_date) < :today THEN l.amount ELSE 0 END)
                             - SUM(CASE WHEN l.amount < 0 THEN -l.amount ELSE 0 END)) AS overdue,
                   MIN(CASE WHEN l.amount > 0 THEN COALESCE(l.due_on, l.entry_date) END) AS oldest
            FROM mp_seller_ledger l JOIN mp_sellers s ON s.id = l.seller_id
            WHERE l.company_id = :company AND l.entry_date <= :today
            GROUP BY l.seller_id, name""";

    private final NamedParameterJdbcTemplate jdbc;

    public OperationsJdbcReadModel(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public AlertData compute(String key, long companyId, LocalDate today, Thresholds t) {
        MapSqlParameterSource p = new MapSqlParameterSource("company", companyId).addValue("today", today)
                .addValue("limit", SAMPLE);
        return switch (key) {
            case "DISPATCH_OVERDUE" -> orders(p, " AND o.ship_by < :today", "o.ship_by, o.id_order");
            case "DISPATCH_DUE_TODAY" -> orders(p, " AND o.ship_by = :today", "o.id_order");
            case "DISPATCH_WITHOUT_DATE" -> orders(p, " AND o.ship_by IS NULL AND o.paid_at IS NOT NULL", "o.paid_at, o.id_order");
            case "COMMISSIONS_IN_REVIEW" -> data(p,
                    "SELECT COUNT(*) AS n, SUM(c.amount) AS amount FROM mp_commissions c WHERE c.company_id = :company AND c.status = 'REVIEW'",
                    """
                    SELECT c.id AS ref_id, COALESCE(c.order_number, CONCAT('#', c.order_id)) AS label,
                           CONCAT_WS(' · ', COALESCE(s.trade_name, s.legal_name), c.review_reason) AS detail, c.amount,
                           c.created_at AS at, NULL AS days
                    FROM mp_commissions c JOIN mp_sellers s ON s.id = c.seller_id
                    WHERE c.company_id = :company AND c.status = 'REVIEW' ORDER BY c.created_at LIMIT :limit""", "COMMISSION");
            case "SELLERS_OVERDUE" -> data(p,
                    "SELECT COUNT(*) AS n, SUM(b.overdue) AS amount FROM (" + SELLER_BALANCES + ") b WHERE b.overdue > 0",
                    "SELECT b.seller_id AS ref_id, b.name AS label, NULL AS detail, b.overdue AS amount, NULL AS at,"
                            + " DATEDIFF(:today, b.oldest) AS days FROM (" + SELLER_BALANCES + ") b WHERE b.overdue > 0"
                            + " ORDER BY b.overdue DESC LIMIT :limit", "SELLER");
            case "SELLERS_PAYABLE" -> data(p,
                    "SELECT COUNT(*) AS n, -SUM(b.balance) AS amount FROM (" + SELLER_BALANCES + ") b WHERE b.balance < 0",
                    "SELECT b.seller_id AS ref_id, b.name AS label, NULL AS detail, -b.balance AS amount, NULL AS at, NULL AS days"
                            + " FROM (" + SELLER_BALANCES + ") b WHERE b.balance < 0 ORDER BY b.balance LIMIT :limit", "SELLER");
            case "DISPUTES_OVERDUE" -> disputes(p, "d.status = 'OPEN' AND d.respond_by < :today");
            case "DISPUTES_TO_DECIDE" -> disputes(p, "d.status = 'SELLER_RESPONDED'");
            case "OFFERS_IN_REVIEW" -> data(p,
                    "SELECT COUNT(*) AS n, NULL AS amount FROM mp_offers f WHERE f.company_id = :company AND f.status = 'PENDING_REVIEW'",
                    """
                    SELECT f.id AS ref_id, COALESCE(f.sku_code, CONCAT('#', f.sku_id)) AS label,
                           COALESCE(s.trade_name, s.legal_name) AS detail, f.price AS amount, f.updated_at AS at,
                           DATEDIFF(:today, f.updated_at) AS days
                    FROM mp_offers f JOIN mp_sellers s ON s.id = f.seller_id
                    WHERE f.company_id = :company AND f.status = 'PENDING_REVIEW' ORDER BY f.updated_at LIMIT :limit""", "OFFER");
            case "SELLERS_IN_REVIEW" -> data(p,
                    "SELECT COUNT(*) AS n, NULL AS amount FROM mp_sellers s WHERE s.company_id = :company AND s.status = 'IN_REVIEW'",
                    """
                    SELECT s.id AS ref_id, COALESCE(s.trade_name, s.legal_name) AS label, s.doc_number AS detail, NULL AS amount,
                           s.updated_at AS at, DATEDIFF(:today, s.updated_at) AS days
                    FROM mp_sellers s WHERE s.company_id = :company AND s.status = 'IN_REVIEW' ORDER BY s.updated_at LIMIT :limit""",
                    "SELLER");
            case "CONSIGNMENT_WITHOUT_OFFER" -> {
                String grouped = """
                        SELECT x.seller_id, x.sku_id, SUM(x.on_hand) AS units FROM mp_consignment_stock x
                        WHERE x.company_id = :company AND NOT EXISTS (
                            SELECT 1 FROM mp_offers f WHERE f.company_id = x.company_id AND f.seller_id = x.seller_id
                              AND f.sku_id = x.sku_id AND f.status <> 'CLOSED' AND f.fulfillment = 'PLATFORM')
                        GROUP BY x.seller_id, x.sku_id HAVING SUM(x.on_hand) > 0""";
                yield data(p, "SELECT COUNT(*) AS n, NULL AS amount FROM (" + grouped + ") g",
                        "SELECT g.seller_id AS ref_id, k.code AS label, COALESCE(s.trade_name, s.legal_name) AS detail, g.units AS amount,"
                                + " NULL AS at, NULL AS days FROM (" + grouped + ") g JOIN product_skus k ON k.id_sku = g.sku_id"
                                + " JOIN mp_sellers s ON s.id = g.seller_id ORDER BY g.units DESC LIMIT :limit", "SELLER");
            }
            case "CONSIGNMENT_AGED" -> {
                p.addValue("aged", today.minusDays(t.consignmentAgedDays()));
                yield data(p, """
                        SELECT COUNT(*) AS n, SUM(x.on_hand) AS amount FROM mp_consignment_stock x
                        WHERE x.company_id = :company AND x.on_hand > 0 AND x.first_received_at < :aged""",
                        """
                        SELECT x.seller_id AS ref_id, k.code AS label,
                               CONCAT_WS(' · ', COALESCE(s.trade_name, s.legal_name), w.name) AS detail, x.on_hand AS amount,
                               x.first_received_at AS at, DATEDIFF(:today, x.first_received_at) AS days
                        FROM mp_consignment_stock x JOIN product_skus k ON k.id_sku = x.sku_id
                        JOIN mp_sellers s ON s.id = x.seller_id LEFT JOIN warehouses w ON w.id_warehouse = x.warehouse_id
                        WHERE x.company_id = :company AND x.on_hand > 0 AND x.first_received_at < :aged
                        ORDER BY x.first_received_at LIMIT :limit""", "SELLER");
            }
            case "INVOICES_REJECTED" -> invoices(p, "i.status = 'REJECTED'");
            case "INVOICES_PENDING" -> {
                p.addValue("pendingBefore", today.minusDays(t.invoicesPendingDays()).atStartOfDay());
                yield invoices(p, "i.status = 'PENDING' AND i.issued_at < :pendingBefore");
            }
            case "RETURNS_STALLED" -> {
                p.addValue("stalled", today.minusDays(t.returnsStalledDays()).atStartOfDay());
                String where = " FROM order_devolutions d LEFT JOIN orders o ON o.id_order = d.order_id"
                        + " WHERE COALESCE(d.deleted, 0) = 0 AND d.state IN ('P', 'A', 'R', 'I')"
                        + " AND COALESCE(d.updated_at, d.created_at) < :stalled";
                yield data(p, "SELECT COUNT(*) AS n, SUM(d.total_refund_amount) AS amount" + where,
                        "SELECT d.id_order_devolution AS ref_id, CONCAT('#', d.id_order_devolution) AS label,"
                                + " CONCAT_WS(' · ', o.order_number, d.state) AS detail, d.total_refund_amount AS amount,"
                                + " COALESCE(d.updated_at, d.created_at) AS at,"
                                + " DATEDIFF(:today, COALESCE(d.updated_at, d.created_at)) AS days" + where
                                + " ORDER BY COALESCE(d.updated_at, d.created_at) LIMIT :limit", "RETURN");
            }
            case "PURCHASES_OVERDUE" -> {
                String where = " FROM inv_purchase_orders p JOIN suppliers s ON s.id_supplier = p.supplier_id"
                        + " WHERE p.company_id = :company AND p.status IN ('SENT', 'PARTIALLY_RECEIVED') AND p.expected_date < :today";
                yield data(p, "SELECT COUNT(*) AS n, SUM(p.total) AS amount" + where,
                        "SELECT p.id AS ref_id, COALESCE(p.order_number, CONCAT('#', p.id)) AS label,"
                                + " COALESCE(NULLIF(s.trade_name, ''), s.name) AS detail, p.total AS amount, p.created_at AS at,"
                                + " DATEDIFF(:today, p.expected_date) AS days" + where + " ORDER BY p.expected_date, p.id LIMIT :limit",
                        "PURCHASE_ORDER");
            }
            case "LOW_STOCK" -> {
                String where = " FROM stock_items i JOIN product_skus k ON k.id_sku = i.sku_id"
                        + " LEFT JOIN warehouses w ON w.id_warehouse = i.warehouse_id"
                        + " WHERE i.deleted = 0 AND i.min_threshold > 0 AND i.on_hand - i.reserved <= i.min_threshold";
                yield data(p, "SELECT COUNT(*) AS n, NULL AS amount" + where,
                        "SELECT i.warehouse_id AS ref_id, k.code AS label,"
                                + " CONCAT_WS(' · ', w.name, CONCAT(i.on_hand - i.reserved, ' / ', i.min_threshold)) AS detail,"
                                + " i.on_hand - i.reserved AS amount, NULL AS at, NULL AS days" + where
                                + " ORDER BY (i.on_hand - i.reserved) - i.min_threshold, i.id_stock_item LIMIT :limit", "WAREHOUSE");
            }
            default -> throw new IllegalArgumentException("Alerta desconocida: " + key);
        };
    }

    private AlertData orders(MapSqlParameterSource p, String condition, String order) {
        return data(p, "SELECT COUNT(*) AS n, SUM(o.total) AS amount" + OPEN_ORDERS + condition,
                ORDER_ITEM + OPEN_ORDERS + condition + " ORDER BY " + order + " LIMIT :limit", "ORDER");
    }

    private AlertData disputes(MapSqlParameterSource p, String condition) {
        String where = " FROM mp_disputes d JOIN mp_sellers s ON s.id = d.seller_id LEFT JOIN orders o ON o.id_order = d.order_id"
                + " WHERE d.company_id = :company AND " + condition;
        return data(p, "SELECT COUNT(*) AS n, SUM(d.claimed_amount) AS amount" + where,
                "SELECT d.id AS ref_id, COALESCE(d.dispute_number, CONCAT('#', d.id)) AS label,"
                        + " CONCAT_WS(' · ', COALESCE(s.trade_name, s.legal_name), o.order_number) AS detail, d.claimed_amount AS amount,"
                        + " d.created_at AS at, DATEDIFF(:today, d.respond_by) AS days" + where
                        + " ORDER BY d.respond_by, d.id LIMIT :limit", "DISPUTE");
    }

    private AlertData invoices(MapSqlParameterSource p, String condition) {
        String where = " FROM invoices i LEFT JOIN orders o ON o.id_order = i.order_id WHERE COALESCE(i.deleted, 0) = 0 AND "
                + condition;
        return data(p, "SELECT COUNT(*) AS n, SUM(i.total) AS amount" + where,
                "SELECT i.id_invoice AS ref_id, i.full_number AS label, CONCAT_WS(' · ', o.order_number, i.status_detail) AS detail,"
                        + " i.total AS amount, i.issued_at AS at, DATEDIFF(:today, i.issued_at) AS days" + where
                        + " ORDER BY i.issued_at LIMIT :limit", "INVOICE");
    }

    private AlertData data(MapSqlParameterSource p, String countSql, String itemsSql, String refType) {
        Object[] head = jdbc.query(countSql, p, (rs, i) -> new Object[] {rs.getLong("n"), rs.getBigDecimal("amount")}).get(0);
        long count = (Long) head[0];
        List<AlertItemDto> items = count == 0 ? List.of() : jdbc.query(itemsSql, p, (rs, i) -> item(rs, refType));
        return new AlertData(count, (BigDecimal) head[1], items);
    }

    private static AlertItemDto item(ResultSet rs, String refType) throws SQLException {
        long ref = rs.getLong("ref_id");
        Long refId = rs.wasNull() ? null : ref;
        long days = rs.getLong("days");
        Long d = rs.wasNull() ? null : days;
        LocalDateTime at = rs.getObject("at", LocalDateTime.class);
        Instant when = at == null ? null : at.toInstant(ZoneOffset.UTC);
        return new AlertItemDto(refType, refId, rs.getString("label"), rs.getString("detail"), rs.getBigDecimal("amount"), when, d);
    }
}
