package com.essenza.draco.modules.inventory.infrastructure.outbound.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.inventory.application.output.repository.SkuCostStore;

/** Costo promedio por SKU en MySQL (JDBC, misma transacción que la recepción). */
@Repository
public class SkuCostJdbcStore implements SkuCostStore {

    private final NamedParameterJdbcTemplate jdbc;

    public SkuCostJdbcStore(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public CostRow lock(long skuId, long productId, Instant now) {
        MapSqlParameterSource params = new MapSqlParameterSource("sku", skuId).addValue("product", productId).addValue("now", utc(now));
        jdbc.update("INSERT IGNORE INTO inv_sku_costs (sku_id, product_id, updated_at) VALUES (:sku, :product, :now)", params);
        return jdbc.queryForObject("""
                SELECT c.sku_id, c.avg_cost, c.last_cost, k.cost_price
                FROM inv_sku_costs c LEFT JOIN product_skus k ON k.id_sku = c.sku_id
                WHERE c.sku_id = :sku FOR UPDATE""", params,
                (rs, i) -> new CostRow(rs.getLong(1), rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getBigDecimal(4)));
    }

    @Override
    public long unitsOnHand(long skuId) {
        Long units = jdbc.queryForObject("SELECT COALESCE(SUM(on_hand), 0) FROM stock_items WHERE sku_id = :sku AND deleted = 0",
                new MapSqlParameterSource("sku", skuId), Long.class);
        return units == null ? 0 : units;
    }

    @Override
    public void save(long skuId, BigDecimal averageCost, BigDecimal lastCost, long receiptId, Instant now) {
        jdbc.update("""
                UPDATE inv_sku_costs SET avg_cost = :avg, last_cost = :last, last_receipt_id = :receipt, updated_at = :now
                WHERE sku_id = :sku""", new MapSqlParameterSource("sku", skuId).addValue("avg", averageCost).addValue("last", lastCost)
                .addValue("receipt", receiptId).addValue("now", utc(now)));
    }

    @Override
    public void addHistory(long skuId, long receiptId, long unitsBefore, BigDecimal averageBefore, int quantity, BigDecimal unitCost,
                           BigDecimal averageAfter, Instant now) {
        jdbc.update("""
                INSERT INTO inv_sku_cost_history (sku_id, receipt_id, units_before, avg_before, quantity, unit_cost, avg_after, created_at)
                VALUES (:sku, :receipt, :units, :before, :qty, :cost, :after, :now)""",
                new MapSqlParameterSource("sku", skuId).addValue("receipt", receiptId).addValue("units", unitsBefore)
                        .addValue("before", averageBefore).addValue("qty", quantity).addValue("cost", unitCost)
                        .addValue("after", averageAfter).addValue("now", utc(now)));
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
