package com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc;

import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.instant;
import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.utc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.marketplace.application.port.AssignmentStore;

/** Vigencias de planes en MySQL. Toda sentencia filtra por company_id. */
@Repository
public class AssignmentJdbcStore implements AssignmentStore {

    private static final String SELECT = """
            SELECT a.*, p.code AS plan_code, p.name AS plan_name, p.version AS plan_version
            FROM mp_plan_assignments a JOIN mp_plans p ON p.id = a.plan_id AND p.company_id = a.company_id""";

    private final NamedParameterJdbcTemplate jdbc;

    public AssignmentJdbcStore(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public List<AssignmentRow> bySeller(long companyId, long sellerId) {
        return jdbc.query(SELECT + " WHERE a.company_id = :company AND a.seller_id = :seller ORDER BY a.starts_on DESC, a.id DESC",
                new MapSqlParameterSource("company", companyId).addValue("seller", sellerId), (rs, i) -> row(rs));
    }

    @Override
    public Map<Long, List<AssignmentRow>> openFor(long companyId, Collection<Long> sellerIds, LocalDate today) {
        if (sellerIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<AssignmentRow>> result = new LinkedHashMap<>();
        jdbc.query(SELECT + """
                 WHERE a.company_id = :company AND a.seller_id IN (:sellers) AND a.cancelled_at IS NULL
                   AND (a.ends_on IS NULL OR a.ends_on >= :today)
                ORDER BY a.starts_on""",
                new MapSqlParameterSource("company", companyId).addValue("sellers", sellerIds).addValue("today", today),
                rs -> {
                    AssignmentRow row = row(rs);
                    result.computeIfAbsent(row.sellerId(), k -> new java.util.ArrayList<>()).add(row);
                });
        return result;
    }

    @Override
    public Map<Long, Long> openCountByPlan(long companyId, Collection<Long> planIds, LocalDate today) {
        if (planIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>();
        jdbc.query("""
                SELECT plan_id, COUNT(*) n FROM mp_plan_assignments
                WHERE company_id = :company AND plan_id IN (:plans) AND cancelled_at IS NULL
                  AND (ends_on IS NULL OR ends_on >= :today)
                GROUP BY plan_id""",
                new MapSqlParameterSource("company", companyId).addValue("plans", planIds).addValue("today", today),
                rs -> {
                    result.put(rs.getLong("plan_id"), rs.getLong("n"));
                });
        return result;
    }

    @Override
    public long insert(long companyId, NewAssignment a, String actorId, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO mp_plan_assignments (company_id, seller_id, plan_id, starts_on, override_commission_percent,
                  override_monthly_fee, override_reason, override_approved_by, agreed_terms, notes, created_at, created_by)
                VALUES (:company, :seller, :plan, :starts, :pct, :fee, :reason, :approvedBy, :terms, :notes, :now, :actor)""",
                new MapSqlParameterSource("company", companyId).addValue("seller", a.sellerId()).addValue("plan", a.planId())
                        .addValue("starts", a.startsOn()).addValue("pct", a.overrideCommissionPercent())
                        .addValue("fee", a.overrideMonthlyFee()).addValue("reason", a.overrideReason())
                        .addValue("approvedBy", a.overrideApprovedBy()).addValue("terms", a.agreedTermsJson())
                        .addValue("notes", a.notes()).addValue("now", utc(now)).addValue("actor", actorId),
                keys, new String[] {"id"});
        return keys.getKey().longValue();
    }

    @Override
    public void setEndsOn(long companyId, long assignmentId, LocalDate endsOn) {
        jdbc.update("UPDATE mp_plan_assignments SET ends_on = :ends WHERE id = :id AND company_id = :company",
                new MapSqlParameterSource("ends", endsOn).addValue("id", assignmentId).addValue("company", companyId));
    }

    @Override
    public void cancel(long companyId, long assignmentId, String reason, String actorId, Instant now) {
        jdbc.update("""
                UPDATE mp_plan_assignments SET cancelled_at = :now, cancelled_by = :actor, cancel_reason = :reason
                WHERE id = :id AND company_id = :company AND cancelled_at IS NULL""",
                new MapSqlParameterSource("now", utc(now)).addValue("actor", actorId).addValue("reason", reason)
                        .addValue("id", assignmentId).addValue("company", companyId));
    }

    private static AssignmentRow row(ResultSet rs) throws SQLException {
        return new AssignmentRow(rs.getLong("id"), rs.getLong("seller_id"), rs.getLong("plan_id"), rs.getString("plan_code"),
                rs.getString("plan_name"), rs.getInt("plan_version"), JdbcSupport.date(rs, "starts_on"),
                JdbcSupport.date(rs, "ends_on"), rs.getBigDecimal("override_commission_percent"),
                rs.getBigDecimal("override_monthly_fee"), rs.getString("override_reason"),
                rs.getString("override_approved_by"), rs.getString("agreed_terms"), rs.getString("notes"),
                instant(rs, "cancelled_at"), rs.getString("cancelled_by"), rs.getString("cancel_reason"),
                instant(rs, "created_at"), rs.getString("created_by"));
    }
}
