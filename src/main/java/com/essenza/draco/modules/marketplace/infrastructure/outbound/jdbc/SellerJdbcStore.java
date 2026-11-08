package com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc;

import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.instant;
import static com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.JdbcSupport.utc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
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

import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.SellerEventDto;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.SellerStatsDto;
import com.essenza.draco.modules.marketplace.application.port.SellerStore;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.DocType;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.DocumentKind;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.DocumentReview;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.Kind;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.Status;
import com.essenza.draco.modules.marketplace.domain.seller.SellerProfile;
import com.essenza.draco.modules.marketplace.domain.seller.SellerProfile.SellerContact;

/** Vendedores en MySQL con SQL explícito. Toda sentencia filtra por company_id. */
@Repository
public class SellerJdbcStore implements SellerStore {

    private static final Map<String, String> DUPLICATES = Map.of(
            "uk_mp_sellers_code", "Ya existe un vendedor con ese código.",
            "uk_mp_sellers_doc", "Ya existe un vendedor con ese tipo y número de documento.",
            "uk_mp_sellers_own", "La empresa ya tiene su vendedor propio; solo puede haber uno.");

    private static final Map<String, String> SORTS = Map.of(
            "code", "s.code", "legalName", "s.legal_name", "status", "s.status", "kind", "s.kind",
            "city", "s.city", "createdAt", "s.created_at", "updatedAt", "s.updated_at", "id", "s.id");

    private static final String COLUMNS = """
            s.id, s.code, s.kind, s.legal_name, s.trade_name, s.doc_type, s.doc_number, s.email, s.phone, s.website,
            s.address, s.city, s.description, s.account_manager_id, s.account_manager_name, s.status, s.status_reason,
            s.status_changed_at, s.approved_at, s.approved_by, s.lock_version, s.created_at, s.created_by,
            s.updated_at, s.updated_by""";

    private final NamedParameterJdbcTemplate jdbc;

    public SellerJdbcStore(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public long insert(long companyId, SellerProfile p, Status status, String actorId, Instant now) {
        MapSqlParameterSource params = profileParams(p).addValue("company", companyId).addValue("status", status.name())
                .addValue("actor", actorId).addValue("now", utc(now)).addValue("code", p.code());
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update("""
                    INSERT INTO mp_sellers (company_id, code, kind, legal_name, trade_name, doc_type, doc_number, email, phone,
                      website, address, city, description, account_manager_id, account_manager_name, status,
                      status_changed_at, lock_version, created_at, created_by, updated_at, updated_by)
                    VALUES (:company, :code, :kind, :legal, :trade, :docType, :docNumber, :email, :phone, :website, :address,
                      :city, :description, :managerId, :managerName, :status, :now, 0, :now, :actor, :now, :actor)""",
                    params, keys, new String[] {"id"});
        } catch (DataIntegrityViolationException ex) {
            throw JdbcSupport.duplicate(ex, DUPLICATES);
        }
        long id = keys.getKey().longValue();
        replaceContacts(id, p.contacts());
        return id;
    }

    @Override
    public void assignCode(long companyId, long sellerId, String code) {
        try {
            jdbc.update("UPDATE mp_sellers SET code = :code WHERE id = :id AND company_id = :company",
                    new MapSqlParameterSource("code", code).addValue("id", sellerId).addValue("company", companyId));
        } catch (DataIntegrityViolationException ex) {
            throw JdbcSupport.duplicate(ex, DUPLICATES);
        }
    }

    @Override
    public Optional<SellerRow> find(long companyId, long sellerId, boolean forUpdate) {
        List<SellerRow> rows = jdbc.query("SELECT " + COLUMNS + " FROM mp_sellers s WHERE s.id = :id AND s.company_id = :company"
                        + (forUpdate ? " FOR UPDATE" : ""),
                new MapSqlParameterSource("id", sellerId).addValue("company", companyId),
                (rs, i) -> row(rs, List.of()));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        SellerRow r = rows.get(0);
        return Optional.of(withContacts(r, contacts(sellerId)));
    }

