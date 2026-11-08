package com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc;

import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.utc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.marketplace.application.port.OfferSaleStore;
import com.essenza.draco.modules.marketplace.domain.offer.OfferReservation;

/** Disponibilidad y reservas de ofertas en MySQL. */
@Repository
public class OfferSaleJdbcStore implements OfferSaleStore {

    private static final String SELECT = """
            SELECT o.id, o.company_id, o.sku_id, o.currency, o.price, o.available_quantity, o.handling_days,
                   o.item_condition, o.status, o.fulfillment, s.id AS seller_id, COALESCE(s.trade_name, s.legal_name) AS seller_name,
                   s.kind AS seller_kind, s.status AS seller_status
            FROM mp_offers o JOIN mp_sellers s ON s.id = o.seller_id AND s.company_id = o.company_id""";

    private final NamedParameterJdbcTemplate jdbc;

    public OfferSaleJdbcStore(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public Optional<SaleOffer> lockForSale(long companyId, long offerId) {
        return jdbc.query(SELECT + " WHERE o.id = :id AND o.company_id = :company FOR UPDATE",
                new MapSqlParameterSource("id", offerId).addValue("company", companyId), (rs, i) -> offer(rs))
                .stream().findFirst();
    }

    @Override
    public List<SaleOffer> forSale(long companyId, long skuId) {
        return jdbc.query(SELECT + " " + """
                WHERE o.company_id = :company AND o.sku_id = :sku AND o.status = 'ACTIVE' AND o.available_quantity > 0
                  AND s.kind = 'EXTERNAL' AND s.status = 'APPROVED'
                ORDER BY o.price, o.id LIMIT 50""",
                new MapSqlParameterSource("company", companyId).addValue("sku", skuId), (rs, i) -> offer(rs));
    }

    @Override
    public Optional<SaleOffer.Seller> ownSeller(long companyId) {
        return jdbc.query("""
                SELECT id, COALESCE(trade_name, legal_name) AS name, kind, status FROM mp_sellers
                WHERE company_id = :company AND kind = 'OWN' AND status <> 'CLOSED' ORDER BY id LIMIT 1""",
                new MapSqlParameterSource("company", companyId),
                (rs, i) -> new SaleOffer.Seller(rs.getLong("id"), rs.getString("name"), rs.getString("kind"),
                        rs.getString("status"))).stream().findFirst();
    }

    @Override
    public boolean decrement(long companyId, long offerId, int quantity, Instant now) {
        // lock_version sube: una edición hecha con datos viejos no pisa lo que se acaba de reservar.
        return jdbc.update("""
                UPDATE mp_offers SET available_quantity = available_quantity - :qty, lock_version = lock_version + 1,
                  updated_at = :now
                WHERE id = :id AND company_id = :company AND available_quantity >= :qty""",
                new MapSqlParameterSource("qty", quantity).addValue("now", utc(now)).addValue("id", offerId)
                        .addValue("company", companyId)) == 1;
    }

    @Override
    public void increment(long companyId, long offerId, int quantity, Instant now) {
        jdbc.update("""
                UPDATE mp_offers SET available_quantity = available_quantity + :qty, lock_version = lock_version + 1,
                  updated_at = :now
                WHERE id = :id AND company_id = :company""",
                new MapSqlParameterSource("qty", quantity).addValue("now", utc(now)).addValue("id", offerId)
                        .addValue("company", companyId));
    }

    @Override
    public long insertReservation(long companyId, long offerId, long orderId, long lineId, int quantity, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO mp_offer_reservations (company_id, offer_id, order_id, order_line_id, quantity, status,
                  created_at, updated_at)
                VALUES (:company, :offer, :order, :line, :qty, 'ACTIVE', :now, :now)""",
                new MapSqlParameterSource("company", companyId).addValue("offer", offerId).addValue("order", orderId)
                        .addValue("line", lineId).addValue("qty", quantity).addValue("now", utc(now)),
                keys, new String[] {"id"});
        return keys.getKey().longValue();
    }

    @Override
    public List<ReservationRow> reservations(long orderId, boolean forUpdate) {
        return jdbc.query("""
                SELECT id, company_id, offer_id, order_id, order_line_id, quantity, status
                FROM mp_offer_reservations WHERE order_id = :order ORDER BY offer_id, id""" + (forUpdate ? " FOR UPDATE" : ""),
                new MapSqlParameterSource("order", orderId),
                (rs, i) -> new ReservationRow(rs.getLong("id"), rs.getLong("company_id"), rs.getLong("offer_id"),
                        rs.getLong("order_id"), rs.getLong("order_line_id"), rs.getInt("quantity"),
                        OfferReservation.Status.valueOf(rs.getString("status"))));
    }

    @Override
    public void setReservationStatus(long reservationId, OfferReservation.Status status, Instant now) {
        jdbc.update("UPDATE mp_offer_reservations SET status = :status, updated_at = :now WHERE id = :id",
                new MapSqlParameterSource("status", status.name()).addValue("now", utc(now)).addValue("id", reservationId));
    }

    private static SaleOffer offer(ResultSet rs) throws SQLException {
        return new SaleOffer(rs.getLong("id"), rs.getLong("company_id"),
                new SaleOffer.Seller(rs.getLong("seller_id"), rs.getString("seller_name"), rs.getString("seller_kind"),
                        rs.getString("seller_status")),
                rs.getLong("sku_id"), rs.getString("currency"), rs.getBigDecimal("price"), rs.getInt("available_quantity"),
                rs.getInt("handling_days"), rs.getString("item_condition"), rs.getString("status"), rs.getString("fulfillment"));
    }
}
