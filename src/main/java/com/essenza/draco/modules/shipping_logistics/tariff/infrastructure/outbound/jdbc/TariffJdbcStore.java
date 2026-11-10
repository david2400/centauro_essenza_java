package com.essenza.draco.modules.shipping_logistics.tariff.infrastructure.outbound.jdbc;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.shipping_logistics.tariff.application.output.TariffStore;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.Bracket;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.Destination;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.Place;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.RateCard;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.Zone;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.ZoneRate;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.TariffEngine.Piece;

/**
 * Tarifario en {@code shipping_rate_cards}, {@code shipping_rates}, {@code shipping_rate_brackets},
 * {@code shipping_zones} y {@code shipping_zone_places} (JDBC, filtrado por empresa). Lee {@code carriers},
 * {@code warehouses}, {@code product_skus} y {@code products} solo para nombres, origen y pesos.
 */
@Repository
public class TariffJdbcStore implements TariffStore {

    private final NamedParameterJdbcTemplate jdbc;

    public TariffJdbcStore(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    // ------------------------------------------------------------------------------------------ tarifarios

    @Override
    public List<RateCard> cards(long companyId) {
        return loadCards(companyId, null);
    }

    @Override
    public Optional<RateCard> card(long companyId, long id) {
        return loadCards(companyId, id).stream().findFirst();
    }

    private List<RateCard> loadCards(long companyId, Long onlyId) {
        MapSqlParameterSource p = new MapSqlParameterSource("c", companyId).addValue("id", onlyId);
        String filter = onlyId == null ? "" : " AND rc.id = :id";
        Map<Long, List<Bracket>> brackets = new HashMap<>();
        jdbc.query("""
                SELECT b.rate_id, b.min_kg, b.max_kg, b.price, b.per_kg_over
                  FROM shipping_rate_brackets b
                  JOIN shipping_rates r ON r.id = b.rate_id
                  JOIN shipping_rate_cards rc ON rc.id = r.rate_card_id
                 WHERE rc.company_id = :c""" + filter + " ORDER BY b.rate_id, b.min_kg", p, rs -> {
            brackets.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                    .add(new Bracket(rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getBigDecimal(5)));
        });
        Map<Long, List<ZoneRate>> rates = new HashMap<>();
        jdbc.query("""
                SELECT r.id, r.rate_card_id, r.zone_key, r.min_days, r.max_days, r.free_from, r.active
                  FROM shipping_rates r
                  JOIN shipping_rate_cards rc ON rc.id = r.rate_card_id
                 WHERE rc.company_id = :c""" + filter + " ORDER BY r.rate_card_id, r.zone_key", p, rs -> {
            long id = rs.getLong(1);
            rates.computeIfAbsent(rs.getLong(2), k -> new ArrayList<>()).add(new ZoneRate(id, rs.getString(3), rs.getInt(4),
                    rs.getInt(5), rs.getBigDecimal(6), rs.getBoolean(7), brackets.getOrDefault(id, List.of())));
        });
        return jdbc.query("""
                SELECT rc.*, c.name AS carrier_name
                  FROM shipping_rate_cards rc
                  LEFT JOIN carriers c ON c.id_carrier = rc.carrier_id
                 WHERE rc.company_id = :c""" + filter + " ORDER BY rc.active DESC, c.name, rc.name", p,
                (rs, i) -> new RateCard(rs.getLong("id"), rs.getLong("carrier_id"), rs.getString("carrier_name"), rs.getString("name"),
                        rs.getInt("volumetric_divisor"), rs.getBigDecimal("default_weight_kg"), rs.getBigDecimal("min_charge"),
                        rs.getBigDecimal("insurance_pct"), rs.getBigDecimal("insurance_min"), rs.getBoolean("active"),
                        rs.getLong("version"), rates.getOrDefault(rs.getLong("id"), List.of())));
    }

    @Override
    public Map<Long, Audit> cardAudits(long companyId) {
        return audits("shipping_rate_cards", companyId);
    }

    @Override
    public boolean carrierExists(long carrierId) {
        return count("SELECT COUNT(*) FROM carriers WHERE id_carrier = :id AND deleted = 0", new MapSqlParameterSource("id", carrierId)) > 0;
    }

    @Override
    public boolean cardExistsForCarrier(long companyId, long carrierId, Long exceptId) {
        return count("SELECT COUNT(*) FROM shipping_rate_cards WHERE company_id = :c AND carrier_id = :carrier AND id <> :except",
                new MapSqlParameterSource("c", companyId).addValue("carrier", carrierId).addValue("except", exceptId == null ? -1 : exceptId)) > 0;
    }

    @Override
    public long insertCard(long companyId, RateCard card, Actor actor, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO shipping_rate_cards (company_id, carrier_id, name, volumetric_divisor, default_weight_kg, min_charge,
                  insurance_pct, insurance_min, active, version, updated_by, updated_by_name, created_at, updated_at)
                VALUES (:c, :carrier, :name, :divisor, :defKg, :minCharge, :insPct, :insMin, :active, 0, :actor, :actorName, :now, :now)""",
                cardParams(companyId, card, actor, now), keys, new String[] {"id"});
        return keys.getKey().longValue();
    }

    @Override
    public boolean updateCard(long companyId, long id, long expectedVersion, RateCard card, Actor actor, Instant now) {
        return jdbc.update("""
                UPDATE shipping_rate_cards SET carrier_id = :carrier, name = :name, volumetric_divisor = :divisor,
                  default_weight_kg = :defKg, min_charge = :minCharge, insurance_pct = :insPct, insurance_min = :insMin,
                  active = :active, version = version + 1, updated_by = :actor, updated_by_name = :actorName, updated_at = :now
                WHERE company_id = :c AND id = :id AND version = :v""",
                cardParams(companyId, card, actor, now).addValue("id", id).addValue("v", expectedVersion)) == 1;
    }

    @Override
    public boolean touchCard(long companyId, long id, long expectedVersion, Actor actor, Instant now) {
        return jdbc.update("""
                UPDATE shipping_rate_cards SET version = version + 1, updated_by = :actor, updated_by_name = :actorName, updated_at = :now
                WHERE company_id = :c AND id = :id AND version = :v""",
                new MapSqlParameterSource("c", companyId).addValue("id", id).addValue("v", expectedVersion)
                        .addValue("actor", actor.id()).addValue("actorName", actor.name()).addValue("now", ts(now))) == 1;
    }

    @Override
    public void deleteCard(long companyId, long id) {
        MapSqlParameterSource p = new MapSqlParameterSource("c", companyId).addValue("id", id);
        jdbc.update("""
                DELETE b FROM shipping_rate_brackets b JOIN shipping_rates r ON r.id = b.rate_id
                  JOIN shipping_rate_cards rc ON rc.id = r.rate_card_id WHERE rc.company_id = :c AND rc.id = :id""", p);
        jdbc.update("""
                DELETE r FROM shipping_rates r JOIN shipping_rate_cards rc ON rc.id = r.rate_card_id
                 WHERE rc.company_id = :c AND rc.id = :id""", p);
        jdbc.update("DELETE FROM shipping_rate_cards WHERE company_id = :c AND id = :id", p);
    }

    @Override
    public void saveRate(long cardId, ZoneRate rate) {
        MapSqlParameterSource p = new MapSqlParameterSource("card", cardId).addValue("zone", rate.zoneKey())
                .addValue("min", rate.minDays()).addValue("max", rate.maxDays()).addValue("free", rate.freeFrom())
                .addValue("active", rate.active());
        List<Long> existing = jdbc.queryForList("SELECT id FROM shipping_rates WHERE rate_card_id = :card AND zone_key = :zone", p, Long.class);
        long rateId;
        if (existing.isEmpty()) {
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO shipping_rates (rate_card_id, zone_key, min_days, max_days, free_from, active)
                    VALUES (:card, :zone, :min, :max, :free, :active)""", p, keys, new String[] {"id"});
            rateId = keys.getKey().longValue();
        } else {
            rateId = existing.get(0);
            jdbc.update("""
                    UPDATE shipping_rates SET min_days = :min, max_days = :max, free_from = :free, active = :active
                    WHERE id = :rate""", p.addValue("rate", rateId));
            jdbc.update("DELETE FROM shipping_rate_brackets WHERE rate_id = :rate", new MapSqlParameterSource("rate", rateId));
        }
        for (Bracket b : rate.brackets()) {
            jdbc.update("""
                    INSERT INTO shipping_rate_brackets (rate_id, min_kg, max_kg, price, per_kg_over)
                    VALUES (:rate, :min, :max, :price, :over)""",
                    new MapSqlParameterSource("rate", rateId).addValue("min", b.minKg()).addValue("max", b.maxKg())
                            .addValue("price", b.price()).addValue("over", b.perKgOver()));
        }
    }

    @Override
    public boolean deleteRate(long cardId, String zoneKey) {
        MapSqlParameterSource p = new MapSqlParameterSource("card", cardId).addValue("zone", zoneKey);
        jdbc.update("""
                DELETE b FROM shipping_rate_brackets b JOIN shipping_rates r ON r.id = b.rate_id
                 WHERE r.rate_card_id = :card AND r.zone_key = :zone""", p);
        return jdbc.update("DELETE FROM shipping_rates WHERE rate_card_id = :card AND zone_key = :zone", p) > 0;
    }

    private static MapSqlParameterSource cardParams(long companyId, RateCard card, Actor actor, Instant now) {
        return new MapSqlParameterSource("c", companyId).addValue("carrier", card.carrierId()).addValue("name", card.name())
                .addValue("divisor", card.volumetricDivisor()).addValue("defKg", card.defaultWeightKg())
                .addValue("minCharge", card.minCharge()).addValue("insPct", card.insurancePct())
                .addValue("insMin", card.insuranceMin()).addValue("active", card.active()).addValue("actor", actor.id())
                .addValue("actorName", actor.name()).addValue("now", ts(now));
    }

    // ------------------------------------------------------------------------------------------ zonas

    @Override
    public List<ZoneView> zones(long companyId) {
        return loadZones(companyId, null);
    }

    @Override
    public Optional<ZoneView> zone(long companyId, long id) {
        return loadZones(companyId, id).stream().findFirst();
    }

    private List<ZoneView> loadZones(long companyId, Long onlyId) {
        MapSqlParameterSource p = new MapSqlParameterSource("c", companyId).addValue("id", onlyId);
        Map<Long, List<LabeledPlace>> places = new HashMap<>();
        jdbc.query("SELECT zone_id, country_id, state_id, city_id, label FROM shipping_zone_places WHERE company_id = :c"
                + (onlyId == null ? "" : " AND zone_id = :id") + " ORDER BY zone_id, label, id", p, rs -> {
            places.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new LabeledPlace(
                    new Place(rs.getLong(2), longOrNull(rs, 3), longOrNull(rs, 4)), rs.getString(5)));
        });
        return jdbc.query("SELECT * FROM shipping_zones WHERE company_id = :c" + (onlyId == null ? "" : " AND id = :id")
                + " ORDER BY active DESC, name", p, (rs, i) -> {
            long id = rs.getLong("id");
            List<LabeledPlace> mine = places.getOrDefault(id, List.of());
            Zone zone = new Zone(id, rs.getString("code"), rs.getString("name"), rs.getBoolean("active"),
                    mine.stream().map(LabeledPlace::place).toList());
            return new ZoneView(zone, rs.getString("description"), rs.getLong("version"), mine,
                    new Audit(rs.getString("updated_by_name"), instant(rs.getTimestamp("updated_at"))));
        });
    }

    @Override
    public boolean zoneCodeTaken(long companyId, String code, Long exceptId) {
        return count("SELECT COUNT(*) FROM shipping_zones WHERE company_id = :c AND code = :code AND id <> :except",
                new MapSqlParameterSource("c", companyId).addValue("code", code).addValue("except", exceptId == null ? -1 : exceptId)) > 0;
    }

    @Override
    public Optional<String> placeOwner(long companyId, Collection<String> placeKeys, Long exceptZoneId) {
        if (placeKeys.isEmpty()) {
            return Optional.empty();
        }
        return jdbc.query("""
                SELECT CONCAT(z.name, ' (', p.label, ')') FROM shipping_zone_places p JOIN shipping_zones z ON z.id = p.zone_id
                 WHERE p.company_id = :c AND p.place_key IN (:keys) AND p.zone_id <> :except LIMIT 1""",
                new MapSqlParameterSource("c", companyId).addValue("keys", placeKeys)
                        .addValue("except", exceptZoneId == null ? -1 : exceptZoneId), (rs, i) -> rs.getString(1)).stream().findFirst();
    }

    @Override
    public long insertZone(long companyId, String code, String name, String description, boolean active, Actor actor, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO shipping_zones (company_id, code, name, description, active, version, updated_by, updated_by_name,
                  created_at, updated_at)
                VALUES (:c, :code, :name, :description, :active, 0, :actor, :actorName, :now, :now)""",
                zoneParams(companyId, code, name, description, active, actor, now), keys, new String[] {"id"});
        return keys.getKey().longValue();
    }

    @Override
    public boolean updateZone(long companyId, long id, long expectedVersion, String code, String name, String description,
                              boolean active, Actor actor, Instant now) {
        return jdbc.update("""
                UPDATE shipping_zones SET code = :code, name = :name, description = :description, active = :active,
                  version = version + 1, updated_by = :actor, updated_by_name = :actorName, updated_at = :now
                WHERE company_id = :c AND id = :id AND version = :v""",
                zoneParams(companyId, code, name, description, active, actor, now).addValue("id", id).addValue("v", expectedVersion)) == 1;
    }

    private static MapSqlParameterSource zoneParams(long companyId, String code, String name, String description, boolean active,
                                                    Actor actor, Instant now) {
        return new MapSqlParameterSource("c", companyId).addValue("code", code).addValue("name", name)
                .addValue("description", description).addValue("active", active).addValue("actor", actor.id())
                .addValue("actorName", actor.name()).addValue("now", ts(now));
    }

    @Override
    public void replacePlaces(long companyId, long zoneId, List<LabeledPlace> places) {
        jdbc.update("DELETE FROM shipping_zone_places WHERE company_id = :c AND zone_id = :z",
                new MapSqlParameterSource("c", companyId).addValue("z", zoneId));
        for (LabeledPlace lp : places) {
            Place pl = lp.place();
            jdbc.update("""
                    INSERT INTO shipping_zone_places (company_id, zone_id, country_id, state_id, city_id, place_key, label)
                    VALUES (:c, :z, :country, :state, :city, :key, :label)""",
                    new MapSqlParameterSource("c", companyId).addValue("z", zoneId).addValue("country", pl.countryId())
                            .addValue("state", pl.stateId()).addValue("city", pl.cityId()).addValue("key", pl.key())
                            .addValue("label", lp.label()));
        }
    }

    @Override
    public int ratesUsingZone(long companyId, String zoneKey) {
        return count("""
                SELECT COUNT(*) FROM shipping_rates r JOIN shipping_rate_cards rc ON rc.id = r.rate_card_id
                 WHERE rc.company_id = :c AND r.zone_key = :zone""",
                new MapSqlParameterSource("c", companyId).addValue("zone", zoneKey));
    }

    @Override
    public void deleteZone(long companyId, long id) {
        MapSqlParameterSource p = new MapSqlParameterSource("c", companyId).addValue("id", id);
        jdbc.update("DELETE FROM shipping_zone_places WHERE company_id = :c AND zone_id = :id", p);
        jdbc.update("DELETE FROM shipping_zones WHERE company_id = :c AND id = :id", p);
    }

    // ------------------------------------------------------------------------------------------ cotizar

    @Override
    public Optional<Origin> origin(Long warehouseId) {
        String sql = warehouseId != null
                ? "SELECT id_warehouse, country_id, state_id, city_id FROM warehouses WHERE id_warehouse = :id AND deleted = 0"
                : """
                  SELECT id_warehouse, country_id, state_id, city_id FROM warehouses
                   WHERE deleted = 0 AND active = 1 ORDER BY is_main DESC, fulfillment_priority, id_warehouse LIMIT 1""";
        return jdbc.query(sql, new MapSqlParameterSource("id", warehouseId), (rs, i) -> new Origin(rs.getLong(1),
                new Destination(longOrNull(rs, 2), longOrNull(rs, 3), longOrNull(rs, 4)))).stream().findFirst();
    }

    @Override
    public Map<Long, Piece> skuPieces(Collection<Long> skuIds) {
        Map<Long, Piece> result = new LinkedHashMap<>();
        if (skuIds.isEmpty()) {
            return result;
        }
        // Medidas del SKU y, si no las tiene, las del producto.
        jdbc.query("""
                SELECT s.id_sku, COALESCE(s.weight, p.weight), COALESCE(s.length, p.length), COALESCE(s.width, p.width),
                       COALESCE(s.height, p.height)
                  FROM product_skus s JOIN products p ON p.id_product = s.product_id
                 WHERE s.id_sku IN (:ids)""", new MapSqlParameterSource("ids", skuIds),
                rs -> {
                    result.put(rs.getLong(1), piece(rs));
                });
        return result;
    }

    @Override
    public Map<Long, Piece> productPieces(Collection<Long> productIds) {
        Map<Long, Piece> result = new LinkedHashMap<>();
        if (productIds.isEmpty()) {
            return result;
        }
        jdbc.query("SELECT id_product, weight, length, width, height FROM products WHERE id_product IN (:ids)",
                new MapSqlParameterSource("ids", productIds), rs -> {
                    result.put(rs.getLong(1), piece(rs));
                });
        return result;
    }

    private static Piece piece(ResultSet rs) throws SQLException {
        return new Piece(decimal(rs, 2), decimal(rs, 3), decimal(rs, 4), decimal(rs, 5), 1);
    }

    @Override
    public void recordMiss(long companyId, Destination d, String label, String zoneKey, Instant now) {
        jdbc.update("""
                INSERT INTO shipping_quote_misses (company_id, place_key, country_id, state_id, city_id, zone_key, label, misses, last_at)
                VALUES (:c, :key, :country, :state, :city, :zone, :label, 1, :now)
                ON DUPLICATE KEY UPDATE misses = misses + 1, last_at = VALUES(last_at), zone_key = VALUES(zone_key),
                  label = COALESCE(VALUES(label), label)""",
                new MapSqlParameterSource("c", companyId).addValue("key", missKey(d)).addValue("country", d.countryId())
                        .addValue("state", d.stateId()).addValue("city", d.cityId()).addValue("zone", zoneKey)
                        .addValue("label", label).addValue("now", ts(now)));
    }

    @Override
    public List<Miss> misses(long companyId, int limit) {
        return jdbc.query("""
                SELECT country_id, state_id, city_id, label, zone_key, misses, last_at FROM shipping_quote_misses
                 WHERE company_id = :c ORDER BY last_at DESC LIMIT :limit""",
                new MapSqlParameterSource("c", companyId).addValue("limit", limit),
                (rs, i) -> new Miss(longOrNull(rs, 1), longOrNull(rs, 2), longOrNull(rs, 3), rs.getString(4), rs.getString(5),
                        rs.getInt(6), instant(rs.getTimestamp(7))));
    }

    @Override
    public void clearMiss(long companyId, Destination d) {
        jdbc.update("DELETE FROM shipping_quote_misses WHERE company_id = :c AND place_key = :key",
                new MapSqlParameterSource("c", companyId).addValue("key", missKey(d)));
    }

    static String missKey(Destination d) {
        return d.countryId() + ":" + d.stateId() + ":" + d.cityId();
    }

    // ------------------------------------------------------------------------------------------ apoyo

    private Map<Long, Audit> audits(String table, long companyId) {
        Map<Long, Audit> result = new LinkedHashMap<>();
        jdbc.query("SELECT id, updated_by_name, updated_at FROM " + table + " WHERE company_id = :c",
                new MapSqlParameterSource("c", companyId), rs -> {
                    result.put(rs.getLong(1), new Audit(rs.getString(2), instant(rs.getTimestamp(3))));
                });
        return result;
    }

    private int count(String sql, MapSqlParameterSource p) {
        Integer n = jdbc.queryForObject(sql, p, Integer.class);
        return n == null ? 0 : n;
    }

    private static Long longOrNull(ResultSet rs, int col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private static BigDecimal decimal(ResultSet rs, int col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : BigDecimal.valueOf(v);
    }

    private static Timestamp ts(Instant now) {
        return Timestamp.valueOf(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }
}
