package com.essenza.draco.modules.inventory.infrastructure.outbound.repositories.warehouse;

import com.essenza.draco.modules.inventory.application.output.repository.WarehouseInsights;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Consultas de reporte (SQL nativo, solo lectura) sobre las existencias, el costo de los SKU, los
 * movimientos y las tiendas o cajas que venden desde una bodega. Lee tablas de catálogo y ventas
 * sin modificarlas, igual que los lookups.
 */
@Component
public class WarehouseInsightsAdapter implements WarehouseInsights {

    /** Una fila por SKU vigente en la bodega; valor = físicas × costo promedio (V27) o, si no lo hay, el del catálogo. */
    private static final String STOCK_BASE = """
              FROM stock_items i
              JOIN product_skus k ON k.id_sku = i.sku_id AND k.deleted = 0
              LEFT JOIN inv_sku_costs c ON c.sku_id = i.sku_id
              LEFT JOIN products p ON p.id_product = i.product_id
             WHERE i.deleted = 0 AND i.warehouse_id = :wid
            """;
    private static final String BELOW_MIN = "i.min_threshold > 0 AND (i.on_hand - i.reserved) <= i.min_threshold";
    private static final Map<String, String> STOCK_SORT = Map.of(
            "skuCode", "k.code", "productName", "p.name", "onHand", "i.on_hand", "reserved", "i.reserved",
            "available", "(i.on_hand - i.reserved)", "minThreshold", "i.min_threshold",
            "stockValue", "(i.on_hand * COALESCE(c.avg_cost, k.cost_price, 0))");

    private final EntityManager em;

