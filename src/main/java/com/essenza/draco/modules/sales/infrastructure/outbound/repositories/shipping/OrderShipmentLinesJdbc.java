package com.essenza.draco.modules.sales.infrastructure.outbound.repositories.shipping;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.sales.application.output.repository.OrderShipmentLines;

/** Líneas vigentes de la orden en {@code product_orders} (JDBC). */
@Repository
public class OrderShipmentLinesJdbc implements OrderShipmentLines {

    private final NamedParameterJdbcTemplate jdbc;

    public OrderShipmentLinesJdbc(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public List<Line> lines(long orderId) {
        return jdbc.query("""
                SELECT sku_id, product_id, quantity FROM product_orders
                 WHERE order_id = :o AND deleted = 0 AND quantity > 0 ORDER BY id_product_order""",
                new MapSqlParameterSource("o", orderId), (rs, i) -> {
                    long sku = rs.getLong(1);
                    Long skuId = rs.wasNull() ? null : sku;
                    long product = rs.getLong(2);
                    return new Line(skuId, rs.wasNull() ? null : product, rs.getInt(3));
                });
    }
}
