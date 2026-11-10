package com.essenza.draco.modules.sales.infrastructure.outbound.repositories.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.sales.application.output.repository.CheckoutGroupStore;

/** Grupos de pago de una compra (JDBC, misma transacción que los pagos). */
@Repository
public class CheckoutGroupJdbcStore implements CheckoutGroupStore {

    private final NamedParameterJdbcTemplate jdbc;

    public CheckoutGroupJdbcStore(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public void create(String reference, long companyId, long purchaseId, String provider, BigDecimal amount, List<Long> paymentIds,
                       String actorId, Instant now) {
        jdbc.update("""
                INSERT INTO payment_checkout_groups (reference, company_id, purchase_id, provider, amount, created_by, created_at)
                VALUES (:ref, :company, :purchase, :provider, :amount, :actor, :now)""",
                new MapSqlParameterSource("ref", reference).addValue("company", companyId).addValue("purchase", purchaseId)
                        .addValue("provider", provider).addValue("amount", amount).addValue("actor", actorId)
                        .addValue("now", LocalDateTime.ofInstant(now, ZoneOffset.UTC)));
        for (Long paymentId : paymentIds) {
            jdbc.update("INSERT INTO payment_checkout_group_items (reference, payment_id) VALUES (:ref, :payment)",
                    new MapSqlParameterSource("ref", reference).addValue("payment", paymentId));
        }
    }

    @Override
    public Optional<Group> byReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query("SELECT reference, company_id, purchase_id, provider, amount FROM payment_checkout_groups WHERE reference = :ref",
                new MapSqlParameterSource("ref", reference), (rs, i) -> new Group(rs.getString(1), rs.getLong(2), rs.getLong(3),
                        rs.getString(4), rs.getBigDecimal(5), items(rs.getString(1)))).stream().findFirst();
    }

    @Override
    public Optional<Group> byPayment(long paymentId) {
        return jdbc.query("SELECT reference FROM payment_checkout_group_items WHERE payment_id = :id",
                new MapSqlParameterSource("id", paymentId), (rs, i) -> rs.getString(1)).stream().findFirst().flatMap(this::byReference);
    }

    private List<Long> items(String reference) {
        return jdbc.queryForList("SELECT payment_id FROM payment_checkout_group_items WHERE reference = :ref ORDER BY payment_id",
                new MapSqlParameterSource("ref", reference), Long.class);
    }
}
