package com.essenza.draco.modules.sales.infrastructure.outbound.repositories.storefront;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.sales.application.output.storefront.StorefrontAccessStore;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReadStore;

/** Lecturas de la tienda pública y enlaces de seguimiento (JDBC). Todo con parámetros: nada se concatena del usuario. */
@Repository
public class StorefrontJdbcAdapter implements StorefrontReadStore, StorefrontAccessStore {

    /** Unidades propias vendibles en línea: bodegas activas que venden en línea (sin consignación, que es de terceros). */
    static final String OWN_AVAILABLE = """
            (SELECT COALESCE(SUM(GREATEST(si.on_hand - si.reserved, 0)), 0) FROM stock_items si
               JOIN warehouses w ON w.id_warehouse = si.warehouse_id
              WHERE si.sku_id = s.id_sku AND si.deleted = 0 AND w.deleted = 0 AND w.active = 1 AND w.allow_online = 1
                AND w.warehouse_type <> 'CONSIGNACION')""";
    /** Igual que las ofertas a la venta del marketplace: activas, con cantidad, de vendedores externos aprobados. */
    static final String OFFER_EXISTS = """
            EXISTS (SELECT 1 FROM mp_offers o JOIN mp_sellers m ON m.id = o.seller_id
                     WHERE o.sku_id = s.id_sku AND o.company_id = :company AND o.status = 'ACTIVE' AND o.available_quantity > 0
                       AND o.currency = :currency AND m.company_id = :company AND m.kind = 'EXTERNAL' AND m.status = 'APPROVED')""";
    static final String SELLABLE_SKU = "s.deleted = 0 AND s.active = 1 AND (" + OWN_AVAILABLE + " > 0 OR " + OFFER_EXISTS + ")";
    /** Productos con al menos una oferta a la venta de ese vendedor (:seller). */
    static final String SELLER_PRODUCT = """
            EXISTS (SELECT 1 FROM product_skus s JOIN mp_offers o ON o.sku_id = s.id_sku
                     WHERE s.product_id = p.id_product AND s.deleted = 0 AND s.active = 1 AND o.seller_id = :seller
                       AND o.company_id = :company AND o.status = 'ACTIVE' AND o.available_quantity > 0 AND o.currency = :currency)""";
    static final String VISIBLE_PRODUCT = """
            p.deleted = 0 AND p.status = 'ACTIVE'
              AND EXISTS (SELECT 1 FROM product_skus s WHERE s.product_id = p.id_product AND """ + " " + SELLABLE_SKU + ")";

    /** Tope para el LEAST cuando un lado no tiene precio (sin unidades propias o sin ofertas). */
    private static final String NO_PRICE = "999999999999";
    /**
     * "Desde" del producto, igual que la tarjeta: el menor precio entre los SKU propios con unidades en línea y las
     * ofertas activas de vendedores aprobados. Sirve para filtrar y ordenar por precio.
     */
    static final String FROM_PRICE = "LEAST(COALESCE((SELECT MIN(s.price) FROM product_skus s WHERE s.product_id = p.id_product"
            + " AND s.deleted = 0 AND s.active = 1 AND " + OWN_AVAILABLE + " > 0), " + NO_PRICE + "),"
            + " COALESCE((SELECT MIN(o.price) FROM mp_offers o JOIN mp_sellers m ON m.id = o.seller_id"
            + " JOIN product_skus s ON s.id_sku = o.sku_id WHERE s.product_id = p.id_product AND s.deleted = 0 AND s.active = 1"
            + " AND o.company_id = :company AND o.status = 'ACTIVE' AND o.available_quantity > 0 AND o.currency = :currency"
            + " AND m.company_id = :company AND m.kind = 'EXTERNAL' AND m.status = 'APPROVED'), " + NO_PRICE + "))";

    private final NamedParameterJdbcTemplate jdbc;