    @Override
    public boolean updateProfile(long companyId, long sellerId, int expectedVersion, SellerProfile p, String actorId,
                                 Instant now) {
        MapSqlParameterSource params = profileParams(p).addValue("company", companyId).addValue("id", sellerId)
                .addValue("version", expectedVersion).addValue("actor", actorId).addValue("now", utc(now));
        int updated;
        try {
            updated = jdbc.update("""
                    UPDATE mp_sellers SET kind = :kind, legal_name = :legal, trade_name = :trade, doc_type = :docType,
                      doc_number = :docNumber, email = :email, phone = :phone, website = :website, address = :address,
                      city = :city, description = :description, account_manager_id = :managerId,
                      account_manager_name = :managerName, lock_version = lock_version + 1, updated_at = :now,
                      updated_by = :actor
                    WHERE id = :id AND company_id = :company AND lock_version = :version""", params);
        } catch (DataIntegrityViolationException ex) {
            throw JdbcSupport.duplicate(ex, DUPLICATES);
        }
        if (updated == 1) {
            replaceContacts(sellerId, p.contacts());
        }
        return updated == 1;
    }

    @Override
    public boolean updateStatus(long companyId, long sellerId, int expectedVersion, Status status, String reason,
                                boolean approving, String actorId, Instant now) {
        return jdbc.update("""
                UPDATE mp_sellers SET status = :status, status_reason = :reason, status_changed_at = :now,
                  approved_at = CASE WHEN :approving = 1 THEN :now ELSE approved_at END,
                  approved_by = CASE WHEN :approving = 1 THEN :actor ELSE approved_by END,
                  lock_version = lock_version + 1, updated_at = :now, updated_by = :actor
                WHERE id = :id AND company_id = :company AND lock_version = :version""",
                new MapSqlParameterSource("status", status.name()).addValue("reason", reason).addValue("now", utc(now))
                        .addValue("approving", approving ? 1 : 0).addValue("actor", actorId).addValue("id", sellerId)
                        .addValue("company", companyId).addValue("version", expectedVersion)) == 1;
    }

