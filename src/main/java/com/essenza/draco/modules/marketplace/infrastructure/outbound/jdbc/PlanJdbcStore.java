package com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc;

import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.instant;
import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.utc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.PlanEventDto;
import com.essenza.draco.modules.marketplace.application.port.PlanStore;
import com.essenza.draco.modules.marketplace.domain.plan.CommercialPlan;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.AccrualEvent;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.CommissionBase;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.FeeBilling;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.Modality;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.RefundPolicy;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.Status;
import com.essenza.draco.modules.marketplace.domain.plan.PlanTerms;

/** Versiones de planes en MySQL. Toda sentencia filtra por company_id. */
@Repository
public class PlanJdbcStore implements PlanStore {

    private static final Map<String, String> DUPLICATES = Map.of(
            "uk_mp_plans_version", "Ya existe esa versión del plan.",
            "uk_mp_plans_draft", "Ya hay un borrador de este plan; edítalo o publícalo antes de crear otro.");

    private static final Map<String, String> SORTS = Map.of(
            "code", "p.code", "name", "p.name", "status", "p.status", "modality", "p.modality",
            "version", "p.version", "validFrom", "p.valid_from", "updatedAt", "p.updated_at", "id", "p.id");

    private final NamedParameterJdbcTemplate jdbc;

    public PlanJdbcStore(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public long insert(long companyId, CommercialPlan plan, String actorId, Instant now) {
        MapSqlParameterSource params = params(plan).addValue("company", companyId).addValue("code", plan.code())
                .addValue("version", plan.version()).addValue("status", plan.status().name()).addValue("actor", actorId)
                .addValue("now", utc(now));
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update("""
                    INSERT INTO mp_plans (company_id, code, version, name, description, status, modality, currency, monthly_fee,
                      commission_percent, fixed_fee_per_sale, commission_base, include_shipping, include_taxes, accrual_event,
                      return_window_days, refund_policy, fee_billing, grace_days, late_interest_monthly_percent,
                      storage_fee_unit_day, valid_from, valid_to, lock_version, created_at, created_by, updated_at, updated_by)
                    VALUES (:company, :code, :version, :name, :description, :status, :modality, :currency, :fee, :pct, :fixed,
                      :base, :shipping, :taxes, :accrual, :window, :refund, :billing, :grace, :interest, :storage, :from, :to, 0, :now,
                      :actor, :now, :actor)""", params, keys, new String[] {"id"});
        } catch (DataIntegrityViolationException ex) {
            throw JdbcSupport.duplicate(ex, DUPLICATES);
        }
        return keys.getKey().longValue();
    }

    @Override
    public Optional<PlanRow> find(long companyId, long planId, boolean forUpdate) {
        return jdbc.query("SELECT p.* FROM mp_plans p WHERE p.id = :id AND p.company_id = :company" + (forUpdate ? " FOR UPDATE" : ""),
                new MapSqlParameterSource("id", planId).addValue("company", companyId), (rs, i) -> row(rs)).stream().findFirst();
    }

    @Override
    public Optional<PlanRow> activeVersion(long companyId, String code) {
        return jdbc.query("SELECT p.* FROM mp_plans p WHERE p.company_id = :company AND p.code = :code AND p.status = 'ACTIVE' FOR UPDATE",
                new MapSqlParameterSource("company", companyId).addValue("code", code), (rs, i) -> row(rs)).stream().findFirst();
    }

