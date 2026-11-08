package com.essenza.draco.modules.inventory.infrastructure.outbound.purchase;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
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

import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.EventDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.LineDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.OptionDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.OrderSummaryDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.ReceiptDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.ReceiptLineDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.StatsDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.SupplierOptionDto;
import com.essenza.draco.modules.inventory.application.dto.purchase.PurchaseOrderDtos.SupplierProductDto;
import com.essenza.draco.modules.inventory.application.output.repository.PurchaseOrderStore;

/** Órdenes de compra en MySQL (JDBC, misma transacción que JPA). Se filtran por la empresa del token. */
@Repository
public class PurchaseOrderJdbcStore implements PurchaseOrderStore {

    private static final String SUPPLIER_NAME = "COALESCE(NULLIF(s.trade_name, ''), s.name)";
    private static final String SELECT = """
            SELECT p.*, %s AS supplier_name, w.name AS warehouse_name,
                   (SELECT COALESCE(SUM(l.quantity), 0) FROM inv_purchase_order_lines l WHERE l.order_id = p.id) AS ordered_units,
                   (SELECT COALESCE(SUM(l.received_quantity), 0) FROM inv_purchase_order_lines l WHERE l.order_id = p.id) AS received_units
            FROM inv_purchase_orders p JOIN suppliers s ON s.id_supplier = p.supplier_id
            LEFT JOIN warehouses w ON w.id_warehouse = p.warehouse_id
            WHERE p.company_id = :company""".formatted(SUPPLIER_NAME);
    private static final Map<String, String> SORTS = Map.of("createdAt", "p.created_at", "expectedDate", "p.expected_date", "total", "p.total");

    private final NamedParameterJdbcTemplate jdbc;

    public PurchaseOrderJdbcStore(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public Optional<SupplierRef> supplier(long supplierId) {
        return jdbc.query("SELECT s.id_supplier, " + SUPPLIER_NAME + " AS name, s.status, s.currency, s.lead_time_days, s.min_order_amount"
                        + " FROM suppliers s WHERE s.id_supplier = :id AND s.deleted = 0",
                new MapSqlParameterSource("id", supplierId), (rs, i) -> new SupplierRef(rs.getLong(1), rs.getString("name"),
                        rs.getString("status"), rs.getString("currency"), intOrNull(rs, "lead_time_days"), rs.getBigDecimal("min_order_amount")))
                .stream().findFirst();
    }

    @Override
    public Optional<String> activeWarehouse(long warehouseId) {
        return jdbc.query("SELECT name FROM warehouses WHERE id_warehouse = :id AND deleted = 0 AND active = 1",
                new MapSqlParameterSource("id", warehouseId), (rs, i) -> rs.getString(1)).stream().findFirst();
    }

    @Override
    public Map<Long, SkuInfo> skus(Collection<Long> skuIds) {
        Map<Long, SkuInfo> found = new LinkedHashMap<>();
        if (skuIds.isEmpty()) {
            return found;
        }
        jdbc.query("""
                SELECT k.id_sku, k.product_id, k.code, COALESCE(k.name, p.name) AS name
                FROM product_skus k LEFT JOIN products p ON p.id_product = k.product_id
                WHERE k.id_sku IN (:ids) AND k.deleted = 0""", new MapSqlParameterSource("ids", skuIds), rs -> {
            found.put(rs.getLong(1), new SkuInfo(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4)));
        });
        return found;
    }

