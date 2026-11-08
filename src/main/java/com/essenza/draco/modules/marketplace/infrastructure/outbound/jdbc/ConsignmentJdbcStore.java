package com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc;

import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.instant;
import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.like;
import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.orderBy;
import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.utc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.DocumentDto;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.MovementDto;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.MovementFilter;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.OptionDto;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.StockFilter;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.StockRowDto;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.StockStatsDto;
import com.essenza.draco.modules.marketplace.application.port.ConsignmentStore;
import com.essenza.draco.modules.marketplace.domain.consignment.ConsignmentRules.Bin;

/** Consignación en MySQL (JDBC, misma transacción que JPA). Todo se filtra por la empresa del token. */
@Repository
public class ConsignmentJdbcStore implements ConsignmentStore {

    private static final String SELLER_NAME = "COALESCE(s.trade_name, s.legal_name)";
    /** Reservado en ventas pendientes de ofertas de la plataforma del vendedor y SKU (alias x = saldo). */
    private static final String RESERVED = """
            (SELECT COALESCE(SUM(r.quantity), 0) FROM mp_offer_reservations r JOIN mp_offers f ON f.id = r.offer_id
             WHERE f.company_id = x.company_id AND f.seller_id = x.seller_id AND f.sku_id = x.sku_id
               AND f.fulfillment = 'PLATFORM' AND r.status = 'ACTIVE')""";
    private static final Map<String, String> STOCK_SORTS = Map.of("onHand", "x.on_hand", "firstReceivedAt", "x.first_received_at",
            "lastMovementAt", "x.last_movement_at", "sellerName", "seller_name", "skuCode", "k.code");
    private static final Map<String, String> MOVEMENT_SORTS = Map.of("createdAt", "m.created_at", "quantity", "m.quantity");
    private static final Map<String, String> DOCUMENT_SORTS = Map.of("createdAt", "d.created_at", "units", "d.units");

    private final NamedParameterJdbcTemplate jdbc;