    public WarehouseInsightsAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<Long, StockSummary> summaries(Collection<Long> warehouseIds) {
        if (warehouseIds == null || warehouseIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT i.warehouse_id, COUNT(*), COALESCE(SUM(i.on_hand), 0), COALESCE(SUM(i.reserved), 0),
                               COALESCE(SUM(i.on_hand * COALESCE(c.avg_cost, k.cost_price, 0)), 0),
                               COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0)
                          FROM stock_items i
                          JOIN product_skus k ON k.id_sku = i.sku_id AND k.deleted = 0
                          LEFT JOIN inv_sku_costs c ON c.sku_id = i.sku_id
                         WHERE i.deleted = 0 AND i.warehouse_id IN (:ids)
                         GROUP BY i.warehouse_id""".formatted(BELOW_MIN))
                .setParameter("ids", warehouseIds).getResultList();
        Map<Long, StockSummary> out = new HashMap<>();
        rows.forEach(r -> out.put(lng(r[0]), summary(r, 1)));
        return out;
    }

    @Override
    public StockSummary totals() {
        Object[] r = (Object[]) em.createNativeQuery("""
                SELECT COUNT(*), COALESCE(SUM(i.on_hand), 0), COALESCE(SUM(i.reserved), 0),
                       COALESCE(SUM(i.on_hand * COALESCE(c.avg_cost, k.cost_price, 0)), 0),
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0)
                  FROM stock_items i
                  JOIN product_skus k ON k.id_sku = i.sku_id AND k.deleted = 0
                  LEFT JOIN inv_sku_costs c ON c.sku_id = i.sku_id
                  JOIN warehouses w ON w.id_warehouse = i.warehouse_id AND w.deleted = 0
                 WHERE i.deleted = 0""".formatted(BELOW_MIN)).getSingleResult();
        return summary(r, 0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Page<StockLine> stock(Long warehouseId, String q, boolean belowMinOnly, Pageable pageable) {
        StringBuilder where = new StringBuilder(STOCK_BASE);
        boolean hasText = q != null && !q.isBlank();
        if (hasText) {
            where.append(" AND (LOWER(k.code) LIKE :q OR LOWER(k.name) LIKE :q OR LOWER(p.name) LIKE :q OR LOWER(k.barcode) LIKE :q)");
        }
        if (belowMinOnly) {
            where.append(" AND ").append(BELOW_MIN);
        }
        String order = pageable.getSort().isSorted() ? pageable.getSort().stream()
                .filter(o -> STOCK_SORT.containsKey(o.getProperty()))
                .map(o -> STOCK_SORT.get(o.getProperty()) + (o.getDirection() == Sort.Direction.DESC ? " DESC" : " ASC"))
                .reduce((a, b) -> a + ", " + b).orElse("p.name, k.name") : "p.name, k.name";
        Query list = em.createNativeQuery("""
                SELECT i.sku_id, i.product_id, k.code, p.name, k.name, i.on_hand, i.reserved, i.min_threshold, COALESCE(c.avg_cost, k.cost_price)
                """ + where + " ORDER BY " + order + ", i.sku_id");
        Query count = em.createNativeQuery("SELECT COUNT(*) " + where);
        for (Query query : List.of(list, count)) {
            query.setParameter("wid", warehouseId);
            if (hasText) {
                query.setParameter("q", "%" + q.trim().toLowerCase(Locale.ROOT) + "%");
            }
        }
        list.setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize());
        List<StockLine> content = ((List<Object[]>) list.getResultList()).stream().map(WarehouseInsightsAdapter::line).toList();
        return new PageImpl<>(content, pageable, lng(count.getSingleResult()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<StockLine> line(Long warehouseId, Long skuId) {
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT i.sku_id, i.product_id, k.code, p.name, k.name, i.on_hand, i.reserved, i.min_threshold, COALESCE(c.avg_cost, k.cost_price)
                        """ + STOCK_BASE + " AND i.sku_id = :sku")
                .setParameter("wid", warehouseId).setParameter("sku", skuId).getResultList();
        return rows.stream().findFirst().map(WarehouseInsightsAdapter::line);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<MovementLine> movements(Long warehouseId, int limit) {
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT m.id_movement, m.type, m.from_warehouse_id, m.to_warehouse_id, m.sku_id, k.code, p.name,
                               m.quantity, wf.name, wt.name, m.reason, m.reference_type, m.reference_id, m.created_at
                          FROM inventory_movements m
                          LEFT JOIN product_skus k ON k.id_sku = m.sku_id
                          LEFT JOIN products p ON p.id_product = m.product_id
                          LEFT JOIN warehouses wf ON wf.id_warehouse = m.from_warehouse_id
                          LEFT JOIN warehouses wt ON wt.id_warehouse = m.to_warehouse_id
                         WHERE m.deleted = 0 AND (m.from_warehouse_id = :wid OR m.to_warehouse_id = :wid)
                         ORDER BY m.id_movement DESC""")
                .setParameter("wid", warehouseId).setMaxResults(limit).getResultList();
        return rows.stream().map(r -> new MovementLine(lng(r[0]), (String) r[1], lng(r[2]), lng(r[3]), lng(r[4]),
                (String) r[5], (String) r[6], r[7] == null ? 0 : ((Number) r[7]).intValue(), (String) r[8], (String) r[9],
                (String) r[10], (String) r[11], lng(r[12]), instant(r[13]))).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<StoreLink> stores(Long warehouseId) {
        if (!tableExists("stores")) {
            return List.of();
        }
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT s.id_store, s.code, s.name, s.active FROM stores s
                         WHERE s.deleted = 0 AND s.warehouse_id = :wid ORDER BY s.name""")
                .setParameter("wid", warehouseId).getResultList();
        return rows.stream().map(r -> new StoreLink(lng(r[0]), (String) r[1], (String) r[2], bool(r[3]))).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RegisterLink> registers(Long warehouseId) {
        if (!tableExists("stores")) {
            return List.of();
        }
        // Caja con bodega propia, o sin bodega propia en una tienda que vende desde esta bodega.
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT c.id_cash_register, c.name, s.name, c.active, c.warehouse_id
                          FROM cash_registers c
                          LEFT JOIN stores s ON s.id_store = c.store_id
                         WHERE c.deleted = 0
                           AND (c.warehouse_id = :wid OR (c.warehouse_id IS NULL AND s.warehouse_id = :wid AND s.deleted = 0))
                         ORDER BY s.name, c.name""")
                .setParameter("wid", warehouseId).getResultList();
        return rows.stream().map(r -> new RegisterLink(lng(r[0]), (String) r[1], (String) r[2], bool(r[3]), r[4] != null))
                .toList();
    }

    @Override
    public long activeStores(Long warehouseId) {
        return activeStoreCounts(List.of(warehouseId)).getOrDefault(warehouseId, 0L);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<Long, Long> activeStoreCounts(Collection<Long> warehouseIds) {
        if (warehouseIds == null || warehouseIds.isEmpty() || !tableExists("stores")) {
            return Map.of();
        }
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT s.warehouse_id, COUNT(*) FROM stores s
                         WHERE s.deleted = 0 AND s.active = 1 AND s.warehouse_id IN (:ids) GROUP BY s.warehouse_id""")
                .setParameter("ids", warehouseIds).getResultList();
        Map<Long, Long> out = new HashMap<>();
        rows.forEach(r -> out.put(lng(r[0]), lng(r[1])));
        return out;
    }

    private boolean tableExists(String table) {
        Number n = (Number) em.createNativeQuery("""
                        SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :t""")
                .setParameter("t", table).getSingleResult();
        return n.intValue() > 0;
    }

    private static StockSummary summary(Object[] r, int from) {
        return new StockSummary(lng(r[from]), lng(r[from + 1]), lng(r[from + 2]), money(r[from + 3]), lng(r[from + 4]));
    }

    private static StockLine line(Object[] r) {
        return new StockLine(lng(r[0]), lng(r[1]), (String) r[2], (String) r[3], (String) r[4], ((Number) r[5]).intValue(),
                ((Number) r[6]).intValue(), ((Number) r[7]).intValue(), r[8] == null ? null : money(r[8]));
    }

    private static Long lng(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static BigDecimal money(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return (value instanceof BigDecimal b ? b : new BigDecimal(value.toString())).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value instanceof Number n && n.intValue() != 0;
    }

    private static Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof Timestamp t) {
            return t.toInstant();
        }
        if (value instanceof LocalDateTime l) {
            return l.atZone(ZoneId.systemDefault()).toInstant();
        }
        return null;
    }
}
