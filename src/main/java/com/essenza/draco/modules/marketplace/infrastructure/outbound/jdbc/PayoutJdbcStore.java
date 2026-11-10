package com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.BatchSummaryDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.ItemDto;
import com.essenza.draco.modules.marketplace.application.port.PayoutStore;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutRules.BankAccount;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutRules.BankRef;

/** Pagos a vendedores en MySQL (JDBC, misma transacción que el estado de cuenta). */
@Repository
public class PayoutJdbcStore implements PayoutStore {

    private static final String SELLER_NAME = "COALESCE(NULLIF(s.trade_name, ''), s.legal_name)";
    /** Línea u orden de cada asiento de venta cobrada por la tienda (la clave termina en el id). */
    private static final String KEY_ID = "CAST(SUBSTRING_INDEX(g.idempotency_key, ':', -1) AS UNSIGNED)";
    private static final String BATCH_SELECT = """
            SELECT b.*,
                   (SELECT COUNT(*) FROM mp_payout_items i WHERE i.batch_id = b.id) AS items,
                   (SELECT COUNT(*) FROM mp_payout_items i WHERE i.batch_id = b.id AND i.status = 'PENDING') AS pending,
                   (SELECT COUNT(*) FROM mp_payout_items i WHERE i.batch_id = b.id AND i.status = 'PAID') AS paid,
                   (SELECT COALESCE(SUM(i.amount), 0) FROM mp_payout_items i WHERE i.batch_id = b.id AND i.status <> 'REJECTED') AS total,
                   (SELECT COALESCE(SUM(i.amount), 0) FROM mp_payout_items i WHERE i.batch_id = b.id AND i.status = 'PAID') AS paid_total
            FROM mp_payout_batches b WHERE b.company_id = :company""";

    private final NamedParameterJdbcTemplate jdbc;

    public PayoutJdbcStore(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public List<BankRow> banks(boolean includeInactive) {
        return jdbc.query("SELECT * FROM mp_banks" + (includeInactive ? "" : " WHERE active = 1") + " ORDER BY position, name, id",
                new MapSqlParameterSource(), (rs, i) -> new BankRow(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("kind"), rs.getString("ach_code"), rs.getInt("active") == 1, rs.getInt("position"),
                        rs.getString("wompi_bank_id")));
    }

    @Override
    public Optional<BankRef> bank(long id) {
        return jdbc.query("SELECT id, name, kind, ach_code, active FROM mp_banks WHERE id = :id", new MapSqlParameterSource("id", id),
                (rs, i) -> new BankRef(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5) == 1))
                .stream().findFirst();
    }