    public ConsignmentJdbcStore(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    // ── Referencias ───────────────────────────────────────────────────────

    @Override
    public Optional<SellerRef> seller(long companyId, long sellerId) {
        return jdbc.query("SELECT s.id, " + SELLER_NAME + " AS name, s.kind, s.status FROM mp_sellers s"
                        + " WHERE s.id = :id AND s.company_id = :company",
                new MapSqlParameterSource("id", sellerId).addValue("company", companyId),
                (rs, i) -> new SellerRef(rs.getLong("id"), rs.getString("name"), rs.getString("kind"), rs.getString("status")))
                .stream().findFirst();
    }

    @Override
    public Optional<String> activeWarehouse(long warehouseId) {
        return jdbc.query("SELECT name FROM warehouses WHERE id_warehouse = :id AND deleted = 0 AND active = 1",
                new MapSqlParameterSource("id", warehouseId), (rs, i) -> rs.getString(1)).stream().findFirst();
    }

    @Override
    public Map<Long, String> skuCodes(Collection<Long> skuIds) {
        Map<Long, String> codes = new LinkedHashMap<>();
        if (skuIds.isEmpty()) {
            return codes;
        }
        jdbc.query("SELECT id_sku, code FROM product_skus WHERE id_sku IN (:ids) AND deleted = 0",
                new MapSqlParameterSource("ids", skuIds), rs -> {
                    codes.put(rs.getLong(1), rs.getString(2));
                });
        return codes;
    }

    @Override
    public Optional<OfferRef> offer(long offerId) {
        return jdbc.query("SELECT * FROM mp_offers WHERE id = :id", new MapSqlParameterSource("id", offerId),
                (rs, i) -> offerRef(rs)).stream().findFirst();
    }

    @Override
    public Optional<Long> lineOffer(long orderLineId) {
        return jdbc.query("SELECT offer_id FROM product_orders WHERE id_product_order = :id AND offer_id IS NOT NULL",
                new MapSqlParameterSource("id", orderLineId), (rs, i) -> rs.getLong(1)).stream().findFirst();
    }

    @Override
    public Optional<OfferRef> lockOpenPlatformOffer(long companyId, long sellerId, long skuId) {
        return jdbc.query("""
                SELECT * FROM mp_offers WHERE company_id = :company AND seller_id = :seller AND sku_id = :sku
                  AND status <> 'CLOSED' AND fulfillment = 'PLATFORM' FOR UPDATE""",
                new MapSqlParameterSource("company", companyId).addValue("seller", sellerId).addValue("sku", skuId),
                (rs, i) -> offerRef(rs)).stream().findFirst();
    }

    @Override
    public boolean adjustOfferAvailable(long offerId, int delta, Instant now) {
        // lock_version sube: una edición con datos viejos no pisa el disponible que dejó la consignación.
        return jdbc.update("""
                UPDATE mp_offers SET available_quantity = available_quantity + :delta, lock_version = lock_version + 1,
                  updated_at = :now
                WHERE id = :id AND available_quantity + :delta >= 0""",
                new MapSqlParameterSource("delta", delta).addValue("now", utc(now)).addValue("id", offerId)) == 1;
    }

    @Override
    public int activeReserved(long companyId, long sellerId, long skuId) {
        Integer value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(r.quantity), 0) FROM mp_offer_reservations r JOIN mp_offers f ON f.id = r.offer_id
                WHERE f.company_id = :company AND f.seller_id = :seller AND f.sku_id = :sku AND f.fulfillment = 'PLATFORM'
                  AND r.status = 'ACTIVE'""",
                new MapSqlParameterSource("company", companyId).addValue("seller", sellerId).addValue("sku", skuId), Integer.class);
        return value == null ? 0 : value;
    }

    // ── Saldos ────────────────────────────────────────────────────────────

    @Override
    public Bin lockOrCreate(long companyId, long sellerId, long skuId, long warehouseId, Instant now) {
        MapSqlParameterSource params = new MapSqlParameterSource("company", companyId).addValue("seller", sellerId)
                .addValue("sku", skuId).addValue("warehouse", warehouseId);
        // INSERT IGNORE deja una sola fila aunque dos transacciones lleguen a la vez (llave única).
        jdbc.update("""
                INSERT IGNORE INTO mp_consignment_stock (company_id, seller_id, sku_id, warehouse_id, on_hand, version)
                VALUES (:company, :seller, :sku, :warehouse, 0, 0)""", params);
        return jdbc.query("""
                SELECT id, warehouse_id, on_hand FROM mp_consignment_stock
                WHERE company_id = :company AND seller_id = :seller AND sku_id = :sku AND warehouse_id = :warehouse FOR UPDATE""",
                params, (rs, i) -> new Bin(rs.getLong("id"), rs.getLong("warehouse_id"), rs.getInt("on_hand"))).get(0);
    }

    @Override
    public List<Bin> lockBins(long companyId, long sellerId, long skuId) {
        return jdbc.query("""
                SELECT id, warehouse_id, on_hand FROM mp_consignment_stock
                WHERE company_id = :company AND seller_id = :seller AND sku_id = :sku ORDER BY id FOR UPDATE""",
                new MapSqlParameterSource("company", companyId).addValue("seller", sellerId).addValue("sku", skuId),
                (rs, i) -> new Bin(rs.getLong("id"), rs.getLong("warehouse_id"), rs.getInt("on_hand")));
    }

    @Override
    public int addToStock(long stockId, int delta, Instant now) {
        int updated = jdbc.update("""
                UPDATE mp_consignment_stock SET on_hand = on_hand + :delta, version = version + 1, last_movement_at = :now,
                  first_received_at = CASE WHEN :delta > 0 AND (first_received_at IS NULL OR on_hand = 0) THEN :now
                                           ELSE first_received_at END
                WHERE id = :id AND on_hand + :delta >= 0""",
                new MapSqlParameterSource("delta", delta).addValue("now", utc(now)).addValue("id", stockId));
        if (updated != 1) {
            throw new com.essenza.draco.shared.exceptions.ConflictException("El saldo de consignación " + stockId
                    + " no alcanza para este movimiento.");
        }
        Integer balance = jdbc.queryForObject("SELECT on_hand FROM mp_consignment_stock WHERE id = :id",
                new MapSqlParameterSource("id", stockId), Integer.class);
        return balance == null ? 0 : balance;
    }

    // ── Documentos y kardex ───────────────────────────────────────────────

    @Override
    public long insertDocument(long companyId, String kind, long sellerId, long warehouseId, String reference, String notes,
                               int units, String actorId, String actorName, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO mp_consignment_documents (company_id, kind, seller_id, warehouse_id, reference, notes, units,
                  created_by, created_by_name, created_at)
                VALUES (:company, :kind, :seller, :warehouse, :reference, :notes, :units, :actor, :actorName, :now)""",
                new MapSqlParameterSource("company", companyId).addValue("kind", kind).addValue("seller", sellerId)
                        .addValue("warehouse", warehouseId).addValue("reference", reference).addValue("notes", notes)
                        .addValue("units", units).addValue("actor", actorId).addValue("actorName", actorName)
                        .addValue("now", utc(now)), keys, new String[] {"id"});
        return keys.getKey().longValue();
    }

    @Override
    public void setDocumentNumber(long documentId, String number) {
        jdbc.update("UPDATE mp_consignment_documents SET document_number = :number WHERE id = :id",
                new MapSqlParameterSource("number", number).addValue("id", documentId));
    }

    @Override
    public void insertMovement(NewMovement m) {
        jdbc.update("""
                INSERT INTO mp_consignment_movements (company_id, stock_id, seller_id, sku_id, warehouse_id, kind, quantity,
                  balance_after, document_id, order_id, order_line_id, return_case_id, note, created_by, created_at)
                VALUES (:company, :stock, :seller, :sku, :warehouse, :kind, :qty, :balance, :document, :order, :line, :returnCase,
                  :note, :actor, :now)""",
                new MapSqlParameterSource("company", m.companyId()).addValue("stock", m.stockId()).addValue("seller", m.sellerId())
                        .addValue("sku", m.skuId()).addValue("warehouse", m.warehouseId()).addValue("kind", m.kind())
                        .addValue("qty", m.quantity()).addValue("balance", m.balanceAfter()).addValue("document", m.documentId())
                        .addValue("order", m.orderId()).addValue("line", m.orderLineId()).addValue("returnCase", m.returnCaseId())
                        .addValue("note", m.note()).addValue("actor", m.actorId()).addValue("now", utc(m.now())));
    }

    @Override
    public Optional<LineSale> lineSale(long orderLineId) {
        return jdbc.query("""
                SELECT l.order_id, o.order_number, l.total, l.quantity,
                       (SELECT CASE WHEN COUNT(*) > 0 AND SUM(f.fulfillment = 'PLATFORM') = COUNT(*) THEN 1 ELSE 0 END
                        FROM product_orders a LEFT JOIN mp_offers f ON f.id = a.offer_id
                        WHERE a.order_id = l.order_id AND COALESCE(a.deleted, 0) = 0) AS store_collects
                FROM product_orders l JOIN orders o ON o.id_order = l.order_id WHERE l.id_product_order = :id""",
                new MapSqlParameterSource("id", orderLineId),
                (rs, i) -> new LineSale(rs.getLong("order_id"), rs.getString("order_number"),
                        rs.getBigDecimal("total") == null ? java.math.BigDecimal.ZERO : rs.getBigDecimal("total"),
                        rs.getInt("quantity"), rs.getInt("store_collects") == 1)).stream().findFirst();
    }

    @Override
    public boolean ledgerKeyExists(long companyId, String idempotencyKey) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM mp_seller_ledger WHERE company_id = :company AND idempotency_key = :key",
                new MapSqlParameterSource("company", companyId).addValue("key", idempotencyKey), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public int unitsBefore(long companyId, long sellerId, Instant before) {
        Integer value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(quantity), 0) FROM mp_consignment_movements
                WHERE company_id = :company AND seller_id = :seller AND created_at < :before""",
                new MapSqlParameterSource("company", companyId).addValue("seller", sellerId).addValue("before", utc(before)),
                Integer.class);
        return value == null ? 0 : value;
    }

    @Override
    public List<Delta> deltas(long companyId, long sellerId, Instant from, Instant to) {
        return jdbc.query("""
                SELECT created_at, quantity FROM mp_consignment_movements
                WHERE company_id = :company AND seller_id = :seller AND created_at >= :from AND created_at < :to
                ORDER BY created_at, id""",
                new MapSqlParameterSource("company", companyId).addValue("seller", sellerId).addValue("from", utc(from))
                        .addValue("to", utc(to)),
                (rs, i) -> new Delta(instant(rs, "created_at"), rs.getInt("quantity")));
    }

    @Override
    public Map<Long, Integer> soldByStock(long orderLineId) {
        Map<Long, Integer> sold = new LinkedHashMap<>();
        jdbc.query("""
                SELECT stock_id, -SUM(quantity) AS sold FROM mp_consignment_movements
                WHERE order_line_id = :line AND kind IN ('SALE', 'SALE_CANCEL') GROUP BY stock_id ORDER BY stock_id""",
                new MapSqlParameterSource("line", orderLineId), rs -> {
                    sold.put(rs.getLong("stock_id"), rs.getInt("sold"));
                });
        return sold;
    }

    // ── Consultas ─────────────────────────────────────────────────────────

    @Override
    public Page<StockRowDto> searchStock(long companyId, StockFilter filter, Instant now, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource("company", companyId).addValue("now", utc(now));
        StringBuilder where = new StringBuilder(" WHERE x.company_id = :company");
        if (filter.onlyWithStock()) {
            where.append(" AND x.on_hand > 0");
        }
        if (filter.sellerId() != null) {
            where.append(" AND x.seller_id = :seller");
            params.addValue("seller", filter.sellerId());
        }
        if (filter.warehouseId() != null) {
            where.append(" AND x.warehouse_id = :warehouse");
            params.addValue("warehouse", filter.warehouseId());
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            where.append(" AND (LOWER(k.code) LIKE :q OR LOWER(COALESCE(p.name, '')) LIKE :q OR LOWER(" + SELLER_NAME + ") LIKE :q)");
            params.addValue("q", like(filter.q()));
        }
        String from = """
                 FROM mp_consignment_stock x
                 JOIN mp_sellers s ON s.id = x.seller_id
                 JOIN product_skus k ON k.id_sku = x.sku_id
                 LEFT JOIN products p ON p.id_product = k.product_id
                 LEFT JOIN warehouses w ON w.id_warehouse = x.warehouse_id
                 LEFT JOIN mp_offers f ON f.company_id = x.company_id AND f.seller_id = x.seller_id AND f.sku_id = x.sku_id
                   AND f.status <> 'CLOSED'""";
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, params, Long.class);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<StockRowDto> rows = jdbc.query("SELECT x.*, " + SELLER_NAME + " AS seller_name, k.code AS sku_code, p.name AS product_name,"
                        + " w.name AS warehouse_name, f.id AS offer_id, f.status AS offer_status, f.fulfillment AS offer_fulfillment,"
                        + " " + RESERVED + " AS reserved,"
                        + " (SELECT SUM(t.on_hand) FROM mp_consignment_stock t WHERE t.company_id = x.company_id"
                        + "   AND t.seller_id = x.seller_id AND t.sku_id = x.sku_id) AS seller_on_hand,"
                        + " DATEDIFF(:now, x.first_received_at) AS days_in_custody "
                        + from + where + orderBy(pageable, STOCK_SORTS, "x.id DESC") + " LIMIT :limit OFFSET :offset",
                params, (rs, i) -> {
                    int reserved = rs.getInt("reserved");
                    long days = rs.getLong("days_in_custody");
                    Long custody = rs.wasNull() || rs.getInt("on_hand") == 0 ? null : days;
                    long offer = rs.getLong("offer_id");
                    Long offerId = rs.wasNull() ? null : offer;
                    return new StockRowDto(rs.getLong("id"), rs.getLong("seller_id"), rs.getString("seller_name"),
                            rs.getLong("sku_id"), rs.getString("sku_code"), rs.getString("product_name"),
                            rs.getLong("warehouse_id"), rs.getString("warehouse_name"), rs.getInt("on_hand"), reserved,
                            Math.max(0, rs.getInt("seller_on_hand") - reserved), instant(rs, "first_received_at"),
                            instant(rs, "last_movement_at"), custody, offerId, rs.getString("offer_status"),
                            rs.getString("offer_fulfillment"));
                });
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    @Override
    public StockStatsDto stats(long companyId) {
        return jdbc.query("""
                SELECT COALESCE(SUM(x.on_hand), 0) AS units, COUNT(DISTINCT CASE WHEN x.on_hand > 0 THEN x.sku_id END) AS skus,
                       COUNT(DISTINCT CASE WHEN x.on_hand > 0 THEN x.seller_id END) AS sellers,
                       COUNT(DISTINCT CASE WHEN x.on_hand > 0 THEN x.warehouse_id END) AS warehouses,
                       (SELECT COALESCE(SUM(r.quantity), 0) FROM mp_offer_reservations r JOIN mp_offers f ON f.id = r.offer_id
                        WHERE f.company_id = :company AND f.fulfillment = 'PLATFORM' AND r.status = 'ACTIVE') AS reserved,
                       COUNT(DISTINCT CASE WHEN x.on_hand > 0 AND NOT EXISTS (
                           SELECT 1 FROM mp_offers f WHERE f.company_id = x.company_id AND f.seller_id = x.seller_id
                             AND f.sku_id = x.sku_id AND f.status <> 'CLOSED' AND f.fulfillment = 'PLATFORM')
                         THEN CONCAT(x.seller_id, ':', x.sku_id) END) AS without_offer
                FROM mp_consignment_stock x WHERE x.company_id = :company""",
                new MapSqlParameterSource("company", companyId),
                (rs, i) -> new StockStatsDto(rs.getLong("units"), rs.getLong("skus"), rs.getLong("sellers"),
                        rs.getLong("warehouses"), rs.getLong("reserved"), rs.getLong("without_offer"))).get(0);
    }

    private static final String MOVEMENT_SELECT = """
            SELECT m.*, %s AS seller_name, k.code AS sku_code, p.name AS product_name, w.name AS warehouse_name,
                   d.document_number, o.order_number
            FROM mp_consignment_movements m
            JOIN mp_sellers s ON s.id = m.seller_id
            JOIN product_skus k ON k.id_sku = m.sku_id
            LEFT JOIN products p ON p.id_product = k.product_id
            LEFT JOIN warehouses w ON w.id_warehouse = m.warehouse_id
            LEFT JOIN mp_consignment_documents d ON d.id = m.document_id
            LEFT JOIN orders o ON o.id_order = m.order_id
            WHERE m.company_id = :company""".formatted(SELLER_NAME);

    @Override
    public Page<MovementDto> movements(long companyId, MovementFilter filter, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource("company", companyId);
        StringBuilder where = new StringBuilder();
        if (filter.sellerId() != null) {
            where.append(" AND m.seller_id = :seller");
            params.addValue("seller", filter.sellerId());
        }
        if (filter.skuId() != null) {
            where.append(" AND m.sku_id = :sku");
            params.addValue("sku", filter.skuId());
        }
        if (filter.warehouseId() != null) {
            where.append(" AND m.warehouse_id = :warehouse");
            params.addValue("warehouse", filter.warehouseId());
        }
        if (filter.orderId() != null) {
            where.append(" AND m.order_id = :order");
            params.addValue("order", filter.orderId());
        }
        if (filter.kind() != null && !filter.kind().isBlank()) {
            where.append(" AND m.kind = :kind");
            params.addValue("kind", filter.kind().trim().toUpperCase(Locale.ROOT));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM mp_consignment_movements m WHERE m.company_id = :company" + where,
                params, Long.class);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<MovementDto> rows = jdbc.query(MOVEMENT_SELECT + where + orderBy(pageable, MOVEMENT_SORTS, "m.id DESC")
                + " LIMIT :limit OFFSET :offset", params, (rs, i) -> movement(rs));
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    private static final String DOCUMENT_SELECT = """
            SELECT d.*, %s AS seller_name, w.name AS warehouse_name
            FROM mp_consignment_documents d
            JOIN mp_sellers s ON s.id = d.seller_id
            LEFT JOIN warehouses w ON w.id_warehouse = d.warehouse_id
            WHERE d.company_id = :company""".formatted(SELLER_NAME);

    @Override
    public Page<DocumentDto> documents(long companyId, Long sellerId, String kind, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource("company", companyId);
        StringBuilder where = new StringBuilder();
        if (sellerId != null) {
            where.append(" AND d.seller_id = :seller");
            params.addValue("seller", sellerId);
        }
        if (kind != null) {
            where.append(" AND d.kind = :kind");
            params.addValue("kind", kind);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM mp_consignment_documents d WHERE d.company_id = :company" + where,
                params, Long.class);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<DocumentDto> rows = jdbc.query(DOCUMENT_SELECT + where + orderBy(pageable, DOCUMENT_SORTS, "d.id DESC")
                + " LIMIT :limit OFFSET :offset", params, (rs, i) -> document(rs));
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    @Override
    public Optional<DocumentDto> document(long companyId, long documentId) {
        return jdbc.query(DOCUMENT_SELECT + " AND d.id = :id", new MapSqlParameterSource("company", companyId)
                .addValue("id", documentId), (rs, i) -> document(rs)).stream().findFirst();
    }

    @Override
    public List<MovementDto> documentLines(long companyId, long documentId) {
        return jdbc.query(MOVEMENT_SELECT + " AND m.document_id = :document ORDER BY m.id",
                new MapSqlParameterSource("company", companyId).addValue("document", documentId), (rs, i) -> movement(rs));
    }

    @Override
    public List<OptionDto> sellerOptions(long companyId) {
        return jdbc.query("SELECT s.id, " + SELLER_NAME + " AS name FROM mp_sellers s WHERE s.company_id = :company"
                        + " AND s.kind = 'EXTERNAL' AND (s.status = 'APPROVED' OR EXISTS (SELECT 1 FROM mp_consignment_stock x"
                        + " WHERE x.seller_id = s.id AND x.on_hand > 0)) ORDER BY name LIMIT 500",
                new MapSqlParameterSource("company", companyId), (rs, i) -> new OptionDto(rs.getLong("id"), rs.getString("name")));
    }

    @Override
    public List<OptionDto> warehouseOptions() {
        return jdbc.query("SELECT id_warehouse, name FROM warehouses WHERE deleted = 0 AND active = 1 ORDER BY name LIMIT 200",
                new MapSqlParameterSource(), (rs, i) -> new OptionDto(rs.getLong(1), rs.getString(2)));
    }

    // ── Mapeo ─────────────────────────────────────────────────────────────

    private static OfferRef offerRef(ResultSet rs) throws SQLException {
        return new OfferRef(rs.getLong("id"), rs.getLong("company_id"), rs.getLong("seller_id"), rs.getLong("sku_id"),
                rs.getString("fulfillment"), rs.getString("status"), rs.getInt("available_quantity"));
    }

    private static MovementDto movement(ResultSet rs) throws SQLException {
        return new MovementDto(rs.getLong("id"), rs.getString("kind"), rs.getInt("quantity"), rs.getInt("balance_after"),
                rs.getLong("seller_id"), rs.getString("seller_name"), rs.getLong("sku_id"), rs.getString("sku_code"),
                rs.getString("product_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
                longOrNull(rs, "document_id"), rs.getString("document_number"), longOrNull(rs, "order_id"),
                rs.getString("order_number"), longOrNull(rs, "return_case_id"), rs.getString("note"), rs.getString("created_by"),
                instant(rs, "created_at"));
    }

    private static DocumentDto document(ResultSet rs) throws SQLException {
        return new DocumentDto(rs.getLong("id"), rs.getString("document_number"), rs.getString("kind"), rs.getLong("seller_id"),
                rs.getString("seller_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"), rs.getString("reference"),
                rs.getString("notes"), rs.getInt("units"), rs.getString("created_by"), rs.getString("created_by_name"),
                instant(rs, "created_at"), null);
    }

    private static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
