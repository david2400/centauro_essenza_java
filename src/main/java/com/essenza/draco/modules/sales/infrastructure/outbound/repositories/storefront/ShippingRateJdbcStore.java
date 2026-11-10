package com.essenza.draco.modules.sales.infrastructure.outbound.repositories.storefront;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.sales.application.output.storefront.ShippingRateStore;
import com.essenza.draco.modules.sales.domain.model.storefront.ShippingRates.Rate;

/** Tarifas de envío en {@code storefront_shipping_rates} (JDBC, filtradas por empresa). */
@Repository
public class ShippingRateJdbcStore implements ShippingRateStore {

    private final NamedParameterJdbcTemplate jdbc;

    public ShippingRateJdbcStore(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public List<Rate> rates(long companyId) {
        return jdbc.query("SELECT * FROM storefront_shipping_rates WHERE company_id = :c ORDER BY state_key, city_key, id",
                new MapSqlParameterSource("c", companyId), (rs, i) -> rate(rs));
    }

    @Override
    public Optional<Rate> rate(long companyId, long id) {
        return jdbc.query("SELECT * FROM storefront_shipping_rates WHERE company_id = :c AND id = :id",
                new MapSqlParameterSource("c", companyId).addValue("id", id), (rs, i) -> rate(rs)).stream().findFirst();
    }

    @Override
    public boolean exists(long companyId, String stateKey, String cityKey, Long exceptId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM storefront_shipping_rates
                 WHERE company_id = :c AND state_key = :s AND city_key = :ci AND id <> :except""",
                new MapSqlParameterSource("c", companyId).addValue("s", stateKey).addValue("ci", cityKey)
                        .addValue("except", exceptId == null ? -1 : exceptId), Integer.class);
        return n != null && n > 0;
    }

    @Override
    public long insert(long companyId, Rate r, String actorId, String actorName, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO storefront_shipping_rates (company_id, state, city, state_key, city_key, amount, free_from, min_days,
                  max_days, active, updated_by, updated_by_name, updated_at)
                VALUES (:c, :state, :city, :sk, :ck, :amount, :free, :min, :max, :active, :actor, :actorName, :now)""",
                params(companyId, r, actorId, actorName, now), keys, new String[] {"id"});
        return keys.getKey().longValue();
    }

    @Override
    public void update(long companyId, long id, Rate r, String actorId, String actorName, Instant now) {
        jdbc.update("""
                UPDATE storefront_shipping_rates SET state = :state, city = :city, state_key = :sk, city_key = :ck, amount = :amount,
                  free_from = :free, min_days = :min, max_days = :max, active = :active, updated_by = :actor,
                  updated_by_name = :actorName, updated_at = :now
                WHERE company_id = :c AND id = :id""", params(companyId, r, actorId, actorName, now).addValue("id", id));
    }

    @Override
    public Map<Long, Audit> audits(long companyId) {
        Map<Long, Audit> result = new LinkedHashMap<>();
        jdbc.query("SELECT id, updated_by_name, updated_at FROM storefront_shipping_rates WHERE company_id = :c",
                new MapSqlParameterSource("c", companyId), rs -> {
                    Timestamp ts = rs.getTimestamp(3);
                    result.put(rs.getLong(1), new Audit(rs.getString(2), ts == null ? null : ts.toLocalDateTime().toInstant(ZoneOffset.UTC)));
                });
        return result;
    }

    private static MapSqlParameterSource params(long companyId, Rate r, String actorId, String actorName, Instant now) {
        return new MapSqlParameterSource("c", companyId).addValue("state", r.state()).addValue("city", r.city())
                .addValue("sk", r.stateKey()).addValue("ck", r.cityKey()).addValue("amount", r.amount()).addValue("free", r.freeFrom())
                .addValue("min", r.minDays()).addValue("max", r.maxDays()).addValue("active", r.active() ? 1 : 0)
                .addValue("actor", actorId).addValue("actorName", actorName)
                .addValue("now", LocalDateTime.ofInstant(now, ZoneOffset.UTC));
    }

    private static Rate rate(ResultSet rs) throws SQLException {
        return new Rate(rs.getLong("id"), rs.getString("state"), rs.getString("city"), rs.getBigDecimal("amount"),
                rs.getBigDecimal("free_from"), (Integer) rs.getObject("min_days", Integer.class),
                (Integer) rs.getObject("max_days", Integer.class), rs.getInt("active") == 1);
    }
}
