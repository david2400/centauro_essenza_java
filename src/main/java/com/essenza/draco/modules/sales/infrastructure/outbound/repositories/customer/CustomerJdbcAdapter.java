package com.essenza.draco.modules.sales.infrastructure.outbound.repositories.customer;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.sales.application.output.repository.CustomerRegistry;
import com.essenza.draco.modules.sales.domain.model.customer.CustomerProfile;
import com.essenza.draco.shared.exceptions.ConflictException;

/**
 * Clientes en MySQL (JDBC, misma transacción que JPA). Las órdenes se relacionan por documento:
 * mismo número y mismo tipo (o sin tipo, en órdenes antiguas).
 */
@Repository
public class CustomerJdbcAdapter implements CustomerRegistry {

    private static final String MATCH = "o.customer_doc_number = %s AND (o.customer_doc_type = %s OR o.customer_doc_type IS NULL)";
    private static final String CHARGED = "o.total - COALESCE(o.returned_amount, 0)";
    private static final Map<String, String> SORTS = Map.of("id", "x.id_customer", "name", "x.name", "createdAt", "x.created_at",
            "lastOrderAt", "x.last_order_at", "totalSpent", "x.total_spent", "ordersCount", "x.orders_count");

    private final NamedParameterJdbcTemplate jdbc;

    public CustomerJdbcAdapter(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public long insert(long companyId, CustomerProfile p, String source, String actorId, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update("""
                    INSERT INTO customers (company_id, kind, doc_type, doc_number, name, email, phone, billing_address,
                      billing_city, store_user_id, marketing_consent, consent_at, notes, source, lock_version, created_at,
                      created_by, updated_at, updated_by)
                    VALUES (:company, :kind, :docType, :docNumber, :name, :email, :phone, :address, :city, :storeUser,
                      :consent, :consentAt, :notes, :source, 0, :now, :actor, :now, :actor)""",
                    params(p).addValue("company", companyId).addValue("source", source).addValue("now", utc(now))
                            .addValue("actor", actorId), keys, new String[] {"id_customer"});
        } catch (DataIntegrityViolationException ex) {
            throw duplicate(ex);
        }
        return keys.getKey().longValue();
    }

    @Override
    public boolean update(long companyId, long customerId, int lockVersion, CustomerProfile p, String actorId, Instant now) {
        try {
            return jdbc.update("""
                    UPDATE customers SET kind = :kind, doc_type = :docType, doc_number = :docNumber, name = :name,
                      email = :email, phone = :phone, billing_address = :address, billing_city = :city,
                      store_user_id = :storeUser, marketing_consent = :consent, consent_at = :consentAt, notes = :notes,
                      lock_version = lock_version + 1, updated_at = :now, updated_by = :actor
                    WHERE id_customer = :id AND company_id = :company AND lock_version = :lock""",
                    params(p).addValue("id", customerId).addValue("company", companyId).addValue("lock", lockVersion)
                            .addValue("now", utc(now)).addValue("actor", actorId)) == 1;
        } catch (DataIntegrityViolationException ex) {
            throw duplicate(ex);
        }
    }

    @Override
    public Optional<CustomerRow> find(long companyId, long customerId) {
        return jdbc.query("SELECT x.* FROM (" + withActivity() + ") x WHERE x.id_customer = :id",
                new MapSqlParameterSource("company", companyId).addValue("id", customerId), (rs, i) -> row(rs)).stream().findFirst();
    }

