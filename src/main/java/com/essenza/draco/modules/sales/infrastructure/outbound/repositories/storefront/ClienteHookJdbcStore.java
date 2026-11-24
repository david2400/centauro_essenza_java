package com.essenza.draco.modules.sales.infrastructure.outbound.repositories.storefront;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.sales.application.output.storefront.ClienteHookOutbox;

/** Outbox {@code cliente_hooks} (JDBC, misma base que las órdenes: entra en la transacción del pago). */
@Repository
public class ClienteHookJdbcStore implements ClienteHookOutbox {

    private final NamedParameterJdbcTemplate jdbc;

    public ClienteHookJdbcStore(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public void enqueue(long orderId, String event, Instant now) {
        // Solo compras de la tienda pública (tienen enlace de seguimiento); el resto (mostrador, panel) no se avisa.
        jdbc.update("""
                INSERT INTO cliente_hooks (company_id, event, event_key, purchase_number, order_number, tracking_number, carrier_name,
                                           status, attempts, next_attempt_at, created_at)
                SELECT p.company_id, :event, :key, p.purchase_number, o.order_number, o.tracking_number, LEFT(c.name, 120),
                       'PENDING', 0, :now, :now
                  FROM orders o
                  JOIN purchases p ON p.id_purchase = o.purchase_id
                  JOIN storefront_access a ON a.purchase_id = p.id_purchase
                  LEFT JOIN carriers c ON c.id_carrier = o.carrier_id
                 WHERE o.id_order = :order AND p.purchase_number IS NOT NULL
                ON DUPLICATE KEY UPDATE event_key = event_key""",
                new MapSqlParameterSource("event", event).addValue("key", event + ":" + orderId)
                        .addValue("order", orderId).addValue("now", utc(now)));
    }

    @Override
    public List<Hook> due(Instant now, int limit) {
        return jdbc.query("""
                SELECT id, event, event_key, purchase_number, order_number, tracking_number, carrier_name, attempts, next_attempt_at
                  FROM cliente_hooks
                 WHERE status = 'PENDING' AND next_attempt_at <= :now ORDER BY next_attempt_at, id LIMIT :limit""",
                new MapSqlParameterSource("now", utc(now)).addValue("limit", Math.max(1, Math.min(limit, 500))),
                (rs, i) -> new Hook(rs.getLong("id"), rs.getString("event"), rs.getString("event_key"), rs.getString("purchase_number"),
                        rs.getString("order_number"), rs.getString("tracking_number"), rs.getString("carrier_name"), rs.getInt("attempts"), rs.getObject("next_attempt_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)));
    }

    @Override
    public boolean claim(Hook hook, Instant leaseUntil) {
        return jdbc.update("""
                UPDATE cliente_hooks SET next_attempt_at = :lease
                 WHERE id = :id AND status = 'PENDING' AND next_attempt_at = :seen""",
                new MapSqlParameterSource("lease", utc(leaseUntil)).addValue("id", hook.id())
                        .addValue("seen", utc(hook.nextAttemptAt()))) == 1;
    }

    @Override
    public void markSent(long id, Instant now) {
        jdbc.update("UPDATE cliente_hooks SET status = 'SENT', sent_at = :now, attempts = attempts + 1, last_error = NULL WHERE id = :id",
                new MapSqlParameterSource("now", utc(now)).addValue("id", id));
    }

    @Override
    public void markFailed(long id, String error, Instant nextAttemptAt) {
        jdbc.update("""
                UPDATE cliente_hooks SET attempts = attempts + 1, last_error = :error,
                       status = CASE WHEN :next IS NULL THEN 'FAILED' ELSE 'PENDING' END,
                       next_attempt_at = COALESCE(:next, next_attempt_at)
                 WHERE id = :id""",
                new MapSqlParameterSource("error", error).addValue("next", nextAttemptAt == null ? null : utc(nextAttemptAt))
                        .addValue("id", id));
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