    @Override
    public int maxVersion(long companyId, String code) {
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(version), 0) FROM mp_plans WHERE company_id = :company AND code = :code",
                new MapSqlParameterSource("company", companyId).addValue("code", code), Integer.class);
        return max == null ? 0 : max;
    }

    @Override
    public boolean updateDraft(long companyId, long planId, int lockVersion, CommercialPlan plan, String actorId, Instant now) {
        return jdbc.update("""
                UPDATE mp_plans SET name = :name, description = :description, modality = :modality, currency = :currency,
                  monthly_fee = :fee, commission_percent = :pct, fixed_fee_per_sale = :fixed, commission_base = :base,
                  include_shipping = :shipping, include_taxes = :taxes, accrual_event = :accrual, return_window_days = :window,
                  refund_policy = :refund, fee_billing = :billing, grace_days = :grace,
                  late_interest_monthly_percent = :interest, storage_fee_unit_day = :storage, valid_from = :from, valid_to = :to,
                  lock_version = lock_version + 1, updated_at = :now, updated_by = :actor
                WHERE id = :id AND company_id = :company AND lock_version = :lock AND status = 'DRAFT'""",
                params(plan).addValue("id", planId).addValue("company", companyId).addValue("lock", lockVersion)
                        .addValue("actor", actorId).addValue("now", utc(now))) == 1;
    }

    @Override
    public boolean updateStatus(long companyId, long planId, int lockVersion, Status status, boolean publishing,
                                String actorId, Instant now) {
        try {
            return jdbc.update("""
                    UPDATE mp_plans SET status = :status,
                      published_at = CASE WHEN :publishing = 1 THEN :now ELSE published_at END,
                      published_by = CASE WHEN :publishing = 1 THEN :actor ELSE published_by END,
                      lock_version = lock_version + 1, updated_at = :now, updated_by = :actor
                    WHERE id = :id AND company_id = :company AND lock_version = :lock""",
                    new MapSqlParameterSource("status", status.name()).addValue("publishing", publishing ? 1 : 0)
                            .addValue("now", utc(now)).addValue("actor", actorId).addValue("id", planId)
                            .addValue("company", companyId).addValue("lock", lockVersion)) == 1;
        } catch (DataIntegrityViolationException ex) {
            throw JdbcSupport.duplicate(ex, DUPLICATES);
        }
    }

    @Override
    public Page<PlanRow> search(long companyId, PlanFilter filter, Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE p.company_id = :company");
        MapSqlParameterSource params = new MapSqlParameterSource("company", companyId);
        if (filter.q() != null && !filter.q().isBlank()) {
            where.append(" AND (LOWER(p.code) LIKE :q OR LOWER(p.name) LIKE :q)");
            params.addValue("q", JdbcSupport.like(filter.q()));
        }
        if (filter.status() != null && !filter.status().isBlank()) {
            where.append(" AND p.status = :status");
            params.addValue("status", filter.status().trim().toUpperCase());
        }
        if (filter.modality() != null && !filter.modality().isBlank()) {
            where.append(" AND p.modality = :modality");
            params.addValue("modality", filter.modality().trim().toUpperCase());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM mp_plans p" + where, params, Long.class);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<PlanRow> rows = jdbc.query("SELECT p.* FROM mp_plans p" + where
                        + JdbcSupport.orderBy(pageable, SORTS, "p.code ASC, p.version DESC") + " LIMIT :limit OFFSET :offset",
                params, (rs, i) -> row(rs));
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    @Override
    public List<PlanRow> assignable(long companyId, String q, LocalDate on, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource("company", companyId).addValue("on", on)
                .addValue("limit", limit).addValue("q", JdbcSupport.like(q));
        return jdbc.query("""
                SELECT p.* FROM mp_plans p
                WHERE p.company_id = :company AND p.status = 'ACTIVE' AND (p.valid_to IS NULL OR p.valid_to >= :on)
                  AND (LOWER(p.code) LIKE :q OR LOWER(p.name) LIKE :q)
                ORDER BY p.code LIMIT :limit""", params, (rs, i) -> row(rs));
    }

    @Override
    public void addEvent(long companyId, long planId, String type, String detail, String actorId, String actorName, Instant now) {
        jdbc.update("""
                INSERT INTO mp_plan_events (company_id, plan_id, event_type, detail, actor_id, actor_name, created_at)
                VALUES (:company, :plan, :type, :detail, :actor, :actorName, :now)""",
                new MapSqlParameterSource("company", companyId).addValue("plan", planId).addValue("type", type)
                        .addValue("detail", detail).addValue("actor", actorId).addValue("actorName", actorName)
                        .addValue("now", utc(now)));
    }

    @Override
    public List<PlanEventDto> events(long companyId, long planId, int limit) {
        return jdbc.query("""
                SELECT id, event_type, detail, actor_id, actor_name, created_at FROM mp_plan_events
                WHERE company_id = :company AND plan_id = :plan ORDER BY created_at DESC, id DESC LIMIT :limit""",
                new MapSqlParameterSource("company", companyId).addValue("plan", planId).addValue("limit", limit),
                (rs, i) -> new PlanEventDto(rs.getLong("id"), rs.getString("event_type"), rs.getString("detail"),
                        rs.getString("actor_id"), rs.getString("actor_name"), instant(rs, "created_at")));
    }

    private static MapSqlParameterSource params(CommercialPlan plan) {
        PlanTerms t = plan.terms();
        return new MapSqlParameterSource("name", plan.name()).addValue("description", plan.description())
                .addValue("modality", t.modality().name()).addValue("currency", t.currency())
                .addValue("fee", t.monthlyFee()).addValue("pct", t.commissionPercent()).addValue("fixed", t.fixedFeePerSale())
                .addValue("base", t.commissionBase().name()).addValue("shipping", t.includeShipping() ? 1 : 0)
                .addValue("taxes", t.includeTaxes() ? 1 : 0).addValue("accrual", t.accrualEvent().name())
                .addValue("window", t.returnWindowDays()).addValue("refund", t.refundPolicy().name())
                .addValue("billing", t.feeBilling().name()).addValue("grace", t.graceDays())
                .addValue("interest", t.lateInterestMonthlyPercent()).addValue("storage", t.storageFeePerUnitDay())
                .addValue("from", plan.validFrom())
                .addValue("to", plan.validTo());
    }

    private static PlanRow row(ResultSet rs) throws SQLException {
        PlanTerms terms = PlanTerms.of(Modality.valueOf(rs.getString("modality")), rs.getString("currency"),
                rs.getBigDecimal("monthly_fee"), rs.getBigDecimal("commission_percent"),
                rs.getBigDecimal("fixed_fee_per_sale"), CommissionBase.valueOf(rs.getString("commission_base")),
                rs.getBoolean("include_shipping"), rs.getBoolean("include_taxes"),
                AccrualEvent.valueOf(rs.getString("accrual_event")), rs.getInt("return_window_days"),
                RefundPolicy.valueOf(rs.getString("refund_policy")), FeeBilling.valueOf(rs.getString("fee_billing")),
                rs.getInt("grace_days"), rs.getBigDecimal("late_interest_monthly_percent"),
                rs.getBigDecimal("storage_fee_unit_day"));
        CommercialPlan plan = new CommercialPlan(rs.getLong("id"), rs.getString("code"), rs.getInt("version"),
                rs.getString("name"), rs.getString("description"), Status.valueOf(rs.getString("status")), terms,
                JdbcSupport.date(rs, "valid_from"), JdbcSupport.date(rs, "valid_to"));
        return new PlanRow(plan, rs.getInt("lock_version"), instant(rs, "published_at"), rs.getString("published_by"),
                instant(rs, "created_at"), rs.getString("created_by"), instant(rs, "updated_at"), rs.getString("updated_by"));
    }
}