    @Override
    public Page<CustomerRow> search(long companyId, String q, Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource("company", companyId);
        if (q != null && !q.isBlank()) {
            where.append(" AND (LOWER(x.name) LIKE :q OR LOWER(x.doc_number) LIKE :q OR LOWER(COALESCE(x.email, '')) LIKE :q")
                    .append(" OR LOWER(COALESCE(x.phone, '')) LIKE :q)");
            String value = q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            params.addValue("q", "%" + value + "%");
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM customers x WHERE x.company_id = :company"
                + where.substring(" WHERE 1 = 1".length()), params, Long.class);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<CustomerRow> rows = jdbc.query("SELECT x.* FROM (" + withActivity() + ") x" + where + orderBy(pageable)
                + " LIMIT :limit OFFSET :offset", params, (rs, i) -> row(rs));
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    @Override
    public int importFromOrders(long companyId, String actorId, Instant now) {
        return jdbc.update("""
                INSERT INTO customers (company_id, kind, doc_type, doc_number, name, email, phone, billing_address, billing_city,
                  store_user_id, marketing_consent, source, lock_version, created_at, created_by, updated_at, updated_by)
                SELECT :company, CASE WHEN t.customer_kind = 'COMPANY' OR t.doc_type = 'NIT' THEN 'COMPANY' ELSE 'PERSON' END,
                       t.doc_type, t.customer_doc_number, LEFT(t.customer_name, 200), LEFT(t.customer_email, 160),
                       LEFT(t.customer_phone, 40), LEFT(t.billing_address, 200), LEFT(t.billing_city, 120), t.customer_id, 0,
                       'ORDERS', 0, :now, :actor, :now, :actor
                FROM (SELECT o.customer_kind, o.customer_doc_number, o.customer_name, o.customer_email, o.customer_phone,
                             o.billing_address, o.billing_city, o.customer_id,
                             COALESCE(UPPER(o.customer_doc_type), 'CC') AS doc_type,
                             ROW_NUMBER() OVER (PARTITION BY COALESCE(UPPER(o.customer_doc_type), 'CC'), o.customer_doc_number
                                                ORDER BY o.created_at DESC, o.id_order DESC) AS rn
                      FROM orders o
                      WHERE o.customer_doc_number IS NOT NULL AND TRIM(o.customer_doc_number) <> ''
                        AND o.customer_name IS NOT NULL AND TRIM(o.customer_name) <> '') t
                WHERE t.rn = 1 AND t.doc_type IN ('CC', 'CE', 'NIT', 'PP', 'TI', 'PEP')
                  AND NOT EXISTS (SELECT 1 FROM customers c WHERE c.company_id = :company AND c.doc_type = t.doc_type
                                  AND c.doc_number = t.customer_doc_number)""",
                new MapSqlParameterSource("company", companyId).addValue("now", utc(now)).addValue("actor", actorId));
    }

    /** Campos que una orden puede completar si el cliente los tiene vacíos. */
    private static final String FILLABLE = "(NULLIF(email, '') IS NULL AND VALUES(email) IS NOT NULL)"
            + " OR (NULLIF(phone, '') IS NULL AND VALUES(phone) IS NOT NULL)"
            + " OR (NULLIF(billing_address, '') IS NULL AND VALUES(billing_address) IS NOT NULL)"
            + " OR (NULLIF(billing_city, '') IS NULL AND VALUES(billing_city) IS NOT NULL)"
            + " OR (store_user_id IS NULL AND VALUES(store_user_id) IS NOT NULL)";

    @Override
    public boolean upsertFromOrder(long companyId, com.essenza.draco.modules.sales.domain.model.order.CustomerInfo info,
                                   String actorId, Instant now) {
        // MySQL evalúa las asignaciones en orden: versión y auditoría se calculan con los valores previos.
        int rows = jdbc.update("""
                INSERT INTO customers (company_id, kind, doc_type, doc_number, name, email, phone, billing_address,
                  billing_city, store_user_id, marketing_consent, source, lock_version, created_at, created_by, updated_at,
                  updated_by)
                VALUES (:company, :kind, :docType, :docNumber, LEFT(:name, 200), LEFT(:email, 160), LEFT(:phone, 40),
                  LEFT(:address, 200), LEFT(:city, 120), :storeUser, 0, 'ORDERS', 0, :now, :actor, :now, :actor)
                ON DUPLICATE KEY UPDATE
                  lock_version = lock_version + IF(%1$s, 1, 0),
                  updated_by = IF(%1$s, VALUES(updated_by), updated_by),
                  updated_at = IF(%1$s, VALUES(updated_at), updated_at),
                  email = COALESCE(NULLIF(email, ''), VALUES(email)),
                  phone = COALESCE(NULLIF(phone, ''), VALUES(phone)),
                  billing_address = COALESCE(NULLIF(billing_address, ''), VALUES(billing_address)),
                  billing_city = COALESCE(NULLIF(billing_city, ''), VALUES(billing_city)),
                  store_user_id = COALESCE(store_user_id, VALUES(store_user_id))""".formatted(FILLABLE),
                new MapSqlParameterSource("company", companyId).addValue("kind", info.kind().name())
                        .addValue("docType", info.docType()).addValue("docNumber", info.docNumber())
                        .addValue("name", info.name()).addValue("email", info.email()).addValue("phone", info.phone())
                        .addValue("address", info.billingAddress()).addValue("city", info.billingCity())
                        .addValue("storeUser", info.customerId()).addValue("now", utc(now)).addValue("actor", actorId));
        return rows > 0;
    }

    @Override
    public Optional<Long> findIdByDocument(long companyId, String docType, String docNumber) {
        return jdbc.query("SELECT id_customer FROM customers WHERE company_id = :company AND doc_type = :type AND doc_number = :doc",
                new MapSqlParameterSource("company", companyId).addValue("type", docType).addValue("doc", docNumber),
                (rs, i) -> rs.getLong(1)).stream().findFirst();
    }

    @Override
    public Activity activity(String docType, String docNumber) {
        MapSqlParameterSource params = new MapSqlParameterSource("type", docType).addValue("doc", docNumber);
        String match = MATCH.formatted(":doc", ":type");
        Activity base = jdbc.query("""
                SELECT COUNT(*) AS n, COALESCE(SUM(o.state <> 'CANCELLED'), 0) AS active, COALESCE(SUM(o.state = 'CANCELLED'), 0) AS cancelled,
                       COALESCE(SUM(o.state = 'PENDING'), 0) AS pending,
                       COALESCE(SUM(CASE WHEN o.state <> 'CANCELLED' THEN %1$s ELSE 0 END), 0) AS spent,
                       COALESCE(SUM(COALESCE(o.returned_amount, 0)), 0) AS returned,
                       COALESCE(SUM(CASE WHEN o.state = 'PENDING' THEN o.total ELSE 0 END), 0) AS pending_amount,
                       COALESCE(SUM(CASE WHEN o.state <> 'CANCELLED' AND s.kind = 'EXTERNAL' THEN %1$s ELSE 0 END), 0) AS marketplace,
                       COUNT(DISTINCT o.purchase_id) AS purchases, MIN(o.created_at) AS first_at, MAX(o.created_at) AS last_at
                FROM orders o LEFT JOIN mp_sellers s ON s.id = o.seller_id
                WHERE %2$s""".formatted(CHARGED, match), params,
                (rs, i) -> new Activity(rs.getLong("n"), rs.getLong("active"), rs.getLong("cancelled"), rs.getLong("pending"),
                        money(rs, "spent"), money(rs, "returned"), money(rs, "pending_amount"), money(rs, "marketplace"),
                        rs.getLong("purchases"), instant(rs, "first_at"), instant(rs, "last_at"), List.of())).get(0);
        List<ChannelTotal> channels = jdbc.query("""
                SELECT o.channel, COUNT(*) AS n, COALESCE(SUM(CASE WHEN o.state <> 'CANCELLED' THEN %1$s ELSE 0 END), 0) AS spent
                FROM orders o WHERE %2$s GROUP BY o.channel ORDER BY spent DESC""".formatted(CHARGED, match), params,
                (rs, i) -> new ChannelTotal(rs.getString("channel"), rs.getLong("n"), money(rs, "spent")));
        return new Activity(base.orders(), base.activeOrders(), base.cancelled(), base.pendingOrders(), base.spent(),
                base.returned(), base.pendingAmount(), base.marketplaceSpent(), base.purchases(), base.firstOrderAt(),
                base.lastOrderAt(), channels);
    }

    @Override
    public List<RecentOrder> recentOrders(String docType, String docNumber, int limit) {
        return jdbc.query("""
                SELECT o.id_order, o.order_number, o.state, o.total, o.returned_amount, o.channel, o.purchase_id, o.created_at,
                       CASE WHEN s.kind = 'EXTERNAL' THEN 1 ELSE 0 END AS third_party
                FROM orders o LEFT JOIN mp_sellers s ON s.id = o.seller_id
                WHERE %s ORDER BY o.created_at DESC, o.id_order DESC LIMIT :limit""".formatted(MATCH.formatted(":doc", ":type")),
                new MapSqlParameterSource("type", docType).addValue("doc", docNumber).addValue("limit", limit),
                (rs, i) -> {
                    long purchase = rs.getLong("purchase_id");
                    Long purchaseId = rs.wasNull() ? null : purchase;
                    return new RecentOrder(rs.getLong("id_order"), rs.getString("order_number"), rs.getString("state"),
                            money(rs, "total"), money(rs, "returned_amount"), rs.getString("channel"),
                            rs.getInt("third_party") == 1, purchaseId, instant(rs, "created_at"));
                });
    }

    @Override
    public Returns returns(String docType, String docNumber, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource("type", docType).addValue("doc", docNumber).addValue("limit", limit);
        String match = MATCH.formatted(":doc", ":type");
        Returns head = jdbc.query("""
                SELECT COUNT(*) AS n, COALESCE(SUM(d.state IN ('P', 'A', 'R', 'I')), 0) AS open_n,
                       COALESCE(SUM(d.refunded_amount), 0) AS refunded
                FROM order_devolutions d JOIN orders o ON o.id_order = d.order_id
                WHERE COALESCE(d.deleted, 0) = 0 AND %s""".formatted(match), params,
                (rs, i) -> new Returns(rs.getLong("n"), rs.getLong("open_n"), money(rs, "refunded"), List.of())).get(0);
        List<ReturnRow> recent = jdbc.query("""
                SELECT d.id_order_devolution, d.order_id, o.order_number, d.state, d.total_refund_amount, d.refunded_amount,
                       d.created_at
                FROM order_devolutions d JOIN orders o ON o.id_order = d.order_id
                WHERE COALESCE(d.deleted, 0) = 0 AND %s
                ORDER BY d.created_at DESC, d.id_order_devolution DESC LIMIT :limit""".formatted(match), params,
                (rs, i) -> new ReturnRow(rs.getLong("id_order_devolution"), rs.getLong("order_id"), rs.getString("order_number"),
                        rs.getString("state"), money(rs, "total_refund_amount"), money(rs, "refunded_amount"),
                        instant(rs, "created_at")));
        return new Returns(head.total(), head.open(), head.refunded(), recent);
    }

    @Override
    public Invoices invoices(String docType, String docNumber, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource("type", docType).addValue("doc", docNumber).addValue("limit", limit);
        String match = MATCH.formatted(":doc", ":type");
        Invoices head = jdbc.query("""
                SELECT COALESCE(SUM(i.kind = 'INVOICE'), 0) AS invoices, COALESCE(SUM(i.kind = 'CREDIT_NOTE'), 0) AS notes,
                       COALESCE(SUM(i.status IN ('PENDING', 'REJECTED')), 0) AS pending,
                       COALESCE(SUM(CASE WHEN i.status = 'ISSUED' AND i.kind = 'INVOICE' THEN i.total
                                         WHEN i.status = 'ISSUED' AND i.kind = 'CREDIT_NOTE' THEN -i.total ELSE 0 END), 0) AS invoiced
                FROM invoices i JOIN orders o ON o.id_order = i.order_id
                WHERE COALESCE(i.deleted, 0) = 0 AND %s""".formatted(match), params,
                (rs, i) -> new Invoices(rs.getLong("invoices"), rs.getLong("notes"), rs.getLong("pending"),
                        money(rs, "invoiced"), List.of())).get(0);
        List<InvoiceRow> recent = jdbc.query("""
                SELECT i.id_invoice, i.order_id, i.kind, i.full_number, i.status, i.total, i.issued_at
                FROM invoices i JOIN orders o ON o.id_order = i.order_id
                WHERE COALESCE(i.deleted, 0) = 0 AND %s ORDER BY i.issued_at DESC, i.id_invoice DESC LIMIT :limit""".formatted(match), params,
                (rs, i) -> new InvoiceRow(rs.getLong("id_invoice"), rs.getLong("order_id"), rs.getString("kind"),
                        rs.getString("full_number"), rs.getString("status"), money(rs, "total"), instant(rs, "issued_at")));
        return new Invoices(head.invoices(), head.creditNotes(), head.pending(), head.invoiced(), recent);
    }

    /** Clientes de la empresa con su actividad resumida (subconsultas por documento). */
    private static String withActivity() {
        String match = MATCH.formatted("c.doc_number", "c.doc_type");
        return """
                SELECT c.*,
                  (SELECT COUNT(*) FROM orders o WHERE %1$s) AS orders_count,
                  (SELECT COALESCE(SUM(CASE WHEN o.state <> 'CANCELLED' THEN %2$s ELSE 0 END), 0) FROM orders o WHERE %1$s) AS total_spent,
                  (SELECT MAX(o.created_at) FROM orders o WHERE %1$s) AS last_order_at
                FROM customers c WHERE c.company_id = :company""".formatted(match, CHARGED);
    }

    private static String orderBy(Pageable pageable) {
        StringJoiner joiner = new StringJoiner(", ");
        for (Sort.Order order : pageable.getSort()) {
            String column = SORTS.get(order.getProperty());
            if (column != null) {
                joiner.add(column + (order.isAscending() ? " ASC" : " DESC"));
            }
        }
        return " ORDER BY " + (joiner.length() == 0 ? "" : joiner + ", ") + "x.id_customer DESC";
    }

    private static MapSqlParameterSource params(CustomerProfile p) {
        return new MapSqlParameterSource("kind", p.info().kind().name()).addValue("docType", p.info().docType())
                .addValue("docNumber", p.info().docNumber()).addValue("name", p.info().name()).addValue("email", p.info().email())
                .addValue("phone", p.info().phone()).addValue("address", p.info().billingAddress())
                .addValue("city", p.info().billingCity()).addValue("storeUser", p.storeUserId())
                .addValue("consent", p.marketingConsent() ? 1 : 0).addValue("consentAt", utc(p.consentAt()))
                .addValue("notes", p.notes());
    }

    private static CustomerRow row(ResultSet rs) throws SQLException {
        long storeUser = rs.getLong("store_user_id");
        Long storeUserId = rs.wasNull() ? null : storeUser;
        return new CustomerRow(rs.getLong("id_customer"), rs.getString("kind"), rs.getString("doc_type"),
                rs.getString("doc_number"), rs.getString("name"), rs.getString("email"), rs.getString("phone"),
                rs.getString("billing_address"), rs.getString("billing_city"), storeUserId, rs.getInt("marketing_consent") == 1,
                instant(rs, "consent_at"), rs.getString("notes"), rs.getString("source"), rs.getInt("lock_version"),
                instant(rs, "created_at"), rs.getString("created_by"), instant(rs, "updated_at"), rs.getString("updated_by"),
                rs.getLong("orders_count"), money(rs, "total_spent"), instant(rs, "last_order_at"));
    }

    private static RuntimeException duplicate(DataIntegrityViolationException ex) {
        if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains("uk_customers_doc")) {
            return new ConflictException("Ya existe un cliente con ese documento.");
        }
        return ex;
    }

    private static BigDecimal money(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return (value == null ? BigDecimal.ZERO : value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static LocalDateTime utc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