    @Override
    public Page<SellerRow> search(long companyId, SellerFilter filter, Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE s.company_id = :company");
        MapSqlParameterSource params = new MapSqlParameterSource("company", companyId);
        if (filter.q() != null && !filter.q().isBlank()) {
            where.append(" ").append("""
                     AND (LOWER(s.legal_name) LIKE :q OR LOWER(COALESCE(s.trade_name, '')) LIKE :q
                       OR LOWER(COALESCE(s.code, '')) LIKE :q OR s.doc_number LIKE :q OR LOWER(COALESCE(s.email, '')) LIKE :q
                       OR LOWER(COALESCE(s.city, '')) LIKE :q)""");
            params.addValue("q", JdbcSupport.like(filter.q()));
        }
        if (filter.status() != null && !filter.status().isBlank()) {
            where.append(" AND s.status = :status");
            params.addValue("status", filter.status().trim().toUpperCase());
        }
        if (filter.kind() != null && !filter.kind().isBlank()) {
            where.append(" AND s.kind = :kind");
            params.addValue("kind", filter.kind().trim().toUpperCase());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM mp_sellers s" + where, params, Long.class);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<SellerRow> rows = jdbc.query("SELECT " + COLUMNS + " FROM mp_sellers s" + where
                        + JdbcSupport.orderBy(pageable, SORTS, "s.id DESC") + " LIMIT :limit OFFSET :offset",
                params, (rs, i) -> row(rs, List.of()));
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    @Override
    public List<SellerRow> lookup(long companyId, String q, String kind, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource("company", companyId).addValue("q", JdbcSupport.like(q))
                .addValue("limit", limit).addValue("kind", kind == null || kind.isBlank() ? null : kind.trim().toUpperCase());
        return jdbc.query("SELECT " + COLUMNS + """
                 FROM mp_sellers s
                WHERE s.company_id = :company AND s.status <> 'CLOSED' AND (:kind IS NULL OR s.kind = :kind)
                  AND (LOWER(s.legal_name) LIKE :q OR LOWER(COALESCE(s.trade_name, '')) LIKE :q
                    OR LOWER(COALESCE(s.code, '')) LIKE :q OR s.doc_number LIKE :q)
                ORDER BY s.legal_name LIMIT :limit""", params, (rs, i) -> row(rs, List.of()));
    }

    @Override
    public SellerStatsDto stats(long companyId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) total,
                  COALESCE(SUM(status = 'DRAFT'), 0) draft, COALESCE(SUM(status = 'IN_REVIEW'), 0) in_review,
                  COALESCE(SUM(status = 'CHANGES_REQUESTED'), 0) changes, COALESCE(SUM(status = 'APPROVED'), 0) approved,
                  COALESCE(SUM(status = 'SUSPENDED'), 0) suspended, COALESCE(SUM(status = 'CLOSED'), 0) closed,
                  COALESCE(SUM(kind = 'EXTERNAL'), 0) external_count, COALESCE(SUM(kind = 'OWN'), 0) own_count
                FROM mp_sellers WHERE company_id = :company""", new MapSqlParameterSource("company", companyId),
                (rs, i) -> new SellerStatsDto(rs.getLong("total"), rs.getLong("draft"), rs.getLong("in_review"),
                        rs.getLong("changes"), rs.getLong("approved"), rs.getLong("suspended"), rs.getLong("closed"),
                        rs.getLong("external_count"), rs.getLong("own_count")));
    }

    @Override
    public void addEvent(long companyId, long sellerId, String type, Status from, Status to, String reason, String detail,
                         String actorId, String actorName, Instant now) {
        jdbc.update("""
                INSERT INTO mp_seller_events (company_id, seller_id, event_type, from_status, to_status, reason, detail,
                  actor_id, actor_name, created_at)
                VALUES (:company, :seller, :type, :from, :to, :reason, :detail, :actor, :actorName, :now)""",
                new MapSqlParameterSource("company", companyId).addValue("seller", sellerId).addValue("type", type)
                        .addValue("from", from == null ? null : from.name()).addValue("to", to == null ? null : to.name())
                        .addValue("reason", truncate(reason)).addValue("detail", truncate(detail))
                        .addValue("actor", actorId).addValue("actorName", actorName).addValue("now", utc(now)));
    }

    @Override
    public List<SellerEventDto> events(long companyId, long sellerId, int limit) {
        return jdbc.query("""
                SELECT id, event_type, from_status, to_status, reason, detail, actor_id, actor_name, created_at
                FROM mp_seller_events WHERE company_id = :company AND seller_id = :seller
                ORDER BY created_at DESC, id DESC LIMIT :limit""",
                new MapSqlParameterSource("company", companyId).addValue("seller", sellerId).addValue("limit", limit),
                (rs, i) -> new SellerEventDto(rs.getLong("id"), rs.getString("event_type"), rs.getString("from_status"),
                        rs.getString("to_status"), rs.getString("reason"), rs.getString("detail"), rs.getString("actor_id"),
                        rs.getString("actor_name"), instant(rs, "created_at")));
    }

    @Override
    public long insertDocument(long companyId, long sellerId, NewDocument d, String actorId, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO mp_seller_documents (company_id, seller_id, kind, file_name, content_type, size_bytes, sha256,
                  storage_key, review_status, uploaded_at, uploaded_by)
                VALUES (:company, :seller, :kind, :name, :type, :size, :sha, :key, 'PENDING', :now, :actor)""",
                new MapSqlParameterSource("company", companyId).addValue("seller", sellerId).addValue("kind", d.kind().name())
                        .addValue("name", d.fileName()).addValue("type", d.contentType()).addValue("size", d.sizeBytes())
                        .addValue("sha", d.sha256()).addValue("key", d.storageKey()).addValue("now", utc(now))
                        .addValue("actor", actorId), keys, new String[] {"id"});
        return keys.getKey().longValue();
    }

    @Override
    public void supersedeDocuments(long companyId, long sellerId, DocumentKind kind, long keepId, Instant now) {
        jdbc.update("""
                UPDATE mp_seller_documents SET superseded_at = :now
                WHERE company_id = :company AND seller_id = :seller AND kind = :kind AND id <> :keep AND superseded_at IS NULL""",
                new MapSqlParameterSource("now", utc(now)).addValue("company", companyId).addValue("seller", sellerId)
                        .addValue("kind", kind.name()).addValue("keep", keepId));
    }

    @Override
    public Optional<DocumentRow> findDocument(long companyId, long sellerId, long documentId) {
        return jdbc.query("SELECT * FROM mp_seller_documents WHERE id = :id AND seller_id = :seller AND company_id = :company",
                new MapSqlParameterSource("id", documentId).addValue("seller", sellerId).addValue("company", companyId),
                (rs, i) -> document(rs)).stream().findFirst();
    }

    @Override
    public List<DocumentRow> documents(long companyId, long sellerId, boolean includeSuperseded) {
        return jdbc.query("SELECT * FROM mp_seller_documents WHERE seller_id = :seller AND company_id = :company"
                        + (includeSuperseded ? "" : " AND superseded_at IS NULL") + " ORDER BY uploaded_at DESC, id DESC",
                new MapSqlParameterSource("seller", sellerId).addValue("company", companyId), (rs, i) -> document(rs));
    }

    @Override
    public void reviewDocument(long companyId, long documentId, DocumentReview review, String note, String actorId,
                               Instant now) {
        jdbc.update("""
                UPDATE mp_seller_documents SET review_status = :review, review_note = :note, reviewed_at = :now,
                  reviewed_by = :actor
                WHERE id = :id AND company_id = :company""",
                new MapSqlParameterSource("review", review.name()).addValue("note", note).addValue("now", utc(now))
                        .addValue("actor", actorId).addValue("id", documentId).addValue("company", companyId));
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    private void replaceContacts(long sellerId, List<SellerContact> contacts) {
        jdbc.update("DELETE FROM mp_seller_contacts WHERE seller_id = :seller", new MapSqlParameterSource("seller", sellerId));
        int position = 0;
        for (SellerContact c : contacts) {
            jdbc.update("""
                    INSERT INTO mp_seller_contacts (seller_id, position, name, role, email, phone, is_primary)
                    VALUES (:seller, :position, :name, :role, :email, :phone, :primary)""",
                    new MapSqlParameterSource("seller", sellerId).addValue("position", position++).addValue("name", c.name())
                            .addValue("role", c.role()).addValue("email", c.email()).addValue("phone", c.phone())
                            .addValue("primary", c.primary() ? 1 : 0));
        }
    }

    private List<SellerContact> contacts(long sellerId) {
        return jdbc.query("SELECT name, role, email, phone, is_primary FROM mp_seller_contacts WHERE seller_id = :seller ORDER BY position",
                new MapSqlParameterSource("seller", sellerId),
                (rs, i) -> new SellerContact(rs.getString("name"), rs.getString("role"), rs.getString("email"),
                        rs.getString("phone"), rs.getBoolean("is_primary")));
    }

    private static MapSqlParameterSource profileParams(SellerProfile p) {
        return new MapSqlParameterSource("kind", p.kind().name()).addValue("legal", p.legalName())
                .addValue("trade", p.tradeName()).addValue("docType", p.docType().name())
                .addValue("docNumber", p.docNumber()).addValue("email", p.email()).addValue("phone", p.phone())
                .addValue("website", p.website()).addValue("address", p.address()).addValue("city", p.city())
                .addValue("description", p.description()).addValue("managerId", p.accountManagerId())
                .addValue("managerName", p.accountManagerName());
    }

    private static SellerRow row(ResultSet rs, List<SellerContact> contacts) throws SQLException {
        // La ficha guardada ya fue validada al escribirla; se reconstruye con las mismas reglas.
        SellerProfile profile = new SellerProfile(rs.getString("code"), Kind.valueOf(rs.getString("kind")),
                rs.getString("legal_name"), rs.getString("trade_name"), DocType.valueOf(rs.getString("doc_type")),
                rs.getString("doc_number"), rs.getString("email"), rs.getString("phone"), rs.getString("website"),
                rs.getString("address"), rs.getString("city"), rs.getString("description"),
                rs.getString("account_manager_id"), rs.getString("account_manager_name"), contacts);
        return new SellerRow(rs.getLong("id"), profile, Status.valueOf(rs.getString("status")), rs.getString("status_reason"),
                instant(rs, "status_changed_at"), instant(rs, "approved_at"), rs.getString("approved_by"),
                rs.getInt("lock_version"), instant(rs, "created_at"), rs.getString("created_by"),
                instant(rs, "updated_at"), rs.getString("updated_by"));
    }

    private static SellerRow withContacts(SellerRow r, List<SellerContact> contacts) {
        SellerProfile p = r.profile();
        SellerProfile full = new SellerProfile(p.code(), p.kind(), p.legalName(), p.tradeName(), p.docType(), p.docNumber(),
                p.email(), p.phone(), p.website(), p.address(), p.city(), p.description(), p.accountManagerId(),
                p.accountManagerName(), contacts);
        return new SellerRow(r.id(), full, r.status(), r.statusReason(), r.statusChangedAt(), r.approvedAt(), r.approvedBy(),
                r.version(), r.createdAt(), r.createdBy(), r.updatedAt(), r.updatedBy());
    }

    private static DocumentRow document(ResultSet rs) throws SQLException {
        return new DocumentRow(rs.getLong("id"), rs.getLong("seller_id"), DocumentKind.valueOf(rs.getString("kind")),
                rs.getString("file_name"), rs.getString("content_type"), rs.getLong("size_bytes"), rs.getString("sha256"),
                rs.getString("storage_key"), DocumentReview.valueOf(rs.getString("review_status")),
                rs.getString("review_note"), instant(rs, "reviewed_at"), rs.getString("reviewed_by"),
                instant(rs, "superseded_at"), instant(rs, "uploaded_at"), rs.getString("uploaded_by"));
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