    public StorefrontJdbcAdapter(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public ProductPage search(long companyId, String currency, SearchFilter filter, String sort, int offset, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource("company", companyId).addValue("currency", currency);
        StringBuilder where = new StringBuilder(" WHERE ").append(VISIBLE_PRODUCT);
        String like = filter.like();
        Long categoryId = filter.categoryId();
        if (like != null) {
            where.append(" AND (p.name LIKE :like OR p.description LIKE :like OR EXISTS (SELECT 1 FROM product_skus x"
                    + " WHERE x.product_id = p.id_product AND x.deleted = 0 AND (x.code LIKE :like OR x.name LIKE :like)))");
            p.addValue("like", like);
        }
        if (categoryId != null) {
            where.append(" AND p.category_id = :category");
            p.addValue("category", categoryId);
        }
        if (filter.brandId() != null) {
            where.append(" AND p.brand_id = :brand");
            p.addValue("brand", filter.brandId());
        }
        if (filter.sellerId() != null) {
            where.append(" AND ").append(SELLER_PRODUCT);
            p.addValue("seller", filter.sellerId());
        }
        if (filter.minPrice() != null) {
            where.append(" AND ").append(FROM_PRICE).append(" >= :minPrice");
            p.addValue("minPrice", filter.minPrice());
        }
        if (filter.maxPrice() != null) {
            where.append(" AND ").append(FROM_PRICE).append(" <= :maxPrice");
            p.addValue("maxPrice", filter.maxPrice());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM products p" + where, p, Long.class);
        String order = switch (sort == null ? "NEWEST" : sort) {
            case "NAME" -> " ORDER BY p.name, p.id_product";
            case "PRICE_ASC" -> " ORDER BY " + FROM_PRICE + ", p.id_product DESC";
            case "PRICE_DESC" -> " ORDER BY " + FROM_PRICE + " DESC, p.id_product DESC";
            default -> " ORDER BY p.id_product DESC";
        };
        p.addValue("limit", limit).addValue("offset", offset);
        List<Long> ids = jdbc.queryForList("SELECT p.id_product FROM products p" + where + order + " LIMIT :limit OFFSET :offset", p,
                Long.class);
        return new ProductPage(ids, total == null ? 0 : total);
    }

    @Override
    public Optional<Long> productBySlug(String slugOrId) {
        MapSqlParameterSource p = new MapSqlParameterSource("slug", slugOrId);
        String sql = "SELECT id_product FROM products WHERE deleted = 0 AND status = 'ACTIVE' AND slug = :slug";
        if (slugOrId.matches("\\d{1,18}")) {
            sql = "SELECT id_product FROM products WHERE deleted = 0 AND status = 'ACTIVE' AND (slug = :slug OR id_product = :id)"
                    + " ORDER BY slug = :slug DESC";
            p.addValue("id", Long.valueOf(slugOrId));
        }
        return jdbc.queryForList(sql + " LIMIT 1", p, Long.class).stream().findFirst();
    }

    @Override
    public Map<Long, ProductRow> products(Collection<Long> ids) {
        Map<Long, ProductRow> result = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT p.id_product, p.slug, p.name, p.description, p.image_url, c.name AS category, b.name AS brand,
                       b.slug AS brand_slug
                  FROM products p
                  LEFT JOIN categories c ON c.id_category = p.category_id
                  LEFT JOIN brands b ON b.id_brand = p.brand_id
                 WHERE p.id_product IN (:ids)""", new MapSqlParameterSource("ids", ids), rs -> {
            result.put(rs.getLong(1), new ProductRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                    rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)));
        });
        return result;
    }

    @Override
    public List<SkuRow> skus(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("SELECT s.id_sku, s.product_id, s.code, s.name, s.image_url, s.is_default, s.price, s.compare_at_price, "
                        + OWN_AVAILABLE + " AS own_available FROM product_skus s JOIN products p ON p.id_product = s.product_id"
                        + " WHERE s.product_id IN (:ids) AND s.deleted = 0 AND s.active = 1 AND p.deleted = 0 AND p.status = 'ACTIVE'"
                        + " ORDER BY s.product_id, s.is_default DESC, s.id_sku",
                new MapSqlParameterSource("ids", productIds),
                (rs, i) -> new SkuRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getBoolean(6), rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getLong(9)));
    }

    @Override
    public List<OfferRow> offers(long companyId, String currency, Collection<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT o.id, o.sku_id, COALESCE(NULLIF(m.trade_name, ''), m.legal_name), o.price, o.available_quantity,
                       o.handling_days, o.item_condition, m.id
                  FROM mp_offers o JOIN mp_sellers m ON m.id = o.seller_id
                 WHERE o.sku_id IN (:skus) AND o.company_id = :company AND o.status = 'ACTIVE' AND o.available_quantity > 0
                   AND o.currency = :currency AND m.company_id = :company AND m.kind = 'EXTERNAL' AND m.status = 'APPROVED'
                 ORDER BY o.sku_id, o.price, o.id""",
                new MapSqlParameterSource("skus", skuIds).addValue("company", companyId).addValue("currency", currency),
                (rs, i) -> new OfferRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getBigDecimal(4), rs.getLong(5),
                        (Integer) rs.getObject(6, Integer.class), rs.getString(7), rs.getLong(8)));
    }