    @Override
    public Map<Long, SupplierProductDto> supplierProducts(long supplierId) {
        Map<Long, SupplierProductDto> items = new LinkedHashMap<>();
        jdbc.query("""
                SELECT sp.sku_id, sp.product_id, k.code, COALESCE(k.name, p.name) AS name, sp.supplier_code, sp.unit_cost,
                       sp.min_order_qty, sp.preferred
                FROM supplier_products sp JOIN product_skus k ON k.id_sku = sp.sku_id
                LEFT JOIN products p ON p.id_product = sp.product_id
                WHERE sp.supplier_id = :id AND sp.active = 1 AND k.deleted = 0 ORDER BY k.code""",
                new MapSqlParameterSource("id", supplierId), rs -> {
                    items.put(rs.getLong("sku_id"), new SupplierProductDto(rs.getLong("sku_id"), rs.getLong("product_id"),
                            rs.getString("code"), rs.getString("name"), rs.getString("supplier_code"), rs.getBigDecimal("unit_cost"),
                            intOrNull(rs, "min_order_qty"), rs.getInt("preferred") == 1));
                });
        return items;
    }

    @Override
    public long insertOrder(long companyId, SupplierRef supplier, long warehouseId, LocalDate expected, String notes, BigDecimal total,
                            String actorId, String actorName, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO inv_purchase_orders (company_id, supplier_id, warehouse_id, status, currency, expected_date, notes, total,
                  lock_version, created_by, created_by_name, created_at, updated_at)
                VALUES (:company, :supplier, :warehouse, 'DRAFT', :currency, :expected, :notes, :total, 0, :actor, :actorName, :now, :now)""",
                new MapSqlParameterSource("company", companyId).addValue("supplier", supplier.id()).addValue("warehouse", warehouseId)
                        .addValue("currency", supplier.currency() == null ? "COP" : supplier.currency()).addValue("expected", expected)
                        .addValue("notes", notes).addValue("total", total).addValue("actor", actorId).addValue("actorName", actorName)
                        .addValue("now", utc(now)), keys, new String[] {"id"});
        return keys.getKey().longValue();
    }

    @Override
    public void setOrderNumber(long id, String number) {
        jdbc.update("UPDATE inv_purchase_orders SET order_number = :n WHERE id = :id", new MapSqlParameterSource("n", number).addValue("id", id));
    }

    @Override
    public boolean updateOrder(long id, int lockVersion, long supplierId, long warehouseId, LocalDate expected, String notes,
                               BigDecimal total, Instant now) {
        return jdbc.update("""
                UPDATE inv_purchase_orders SET supplier_id = :supplier, warehouse_id = :warehouse, expected_date = :expected,
                  notes = :notes, total = :total, lock_version = lock_version + 1, updated_at = :now
                WHERE id = :id AND lock_version = :lock AND status = 'DRAFT'""",
                new MapSqlParameterSource("supplier", supplierId).addValue("warehouse", warehouseId).addValue("expected", expected)
                        .addValue("notes", notes).addValue("total", total).addValue("now", utc(now)).addValue("id", id)
                        .addValue("lock", lockVersion)) == 1;
    }

    @Override
    public void replaceLines(long orderId, List<NewLine> lines) {
        jdbc.update("DELETE FROM inv_purchase_order_lines WHERE order_id = :id AND received_quantity = 0",
                new MapSqlParameterSource("id", orderId));
        for (NewLine l : lines) {
            jdbc.update("""
                    INSERT INTO inv_purchase_order_lines (order_id, sku_id, product_id, sku_code, product_name, supplier_code, quantity,
                      received_quantity, unit_cost)
                    VALUES (:order, :sku, :product, :code, LEFT(:name, 300), :supplierCode, :qty, 0, :cost)""",
                    new MapSqlParameterSource("order", orderId).addValue("sku", l.skuId()).addValue("product", l.productId())
                            .addValue("code", l.skuCode()).addValue("name", l.productName()).addValue("supplierCode", l.supplierCode())
                            .addValue("qty", l.quantity()).addValue("cost", l.unitCost()));
        }
    }

    @Override
    public Optional<OrderRow> find(long companyId, long id, boolean forUpdate) {
        return jdbc.query(SELECT + " AND p.id = :id" + (forUpdate ? " FOR UPDATE" : ""),
                new MapSqlParameterSource("company", companyId).addValue("id", id).addValue("today", LocalDate.now()),
                (rs, i) -> new OrderRow(summary(rs, null), rs.getString("currency"), rs.getString("notes"), rs.getString("status_reason"),
                        instant(rs, "sent_at"), instant(rs, "closed_at"), rs.getInt("lock_version"), rs.getString("created_by_name")))
                .stream().findFirst();
    }

    @Override
    public List<LineDto> lines(long orderId) {
        return jdbc.query("SELECT * FROM inv_purchase_order_lines WHERE order_id = :id ORDER BY id", new MapSqlParameterSource("id", orderId),
                (rs, i) -> {
                    int qty = rs.getInt("quantity");
                    int received = rs.getInt("received_quantity");
                    BigDecimal cost = rs.getBigDecimal("unit_cost");
                    return new LineDto(rs.getLong("id"), rs.getLong("sku_id"), rs.getLong("product_id"), rs.getString("sku_code"),
                            rs.getString("product_name"), rs.getString("supplier_code"), qty, received, qty - received, cost,
                            cost.multiply(BigDecimal.valueOf(qty)));
                });
    }

    @Override
    public boolean setStatus(long id, int lockVersion, String status, String reason, boolean sent, boolean closed, Instant now) {
        return jdbc.update("""
                UPDATE inv_purchase_orders SET status = :status, status_reason = COALESCE(:reason, status_reason),
                  sent_at = CASE WHEN :sent THEN :now ELSE sent_at END, closed_at = CASE WHEN :closed THEN :now ELSE closed_at END,
                  lock_version = lock_version + 1, updated_at = :now
                WHERE id = :id AND lock_version = :lock""",
                new MapSqlParameterSource("status", status).addValue("reason", reason).addValue("sent", sent).addValue("closed", closed)
                        .addValue("now", utc(now)).addValue("id", id).addValue("lock", lockVersion)) == 1;
    }

    @Override
    public boolean hasReceipts(long orderId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM inv_purchase_receipts WHERE order_id = :id",
                new MapSqlParameterSource("id", orderId), Integer.class);
        return n != null && n > 0;
    }

    @Override
    public long insertReceipt(long companyId, long orderId, long warehouseId, String reference, String notes, int units, String actorId,
                              String actorName, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO inv_purchase_receipts (company_id, order_id, warehouse_id, reference, notes, units, created_by, created_by_name,
                  created_at)
                VALUES (:company, :order, :warehouse, :reference, :notes, :units, :actor, :actorName, :now)""",
                new MapSqlParameterSource("company", companyId).addValue("order", orderId).addValue("warehouse", warehouseId)
                        .addValue("reference", reference).addValue("notes", notes).addValue("units", units).addValue("actor", actorId)
                        .addValue("actorName", actorName).addValue("now", utc(now)), keys, new String[] {"id"});
        return keys.getKey().longValue();
    }

    @Override
    public void setReceiptNumber(long id, String number) {
        jdbc.update("UPDATE inv_purchase_receipts SET receipt_number = :n WHERE id = :id",
                new MapSqlParameterSource("n", number).addValue("id", id));
    }

    @Override
    public void insertReceiptLine(long receiptId, long lineId, int quantity) {
        jdbc.update("INSERT INTO inv_purchase_receipt_lines (receipt_id, line_id, quantity) VALUES (:r, :l, :q)",
                new MapSqlParameterSource("r", receiptId).addValue("l", lineId).addValue("q", quantity));
    }

    @Override
    public void addReceived(long lineId, int quantity) {
        int updated = jdbc.update("""
                UPDATE inv_purchase_order_lines SET received_quantity = received_quantity + :q
                WHERE id = :id AND received_quantity + :q <= quantity""", new MapSqlParameterSource("q", quantity).addValue("id", lineId));
        if (updated != 1) {
            throw new com.essenza.draco.shared.exceptions.ConflictException("La línea " + lineId + " ya no tiene esa cantidad pendiente.");
        }
    }

    @Override
    public List<ReceiptDto> receipts(long orderId) {
        Map<Long, List<ReceiptLineDto>> lines = new LinkedHashMap<>();
        jdbc.query("""
                SELECT rl.receipt_id, rl.line_id, l.sku_code, l.product_name, rl.quantity
                FROM inv_purchase_receipt_lines rl JOIN inv_purchase_order_lines l ON l.id = rl.line_id
                JOIN inv_purchase_receipts r ON r.id = rl.receipt_id WHERE r.order_id = :id ORDER BY rl.id""",
                new MapSqlParameterSource("id", orderId), rs -> {
                    lines.computeIfAbsent(rs.getLong("receipt_id"), k -> new ArrayList<>()).add(new ReceiptLineDto(rs.getLong("line_id"),
                            rs.getString("sku_code"), rs.getString("product_name"), rs.getInt("quantity")));
                });
        return jdbc.query("""
                SELECT r.*, w.name AS warehouse_name FROM inv_purchase_receipts r LEFT JOIN warehouses w ON w.id_warehouse = r.warehouse_id
                WHERE r.order_id = :id ORDER BY r.id""", new MapSqlParameterSource("id", orderId),
                (rs, i) -> new ReceiptDto(rs.getLong("id"), rs.getString("receipt_number"), rs.getString("warehouse_name"),
                        rs.getString("reference"), rs.getString("notes"), rs.getInt("units"),
                        rs.getString("created_by_name") != null ? rs.getString("created_by_name") : rs.getString("created_by"),
                        instant(rs, "created_at"), lines.getOrDefault(rs.getLong("id"), List.of())));
    }

    @Override
    public void addEvent(long orderId, String action, String from, String to, String note, String actorId, String actorName, Instant now) {
        jdbc.update("""
                INSERT INTO inv_purchase_order_events (order_id, action, from_status, to_status, note, actor_id, actor_name, created_at)
                VALUES (:id, :action, :from, :to, :note, :actor, :actorName, :now)""",
                new MapSqlParameterSource("id", orderId).addValue("action", action).addValue("from", from).addValue("to", to)
                        .addValue("note", note).addValue("actor", actorId).addValue("actorName", actorName).addValue("now", utc(now)));
    }

    @Override
    public List<EventDto> events(long orderId) {
        return jdbc.query("SELECT * FROM inv_purchase_order_events WHERE order_id = :id ORDER BY id", new MapSqlParameterSource("id", orderId),
                (rs, i) -> new EventDto(rs.getString("action"), rs.getString("from_status"), rs.getString("to_status"), rs.getString("note"),
                        rs.getString("actor_name") != null ? rs.getString("actor_name") : rs.getString("actor_id"), instant(rs, "created_at")));
    }

    @Override
    public Page<OrderSummaryDto> search(long companyId, String status, Long supplierId, String q, LocalDate today, Pageable pageable) {
        MapSqlParameterSource p = new MapSqlParameterSource("company", companyId).addValue("today", today);
        StringBuilder where = new StringBuilder();
        if ("OPEN".equals(status)) {
            where.append(" AND p.status IN ('SENT', 'PARTIALLY_RECEIVED')");
        } else if ("OVERDUE".equals(status)) {
            where.append(" AND p.status IN ('SENT', 'PARTIALLY_RECEIVED') AND p.expected_date < :today");
        } else if (status != null) {
            where.append(" AND p.status = :status");
            p.addValue("status", status);
        }
        if (supplierId != null) {
            where.append(" AND p.supplier_id = :supplier");
            p.addValue("supplier", supplierId);
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND (LOWER(COALESCE(p.order_number, '')) LIKE :q OR LOWER(").append(SUPPLIER_NAME).append(") LIKE :q)");
            String value = q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            p.addValue("q", "%" + value + "%");
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM inv_purchase_orders p JOIN suppliers s ON s.id_supplier = p.supplier_id"
                + " WHERE p.company_id = :company" + where, p, Long.class);
        p.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<OrderSummaryDto> rows = jdbc.query(SELECT + where + orderBy(pageable) + " LIMIT :limit OFFSET :offset", p,
                (rs, i) -> summary(rs, today));
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    @Override
    public StatsDto stats(long companyId, LocalDate today) {
        MapSqlParameterSource p = new MapSqlParameterSource("company", companyId).addValue("today", today)
                .addValue("since", today.minusDays(30).atStartOfDay());
        StatsDto head = jdbc.query("""
                SELECT COALESCE(SUM(status = 'DRAFT'), 0) AS drafts, COALESCE(SUM(status IN ('SENT', 'PARTIALLY_RECEIVED')), 0) AS open_n,
                       COALESCE(SUM(status IN ('SENT', 'PARTIALLY_RECEIVED') AND expected_date < :today), 0) AS overdue,
                       COALESCE(SUM(status = 'PARTIALLY_RECEIVED'), 0) AS partial
                FROM inv_purchase_orders WHERE company_id = :company""", p,
                (rs, i) -> new StatsDto(rs.getLong("drafts"), rs.getLong("open_n"), rs.getLong("overdue"), rs.getLong("partial"), 0,
                        BigDecimal.ZERO)).get(0);
        Object[] received = jdbc.query("""
                SELECT COALESCE(SUM(rl.quantity), 0) AS units, COALESCE(SUM(rl.quantity * l.unit_cost), 0) AS amount
                FROM inv_purchase_receipt_lines rl JOIN inv_purchase_receipts r ON r.id = rl.receipt_id
                JOIN inv_purchase_order_lines l ON l.id = rl.line_id
                WHERE r.company_id = :company AND r.created_at >= :since""", p,
                (rs, i) -> new Object[] {rs.getLong("units"), rs.getBigDecimal("amount")}).get(0);
        return new StatsDto(head.drafts(), head.open(), head.overdue(), head.partiallyReceived(), (Long) received[0],
                ((BigDecimal) received[1]).setScale(2, java.math.RoundingMode.HALF_UP));
    }

    @Override
    public List<SupplierOptionDto> supplierOptions() {
        return jdbc.query("SELECT s.id_supplier, " + SUPPLIER_NAME + " AS name, s.status, s.lead_time_days, s.min_order_amount"
                        + " FROM suppliers s WHERE s.deleted = 0 ORDER BY name LIMIT 500", new MapSqlParameterSource(),
                (rs, i) -> new SupplierOptionDto(rs.getLong(1), rs.getString("name"), rs.getString("status"), intOrNull(rs, "lead_time_days"),
                        rs.getBigDecimal("min_order_amount")));
    }

    @Override
    public List<OptionDto> warehouseOptions() {
        return jdbc.query("SELECT id_warehouse, name FROM warehouses WHERE deleted = 0 AND active = 1 ORDER BY name LIMIT 200",
                new MapSqlParameterSource(), (rs, i) -> new OptionDto(rs.getLong(1), rs.getString(2)));
    }

    @Override
    public List<LowStockRow> lowStock(long companyId, Long warehouseId, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource("company", companyId).addValue("limit", limit);
        String byWarehouse = "";
        if (warehouseId != null) {
            byWarehouse = " AND i.warehouse_id = :warehouse";
            params.addValue("warehouse", warehouseId);
        }
        return jdbc.query("""
                SELECT i.sku_id, i.product_id, k.code, COALESCE(k.name, p.name) AS name, i.warehouse_id, w.name AS warehouse_name,
                       i.on_hand, i.reserved, i.min_threshold, COALESCE(oo.pending, 0) AS on_order, c.avg_cost
                FROM stock_items i
                JOIN product_skus k ON k.id_sku = i.sku_id AND k.deleted = 0
                LEFT JOIN products p ON p.id_product = i.product_id
                JOIN warehouses w ON w.id_warehouse = i.warehouse_id AND w.deleted = 0 AND w.active = 1
                LEFT JOIN inv_sku_costs c ON c.sku_id = i.sku_id
                LEFT JOIN (SELECT o.warehouse_id, l.sku_id, SUM(l.quantity - l.received_quantity) AS pending
                           FROM inv_purchase_order_lines l JOIN inv_purchase_orders o ON o.id = l.order_id
                           WHERE o.company_id = :company AND o.status IN ('DRAFT', 'SENT', 'PARTIALLY_RECEIVED')
                           GROUP BY o.warehouse_id, l.sku_id) oo ON oo.warehouse_id = i.warehouse_id AND oo.sku_id = i.sku_id
                WHERE i.deleted = 0 AND i.min_threshold > 0 AND (i.on_hand - i.reserved) <= i.min_threshold""" + byWarehouse
                + " ORDER BY w.name, k.code, i.sku_id LIMIT :limit", params,
                (rs, i) -> new LowStockRow(rs.getLong("sku_id"), rs.getLong("product_id"), rs.getString("code"), rs.getString("name"),
                        rs.getLong("warehouse_id"), rs.getString("warehouse_name"), rs.getInt("on_hand"), rs.getInt("reserved"),
                        rs.getInt("min_threshold"), rs.getInt("on_order"), rs.getBigDecimal("avg_cost")));
    }

    @Override
    public Map<Long, List<SupplierCandidate>> supplierCandidates(Collection<Long> skuIds) {
        Map<Long, List<SupplierCandidate>> found = new LinkedHashMap<>();
        if (skuIds.isEmpty()) {
            return found;
        }
        jdbc.query("SELECT sp.sku_id, s.id_supplier, " + SUPPLIER_NAME + " AS supplier_name, s.lead_time_days, s.min_order_amount,"
                + """
                 sp.supplier_code, sp.unit_cost, sp.min_order_qty, sp.preferred
                FROM supplier_products sp JOIN suppliers s ON s.id_supplier = sp.supplier_id
                WHERE sp.sku_id IN (:ids) AND sp.active = 1 AND s.deleted = 0 AND s.status = 'ACTIVE'
                ORDER BY sp.sku_id, s.id_supplier""", new MapSqlParameterSource("ids", skuIds), rs -> {
            found.computeIfAbsent(rs.getLong("sku_id"), k -> new ArrayList<>()).add(new SupplierCandidate(rs.getLong("id_supplier"),
                    rs.getString("supplier_name"), intOrNull(rs, "lead_time_days"), rs.getBigDecimal("min_order_amount"),
                    rs.getString("supplier_code"), rs.getBigDecimal("unit_cost"), intOrNull(rs, "min_order_qty"), rs.getInt("preferred") == 1));
        });
        return found;
    }

    private static OrderSummaryDto summary(ResultSet rs, LocalDate today) throws SQLException {
        LocalDate expected = rs.getObject("expected_date", LocalDate.class);
        String status = rs.getString("status");
        LocalDate now = today == null ? LocalDate.now() : today;
        boolean open = "SENT".equals(status) || "PARTIALLY_RECEIVED".equals(status);
        return new OrderSummaryDto(rs.getLong("id"), rs.getString("order_number"), rs.getLong("supplier_id"), rs.getString("supplier_name"),
                rs.getLong("warehouse_id"), rs.getString("warehouse_name"), status, expected, open && expected != null && expected.isBefore(now),
                rs.getBigDecimal("total"), rs.getLong("ordered_units"), rs.getLong("received_units"), instant(rs, "created_at"));
    }

    private static String orderBy(Pageable pageable) {
        StringJoiner joiner = new StringJoiner(", ");
        for (Sort.Order order : pageable.getSort()) {
            String column = SORTS.get(order.getProperty());
            if (column != null) {
                joiner.add(column + (order.isAscending() ? " ASC" : " DESC"));
            }
        }
        return " ORDER BY " + (joiner.length() == 0 ? "" : joiner + ", ") + "p.id DESC";
    }

    private static Integer intOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime utc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
