package com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc;

import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.date;
import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.instant;
import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.utc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.marketplace.application.port.SellerLedgerStore;
import com.essenza.draco.modules.marketplace.domain.plan.Money;

/** Estado de cuenta de vendedores en MySQL. Asientos solo de inserción; clave única por empresa. */
@Repository
public class SellerLedgerJdbcStore implements SellerLedgerStore {

    private final NamedParameterJdbcTemplate jdbc;

    public SellerLedgerJdbcStore(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public boolean post(long companyId, NewEntry e, String actorId, String actorName, Instant now) {
        try {
            jdbc.update("""
                    INSERT INTO mp_seller_ledger (company_id, seller_id, entry_type, amount, currency, entry_date, due_on,
                      interest_monthly_pct, reference, commission_id, fee_cycle, idempotency_key, note, created_by,
                      created_by_name, created_at)
                    VALUES (:company, :seller, :type, :amount, :currency, :date, :due, :interest, :reference, :commission,
                      :cycle, :key, :note, :actor, :actorName, :now)""",
                    new MapSqlParameterSource("company", companyId).addValue("seller", e.sellerId()).addValue("type", e.type())
                            .addValue("amount", e.amount()).addValue("currency", e.currency()).addValue("date", e.entryDate())
                            .addValue("due", e.dueOn())
                            .addValue("interest", e.interestMonthlyPct() == null ? BigDecimal.ZERO : e.interestMonthlyPct())
                            .addValue("reference", e.reference()).addValue("commission", e.commissionId())
                            .addValue("cycle", e.feeCycle()).addValue("key", e.idempotencyKey()).addValue("note", e.note())
                            .addValue("actor", actorId).addValue("actorName", actorName).addValue("now", utc(now)));
            return true;
        } catch (DataIntegrityViolationException ex) {
            if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains("uk_mp_seller_ledger_key")) {
                return false;
            }
            throw ex;
        }
    }

    @Override
    public List<LedgerRow> entries(long companyId, long sellerId) {
        return jdbc.query("""
                SELECT * FROM mp_seller_ledger WHERE company_id = :company AND seller_id = :seller
                ORDER BY entry_date, id LIMIT 5000""",
                new MapSqlParameterSource("company", companyId).addValue("seller", sellerId),
                (rs, i) -> {
                    long commission = rs.getLong("commission_id");
                    Long commissionId = rs.wasNull() ? null : commission;
                    return new LedgerRow(rs.getLong("id"), rs.getLong("seller_id"), rs.getString("entry_type"),
                            Money.round(rs.getBigDecimal("amount"), rs.getString("currency")), rs.getString("currency"),
                            date(rs, "entry_date"), date(rs, "due_on"), rs.getBigDecimal("interest_monthly_pct"),
                            rs.getString("reference"), commissionId, rs.getString("fee_cycle"), rs.getString("note"),
                            rs.getString("created_by"), rs.getString("created_by_name"), instant(rs, "created_at"));
                });
    }

    @Override
    public Map<Long, BigDecimal> balances(long companyId) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        jdbc.query("SELECT seller_id, SUM(amount) balance FROM mp_seller_ledger WHERE company_id = :company GROUP BY seller_id",
                new MapSqlParameterSource("company", companyId), rs -> {
                    result.put(rs.getLong("seller_id"), rs.getBigDecimal("balance"));
                });
        return result;
    }

    @Override
    public List<Long> billableSellers(long companyId) {
        return jdbc.queryForList("""
                SELECT id FROM mp_sellers WHERE company_id = :company AND kind = 'EXTERNAL' AND status <> 'CLOSED'
                ORDER BY id""", new MapSqlParameterSource("company", companyId), Long.class);
    }
}