    @Override
    public boolean bankCodeExists(String code) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM mp_banks WHERE code = :code", new MapSqlParameterSource("code", code),
                Integer.class);
        return n != null && n > 0;
    }

    @Override
    public long insertBank(String code, String name, String kind, String achCode, int position, String actorId, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO mp_banks (code, name, kind, ach_code, active, position, updated_by, updated_at)
                VALUES (:code, :name, :kind, :ach, 1, :position, :actor, :now)""",
                new MapSqlParameterSource("code", code).addValue("name", name).addValue("kind", kind).addValue("ach", achCode)
                        .addValue("position", position).addValue("actor", actorId).addValue("now", utc(now)), keys, new String[] {"id"});
        return keys.getKey().longValue();
    }

    @Override
    public boolean updateBank(long id, String name, String kind, String achCode, boolean active, int position, String actorId,
                              Instant now) {
        return jdbc.update("""
                UPDATE mp_banks SET name = :name, kind = :kind, ach_code = :ach, active = :active, position = :position,
                  updated_by = :actor, updated_at = :now WHERE id = :id""",
                new MapSqlParameterSource("id", id).addValue("name", name).addValue("kind", kind).addValue("ach", achCode)
                        .addValue("active", active ? 1 : 0).addValue("position", position).addValue("actor", actorId)
                        .addValue("now", utc(now))) == 1;
    }

    @Override
    public List<FormatRow> formats(boolean includeInactive) {
        return jdbc.query("SELECT * FROM mp_payout_formats" + (includeInactive ? "" : " WHERE active = 1") + " ORDER BY name, id",
                new MapSqlParameterSource(), (rs, i) -> formatRow(rs));
    }

    @Override
    public Optional<FormatRow> format(long id) {
        return jdbc.query("SELECT * FROM mp_payout_formats WHERE id = :id", new MapSqlParameterSource("id", id), (rs, i) -> formatRow(rs))
                .stream().findFirst();
    }

    @Override
    public boolean formatNameExists(String name, Long exceptId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM mp_payout_formats WHERE LOWER(name) = LOWER(:name) AND id <> :except",
                new MapSqlParameterSource("name", name).addValue("except", exceptId == null ? -1 : exceptId), Integer.class);
        return n != null && n > 0;
    }

    @Override
    public long insertFormat(String name, String extension, String encoding, String layoutJson, String actorId, String actorName,
                             Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO mp_payout_formats (name, file_extension, encoding, layout_json, active, updated_by, updated_by_name, updated_at)
                VALUES (:name, :ext, :enc, :layout, 1, :actor, :actorName, :now)""",
                new MapSqlParameterSource("name", name).addValue("ext", extension).addValue("enc", encoding).addValue("layout", layoutJson)
                        .addValue("actor", actorId).addValue("actorName", actorName).addValue("now", utc(now)), keys, new String[] {"id"});
        return keys.getKey().longValue();
    }

    @Override
    public boolean updateFormat(long id, String name, String extension, String encoding, String layoutJson, boolean active, String actorId,
                                String actorName, Instant now) {
        return jdbc.update("""
                UPDATE mp_payout_formats SET name = :name, file_extension = :ext, encoding = :enc, layout_json = :layout, active = :active,
                  updated_by = :actor, updated_by_name = :actorName, updated_at = :now WHERE id = :id""",
                new MapSqlParameterSource("id", id).addValue("name", name).addValue("ext", extension).addValue("enc", encoding)
                        .addValue("layout", layoutJson).addValue("active", active ? 1 : 0).addValue("actor", actorId)
                        .addValue("actorName", actorName).addValue("now", utc(now))) == 1;
    }

    private static FormatRow formatRow(ResultSet rs) throws SQLException {
        return new FormatRow(rs.getLong("id"), rs.getString("name"), rs.getString("file_extension"), rs.getString("encoding"),
                rs.getString("layout_json"), rs.getInt("active") == 1, rs.getString("updated_by_name"), instant(rs, "updated_at"));
    }

    @Override
    public Map<Long, SellerRef> sellers(long companyId) {
        Map<Long, SellerRef> result = new LinkedHashMap<>();
        jdbc.query("SELECT s.id, " + SELLER_NAME + " AS name, CONCAT(s.doc_type, ' ', s.doc_number) AS doc FROM mp_sellers s"
                        + " WHERE s.company_id = :company AND s.kind = 'EXTERNAL' ORDER BY name, s.id",
                new MapSqlParameterSource("company", companyId),
                rs -> {
                    result.put(rs.getLong("id"), new SellerRef(rs.getLong("id"), rs.getString("name"), rs.getString("doc")));
                });
        return result;
    }

    @Override
    public Map<Long, BigDecimal> held(long companyId, LocalDateTime releasedBefore) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        jdbc.query("SELECT g.seller_id, SUM(-g.amount) AS held FROM mp_seller_ledger g"
                        + " LEFT JOIN product_orders l ON g.idempotency_key NOT LIKE 'DSP-REFUND:%' AND l.id_product_order = " + KEY_ID
                        + " LEFT JOIN mp_disputes d ON g.idempotency_key LIKE 'DSP-REFUND:%' AND d.id = " + KEY_ID
                        + " JOIN orders o ON o.id_order = COALESCE(l.order_id, d.order_id)"
                        + " WHERE g.company_id = :company"
                        + " AND g.entry_type IN ('MARKETPLACE_SALE', 'MARKETPLACE_REVERSAL', 'CONSIGNMENT_SALE', 'CONSIGNMENT_REVERSAL')"
                        + " AND NOT (o.state = 'DELIVERED' AND o.delivered_at IS NOT NULL AND o.delivered_at < :released)"
                        + " GROUP BY g.seller_id",
                new MapSqlParameterSource("company", companyId).addValue("released", releasedBefore),
                rs -> {
                    result.put(rs.getLong("seller_id"), rs.getBigDecimal("held"));
                });
        return result;
    }

    /** La cuenta con el código de banco vigente del catálogo. */
    private static final String ACCOUNT_SELECT = "SELECT a.*, b.ach_code FROM mp_seller_bank_accounts a LEFT JOIN mp_banks b ON b.id = a.bank_id";

    @Override
    public Map<Long, Stored> bankAccounts(long companyId) {
        Map<Long, Stored> result = new LinkedHashMap<>();
        jdbc.query(ACCOUNT_SELECT + " WHERE a.company_id = :company", new MapSqlParameterSource("company", companyId),
                rs -> {
                    result.put(rs.getLong("seller_id"), stored(rs));
                });
        return result;
    }

    @Override
    public Optional<Stored> bankAccount(long companyId, long sellerId) {
        return jdbc.query(ACCOUNT_SELECT + " WHERE a.company_id = :company AND a.seller_id = :seller",
                new MapSqlParameterSource("company", companyId).addValue("seller", sellerId), (rs, i) -> stored(rs)).stream().findFirst();
    }

    @Override
    public void saveBankAccount(long companyId, long sellerId, BankAccount a, String actorId, String actorName, Instant now) {
        jdbc.update("""
                INSERT INTO mp_seller_bank_accounts (seller_id, company_id, bank_id, bank_name, account_type, key_type, account_number,
                  holder_name, holder_doc_type, holder_doc_number, updated_by, updated_by_name, updated_at)
                VALUES (:seller, :company, :bankId, :bank, :type, :keyType, :number, :holder, :docType, :doc, :actor, :actorName, :now)
                ON DUPLICATE KEY UPDATE bank_id = VALUES(bank_id), bank_name = VALUES(bank_name), account_type = VALUES(account_type),
                  key_type = VALUES(key_type),
                  account_number = VALUES(account_number), holder_name = VALUES(holder_name), holder_doc_type = VALUES(holder_doc_type),
                  holder_doc_number = VALUES(holder_doc_number), updated_by = VALUES(updated_by),
                  updated_by_name = VALUES(updated_by_name), updated_at = VALUES(updated_at)""",
                new MapSqlParameterSource("seller", sellerId).addValue("company", companyId).addValue("bankId", a.bankId())
                        .addValue("keyType", a.keyType()).addValue("bank", a.bankName())
                        .addValue("type", a.accountType()).addValue("number", a.accountNumber()).addValue("holder", a.holderName())
                        .addValue("docType", a.holderDocType()).addValue("doc", a.holderDocNumber()).addValue("actor", actorId)
                        .addValue("actorName", actorName).addValue("now", utc(now)));
    }

    @Override
    public Map<Long, PendingRef> pendingBySeller(long companyId) {
        Map<Long, PendingRef> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT i.seller_id, b.id, b.batch_number FROM mp_payout_items i JOIN mp_payout_batches b ON b.id = i.batch_id
                WHERE b.company_id = :company AND i.status = 'PENDING'""", new MapSqlParameterSource("company", companyId),
                rs -> {
                    result.put(rs.getLong(1), new PendingRef(rs.getLong(2), rs.getString(3)));
                });
        return result;
    }

    @Override
    public long insertBatch(long companyId, int holdDays, String note, String actorId, String actorName, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO mp_payout_batches (company_id, status, hold_days, note, created_by, created_by_name, created_at)
                VALUES (:company, 'OPEN', :hold, :note, :actor, :actorName, :now)""",
                new MapSqlParameterSource("company", companyId).addValue("hold", holdDays).addValue("note", note)
                        .addValue("actor", actorId).addValue("actorName", actorName).addValue("now", utc(now)), keys, new String[] {"id"});
        return keys.getKey().longValue();
    }

    @Override
    public void setBatchNumber(long id, String number) {
        jdbc.update("UPDATE mp_payout_batches SET batch_number = :n WHERE id = :id", new MapSqlParameterSource("n", number).addValue("id", id));
    }

    @Override
    public long insertItem(long batchId, SellerRef seller, BigDecimal amount, String currency, BankAccount a, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO mp_payout_items (batch_id, seller_id, seller_name, seller_doc, amount, currency, bank_name, bank_code,
                  bank_id, account_type, key_type, account_number, holder_name, holder_doc_type, holder_doc_number, status, updated_at)
                VALUES (:batch, :seller, :name, :doc, :amount, :currency, :bank, :bankCode, :bankId, :type, :keyType, :number, :holder,
                  :docType, :holderDoc, 'PENDING', :now)""",
                new MapSqlParameterSource("batch", batchId).addValue("seller", seller.id()).addValue("name", seller.name())
                        .addValue("doc", seller.doc()).addValue("amount", amount).addValue("currency", currency)
                        .addValue("bank", a.bankName()).addValue("bankCode", a.bankCode()).addValue("bankId", a.bankId())
                        .addValue("type", a.accountType())
                        .addValue("keyType", a.keyType()).addValue("number", a.accountNumber())
                        .addValue("holder", a.holderName()).addValue("docType", a.holderDocType())
                        .addValue("holderDoc", a.holderDocNumber()).addValue("now", utc(now)), keys, new String[] {"id"});
        return keys.getKey().longValue();
    }

    @Override
    public Optional<BatchRow> batch(long companyId, long id, boolean forUpdate) {
        if (forUpdate) {
            jdbc.query("SELECT id FROM mp_payout_batches WHERE id = :id AND company_id = :company FOR UPDATE",
                    new MapSqlParameterSource("id", id).addValue("company", companyId), rs -> { });
        }
        return jdbc.query(BATCH_SELECT + " AND b.id = :id", new MapSqlParameterSource("company", companyId).addValue("id", id),
                (rs, i) -> new BatchRow(summary(rs), rs.getInt("hold_days"), rs.getString("note"), rs.getString("status_reason"),
                        new DispatchState(rs.getString("dispatch_provider"), rs.getString("dispatch_status"),
                                rs.getString("provider_batch_id"), rs.getString("idempotency_key"), rs.getString("dispatch_detail"),
                                instant(rs, "dispatched_at"))))
                .stream().findFirst();
    }

    @Override
    public List<ItemDto> items(long batchId) {
        return jdbc.query("SELECT * FROM mp_payout_items WHERE batch_id = :batch ORDER BY seller_name, id",
                new MapSqlParameterSource("batch", batchId), (rs, i) -> item(rs));
    }

    @Override
    public Optional<ItemDto> lockItem(long batchId, long itemId) {
        return jdbc.query("SELECT * FROM mp_payout_items WHERE id = :id AND batch_id = :batch FOR UPDATE",
                new MapSqlParameterSource("id", itemId).addValue("batch", batchId), (rs, i) -> item(rs)).stream().findFirst();
    }

    @Override
    public boolean settleItem(long itemId, String status, String reference, LocalDate paidOn, String note, String actorId,
                              String actorName, Instant now) {
        return jdbc.update("""
                UPDATE mp_payout_items SET status = :status, reference = :reference, paid_on = :paidOn, note = :note,
                  actor_id = :actor, actor_name = :actorName, updated_at = :now
                WHERE id = :id AND status = 'PENDING'""",
                new MapSqlParameterSource("status", status).addValue("reference", reference).addValue("paidOn", paidOn)
                        .addValue("note", note).addValue("actor", actorId).addValue("actorName", actorName).addValue("now", utc(now))
                        .addValue("id", itemId)) == 1;
    }

    @Override
    public void setBatchStatus(long id, String status, String reason, Instant closedAt) {
        jdbc.update("""
                UPDATE mp_payout_batches SET status = :status, status_reason = COALESCE(:reason, status_reason), closed_at = :closed
                WHERE id = :id""", new MapSqlParameterSource("status", status).addValue("reason", reason)
                .addValue("closed", closedAt == null ? null : utc(closedAt)).addValue("id", id));
    }

    @Override
    public Page<BatchSummaryDto> search(long companyId, String status, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource("company", companyId);
        String where = "";
        if (status != null) {
            where = " AND b.status = :status";
            params.addValue("status", status);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM mp_payout_batches b WHERE b.company_id = :company" + where, params,
                Long.class);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<BatchSummaryDto> rows = jdbc.query(BATCH_SELECT + where + " ORDER BY b.created_at DESC, b.id DESC LIMIT :limit OFFSET :offset",
                params, (rs, i) -> summary(rs));
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    // ── Dispersión automática ──

    @Override
    public void setProviderBankId(long bankId, String providerBankId) {
        jdbc.update("UPDATE mp_banks SET wompi_bank_id = :pid WHERE id = :id",
                new MapSqlParameterSource("pid", providerBankId).addValue("id", bankId));
    }

    @Override
    public List<DispatchRow> dispatchRows(long batchId) {
        return jdbc.query("""
                SELECT i.id, i.seller_id, i.status, i.amount, i.account_type, i.key_type, i.account_number, i.holder_name,
                       i.holder_doc_type, i.holder_doc_number, s.email, b.wompi_bank_id, i.provider_reference, i.provider_status
                FROM mp_payout_items i
                JOIN mp_sellers s ON s.id = i.seller_id
                LEFT JOIN mp_banks b ON b.id = i.bank_id
                WHERE i.batch_id = :batch ORDER BY i.id""", new MapSqlParameterSource("batch", batchId),
                (rs, i) -> new DispatchRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getBigDecimal(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11),
                        rs.getString(12), rs.getString(13), rs.getString(14)));
    }

    @Override
    public void markDispatch(long batchId, String provider, String status, String providerBatchId, String idempotencyKey, String detail,
                             String actorId, Instant now) {
        jdbc.update("""
                UPDATE mp_payout_batches SET dispatch_provider = :provider, dispatch_status = :status,
                  provider_batch_id = COALESCE(:pid, provider_batch_id), idempotency_key = COALESCE(:key, idempotency_key),
                  dispatch_detail = :detail,
                  dispatched_at = CASE WHEN :status = 'SENT' AND :pid IS NULL THEN :now ELSE dispatched_at END,
                  dispatched_by = CASE WHEN :status = 'SENT' AND :pid IS NULL THEN :actor ELSE dispatched_by END
                WHERE id = :id""",
                new MapSqlParameterSource("provider", provider).addValue("status", status).addValue("pid", providerBatchId)
                        .addValue("key", idempotencyKey).addValue("detail", clip(detail, 500)).addValue("now", utc(now))
                        .addValue("actor", actorId).addValue("id", batchId));
    }

    @Override
    public void setItemProvider(long itemId, String reference, String txId, String status, String detail) {
        jdbc.update("""
                UPDATE mp_payout_items SET provider_reference = COALESCE(:ref, provider_reference),
                  provider_tx_id = COALESCE(:tx, provider_tx_id), provider_status = COALESCE(:status, provider_status),
                  provider_detail = COALESCE(:detail, provider_detail)
                WHERE id = :id""",
                new MapSqlParameterSource("ref", reference).addValue("tx", clip(txId, 64)).addValue("status", clip(status, 20))
                        .addValue("detail", clip(detail, 300)).addValue("id", itemId));
    }

    @Override
    public List<BatchKey> sentBatches(String provider) {
        return jdbc.query("""
                SELECT company_id, id FROM mp_payout_batches
                WHERE dispatch_provider = :provider AND dispatch_status = 'SENT' AND status = 'OPEN' ORDER BY id""",
                new MapSqlParameterSource("provider", provider), (rs, i) -> new BatchKey(rs.getLong(1), rs.getLong(2)));
    }

    @Override
    public Optional<BatchKey> batchByProvider(String provider, String providerBatchId) {
        return jdbc.query("SELECT company_id, id FROM mp_payout_batches WHERE dispatch_provider = :provider AND provider_batch_id = :pid",
                new MapSqlParameterSource("provider", provider).addValue("pid", providerBatchId),
                (rs, i) -> new BatchKey(rs.getLong(1), rs.getLong(2))).stream().findFirst();
    }

    private static String clip(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static BatchSummaryDto summary(ResultSet rs) throws SQLException {
        return new BatchSummaryDto(rs.getLong("id"), rs.getString("batch_number"), rs.getString("status"), rs.getInt("items"),
                rs.getInt("pending"), rs.getInt("paid"), rs.getBigDecimal("total"), rs.getBigDecimal("paid_total"),
                rs.getString("created_by_name"), instant(rs, "created_at"), instant(rs, "closed_at"));
    }

    private static ItemDto item(ResultSet rs) throws SQLException {
        return new ItemDto(rs.getLong("id"), rs.getLong("seller_id"), rs.getString("seller_name"), rs.getString("seller_doc"),
                rs.getBigDecimal("amount"), rs.getString("currency"), rs.getString("bank_name"), rs.getString("bank_code"),
                rs.getString("account_type"), rs.getString("key_type"), rs.getString("account_number"), rs.getString("holder_name"),
                rs.getString("holder_doc_type"),
                rs.getString("holder_doc_number"), rs.getString("status"), rs.getString("reference"),
                rs.getObject("paid_on", LocalDate.class), rs.getString("note"), rs.getString("actor_name"), instant(rs, "updated_at"),
                rs.getString("provider_status"), rs.getString("provider_detail"));
    }

    private static Stored stored(ResultSet rs) throws SQLException {
        long bankId = rs.getLong("bank_id");
        return new Stored(new BankAccount(rs.wasNull() ? null : bankId, rs.getString("ach_code"), rs.getString("bank_name"),
                rs.getString("account_type"), rs.getString("account_number"), rs.getString("key_type"), rs.getString("holder_name"),
                rs.getString("holder_doc_type"), rs.getString("holder_doc_number")),
                rs.getString("updated_by_name"), instant(rs, "updated_at"));
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }
}