    @Override
    public Map<Long, List<String>> images(Collection<Long> productIds) {
        Map<Long, List<String>> result = new LinkedHashMap<>();
        if (productIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT product_id, url FROM product_images WHERE product_id IN (:ids) AND deleted = 0 AND url IS NOT NULL
                 ORDER BY product_id, position, id_image""", new MapSqlParameterSource("ids", productIds), rs -> {
            result.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2));
        });
        return result;
    }

    @Override
    public List<CategoryRow> categories(long companyId, String currency) {
        return jdbc.query("""
                SELECT c.id_category, c.name, c.slug, COUNT(*) AS products
                  FROM products p JOIN categories c ON c.id_category = p.category_id AND c.deleted = 0
                 WHERE """ + " " + VISIBLE_PRODUCT + """
                 GROUP BY c.id_category, c.name, c.slug ORDER BY c.name""",
                new MapSqlParameterSource("company", companyId).addValue("currency", currency),
                (rs, i) -> new CategoryRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4)));
    }

    @Override
    public List<BrandRow> brands(long companyId, String currency) {
        return jdbc.query("""
                SELECT b.id_brand, b.name, COUNT(*) AS products, b.slug
                  FROM products p JOIN brands b ON b.id_brand = p.brand_id
                 WHERE """ + " " + VISIBLE_PRODUCT + """
                 GROUP BY b.id_brand, b.name, b.slug ORDER BY b.name""",
                new MapSqlParameterSource("company", companyId).addValue("currency", currency),
                (rs, i) -> new BrandRow(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getString(4)));
    }

    @Override
    public Optional<BrandProfileRow> brand(long companyId, String currency, String slugOrId) {
        MapSqlParameterSource p = new MapSqlParameterSource("company", companyId).addValue("currency", currency)
                .addValue("slug", slugOrId).addValue("id", slugOrId.matches("\\d{1,18}") ? Long.valueOf(slugOrId) : -1L);
        String sql = "SELECT b.id_brand, b.name, b.slug, b.description,"
                + " (SELECT COUNT(*) FROM products p WHERE p.brand_id = b.id_brand AND " + VISIBLE_PRODUCT + ") AS products"
                + " FROM brands b WHERE (b.slug = :slug OR b.id_brand = :id) AND b.deleted = 0"
                + " ORDER BY b.slug = :slug DESC LIMIT 1";
        return jdbc.query(sql, p,
                (rs, i) -> new BrandProfileRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5)))
                .stream().filter(b -> b.products() > 0).findFirst();
    }

    @Override
    public Optional<SellerRow> seller(long companyId, String currency, long sellerId) {
        MapSqlParameterSource p = new MapSqlParameterSource("company", companyId).addValue("currency", currency)
                .addValue("seller", sellerId);
        String sql = "SELECT m.id, COALESCE(NULLIF(m.trade_name, ''), m.legal_name), m.city, m.description, m.website, m.approved_at,"
                + " (SELECT COUNT(*) FROM products p WHERE " + VISIBLE_PRODUCT + " AND " + SELLER_PRODUCT + ") AS products"
                + " FROM mp_sellers m"
                + " WHERE m.id = :seller AND m.company_id = :company AND m.kind = 'EXTERNAL' AND m.status = 'APPROVED'";
        return jdbc.query(sql, p,
                (rs, i) -> new SellerRow(rs.getLong(1), rs.getString(2), blankToNull(rs.getString(3)), blankToNull(rs.getString(4)),
                        blankToNull(rs.getString(5)), rs.getObject(6, LocalDateTime.class), rs.getInt(7)))
                .stream().findFirst();
    }

    @Override
    public List<PaymentOption> gateways() {
        return jdbc.query("""
                SELECT id_payment_type, name, provider FROM payment_types
                 WHERE deleted = 0 AND active = 1 AND kind = 'GATEWAY' AND provider IS NOT NULL ORDER BY position, id_payment_type""",
                new MapSqlParameterSource(), (rs, i) -> new PaymentOption(rs.getLong(1), rs.getString(2), rs.getString(3)));
    }

    @Override
    public List<LineRow> orderLines(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT po.order_id, po.product_name, po.sku_code, po.quantity, po.unit_price, po.total, po.sku_id, po.offer_id,
                       p.slug
                  FROM product_orders po LEFT JOIN products p ON p.id_product = po.product_id
                 WHERE po.order_id IN (:ids) AND po.deleted = 0 ORDER BY po.order_id, po.id_product_order""",
                new MapSqlParameterSource("ids", orderIds),
                (rs, i) -> new LineRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBigDecimal(5),
                        rs.getBigDecimal(6), rs.getObject(7, Long.class), rs.getObject(8, Long.class), rs.getString(9)));
    }

    @Override
    public Map<Long, DeliveryRow> deliveries(Collection<Long> orderIds) {
        Map<Long, DeliveryRow> result = new LinkedHashMap<>();
        if (orderIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT o.id_order, o.delivery_method, o.tracking_number, c.name AS carrier_name, s.name AS store_name,
                       CONCAT_WS(', ', NULLIF(s.address, ''), NULLIF(s.city, '')) AS store_address, s.opening_hours
                  FROM orders o
                  LEFT JOIN carriers c ON c.id_carrier = o.carrier_id
                  LEFT JOIN stores s ON s.id_store = o.pickup_store_id
                 WHERE o.id_order IN (:ids)""",
                new MapSqlParameterSource("ids", orderIds),
                rs -> {
                    boolean pickup = "PICKUP".equals(rs.getString("delivery_method"));
                    result.put(rs.getLong("id_order"), new DeliveryRow(rs.getLong("id_order"), pickup,
                            pickup ? null : blankToNull(rs.getString("carrier_name")),
                            pickup ? null : blankToNull(rs.getString("tracking_number")),
                            pickup ? blankToNull(rs.getString("store_name")) : null,
                            pickup ? blankToNull(rs.getString("store_address")) : null,
                            pickup ? blankToNull(rs.getString("opening_hours")) : null));
                });
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    @Override
    public Map<Long, SkuRef> skuRefs(Collection<Long> skuIds) {
        Map<Long, SkuRef> result = new LinkedHashMap<>();
        if (skuIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT s.id_sku, s.product_id, p.category_id, s.price FROM product_skus s JOIN products p ON p.id_product = s.product_id
                 WHERE s.id_sku IN (:ids) AND s.deleted = 0 AND s.active = 1 AND p.deleted = 0 AND p.status = 'ACTIVE'""",
                new MapSqlParameterSource("ids", skuIds), rs -> {
                    long category = rs.getLong(3);
                    result.put(rs.getLong(1), new SkuRef(rs.getLong(1), rs.getLong(2), rs.wasNull() ? null : category,
                            rs.getBigDecimal(4)));
                });
        return result;
    }

    @Override
    public Map<Long, java.math.BigDecimal> ownPrices(Collection<Long> skuIds) {
        Map<Long, java.math.BigDecimal> result = new LinkedHashMap<>();
        if (skuIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT s.id_sku, s.price FROM product_skus s JOIN products p ON p.id_product = s.product_id
                 WHERE s.id_sku IN (:ids) AND s.deleted = 0 AND s.active = 1 AND p.deleted = 0 AND p.status = 'ACTIVE'""",
                new MapSqlParameterSource("ids", skuIds), rs -> {
                    result.put(rs.getLong(1), rs.getBigDecimal(2));
                });
        return result;
    }

    @Override
    public Map<Long, java.math.BigDecimal> offerPrices(long companyId, String currency, Collection<Long> offerIds) {
        Map<Long, java.math.BigDecimal> result = new LinkedHashMap<>();
        if (offerIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT id, price FROM mp_offers
                 WHERE id IN (:ids) AND company_id = :company AND currency = :currency AND status = 'ACTIVE'""",
                new MapSqlParameterSource("ids", offerIds).addValue("company", companyId).addValue("currency", currency), rs -> {
                    result.put(rs.getLong(1), rs.getBigDecimal(2));
                });
        return result;
    }

    @Override
    public Optional<String> openCheckout(Collection<Long> orderIds, Instant now) {
        if (orderIds.isEmpty()) {
            return Optional.empty();
        }
        return jdbc.queryForList("""
                SELECT checkout_url FROM payments
                 WHERE order_id IN (:ids) AND deleted = 0 AND status = 'PENDING' AND checkout_url IS NOT NULL
                   AND (expires_at IS NULL OR expires_at > :now)
                 ORDER BY id_payment DESC LIMIT 1""",
                new MapSqlParameterSource("ids", orderIds).addValue("now", utc(now)), String.class).stream().findFirst();
    }

    // ── Enlaces de seguimiento ─────────────────────────────────────────────

    @Override
    public void insert(long purchaseId, long companyId, String tokenHash, String email, Instant now) {
        jdbc.update("""
                INSERT INTO storefront_access (purchase_id, company_id, token_hash, email, created_at)
                VALUES (:purchase, :company, :hash, :email, :now)""",
                new MapSqlParameterSource("purchase", purchaseId).addValue("company", companyId).addValue("hash", tokenHash)
                        .addValue("email", email).addValue("now", utc(now)));
    }

    @Override
    public Optional<Access> find(String tokenHash) {
        return jdbc.query("SELECT purchase_id, company_id FROM storefront_access WHERE token_hash = :hash",
                new MapSqlParameterSource("hash", tokenHash), (rs, i) -> new Access(rs.getLong(1), rs.getLong(2))).stream().findFirst();
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
