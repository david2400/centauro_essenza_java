package com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.marketplace.application.port.SellerInvoiceStore;

/** Conceptos facturables del vendedor por periodo y sus facturas (JDBC). */
@Repository
public class SellerInvoiceJdbcStore implements SellerInvoiceStore {

    private final NamedParameterJdbcTemplate jdbc;

    public SellerInvoiceJdbcStore(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public Map<Long, SellerParty> sellers(long companyId) {
        Map<Long, SellerParty> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT id, legal_name, doc_type, doc_number, email, phone, address, city FROM mp_sellers
                WHERE company_id = :company AND kind = 'EXTERNAL' ORDER BY legal_name, id""",
                new MapSqlParameterSource("company", companyId), rs -> {
                    result.put(rs.getLong("id"), new SellerParty(rs.getLong("id"), rs.getString("legal_name"), rs.getString("doc_type"),
                            rs.getString("doc_number"), rs.getString("email"), rs.getString("phone"), rs.getString("address"),
                            rs.getString("city")));
                });
        return result;
    }

    @Override
    public Map<Long, PeriodTotals> totals(long companyId, LocalDate from, LocalDate to, String cycle) {
        Map<Long, PeriodTotals> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT seller_id,
                  COALESCE(SUM(CASE WHEN entry_type IN ('COMMISSION', 'COMMISSION_REVERSAL') AND entry_date BETWEEN :from AND :to
                                    THEN amount END), 0) AS commissions,
                  COALESCE(SUM(CASE WHEN entry_type = 'COMMISSION' AND entry_date BETWEEN :from AND :to THEN 1 END), 0) AS commission_count,
                  COALESCE(SUM(CASE WHEN entry_type = 'MONTHLY_FEE' AND fee_cycle = :cycle THEN amount END), 0) AS monthly_fee,
                  COALESCE(SUM(CASE WHEN entry_type = 'STORAGE_FEE' AND fee_cycle = :cycle THEN amount END), 0) AS storage,
                  COALESCE(SUM(CASE WHEN entry_type = 'DISPUTE_CHARGE' AND entry_date BETWEEN :from AND :to THEN amount END), 0) AS disputes,
                  COALESCE(SUM(CASE WHEN entry_type IN ('MARKETPLACE_SALE', 'MARKETPLACE_REVERSAL', 'CONSIGNMENT_SALE', 'CONSIGNMENT_REVERSAL')
                                    AND entry_date BETWEEN :from AND :to THEN -amount END), 0) AS collected
                FROM mp_seller_ledger WHERE company_id = :company
                  AND (entry_date BETWEEN :from AND :to OR fee_cycle = :cycle)
                GROUP BY seller_id""",
                new MapSqlParameterSource("company", companyId).addValue("from", from).addValue("to", to).addValue("cycle", cycle), rs -> {
                    result.put(rs.getLong("seller_id"), new PeriodTotals(rs.getBigDecimal("commissions"), rs.getInt("commission_count"),
                            rs.getBigDecimal("monthly_fee"), rs.getBigDecimal("storage"), rs.getBigDecimal("disputes"),
                            rs.getBigDecimal("collected")));
                });
        return result;
    }

    @Override
    public Map<Long, List<IssuedRef>> issued(long companyId, String period) {
        Map<Long, List<IssuedRef>> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT seller_id, invoice_id, invoice_number, total, created_at FROM mp_seller_invoices
                WHERE company_id = :company AND period = :period ORDER BY id DESC""",
                new MapSqlParameterSource("company", companyId).addValue("period", period), rs -> {
                    Timestamp at = rs.getTimestamp("created_at");
                    result.computeIfAbsent(rs.getLong("seller_id"), k -> new ArrayList<>()).add(new IssuedRef(rs.getLong("invoice_id"),
                            rs.getString("invoice_number"), rs.getBigDecimal("total"),
                            at == null ? null : at.toLocalDateTime().toInstant(ZoneOffset.UTC)));
                });
        return result;
    }

    @Override
    public void link(long companyId, long sellerId, String period, long invoiceId, String number, BigDecimal total, String actorId,
                     String actorName, Instant now) {
        jdbc.update("""
                INSERT IGNORE INTO mp_seller_invoices (company_id, seller_id, period, invoice_id, invoice_number, total, created_by,
                  created_by_name, created_at)
                VALUES (:company, :seller, :period, :invoice, :number, :total, :actor, :actorName, :now)""",
                new MapSqlParameterSource("company", companyId).addValue("seller", sellerId).addValue("period", period)
                        .addValue("invoice", invoiceId).addValue("number", number).addValue("total", total).addValue("actor", actorId)
                        .addValue("actorName", actorName).addValue("now", LocalDateTime.ofInstant(now, ZoneOffset.UTC)));
    }
}
