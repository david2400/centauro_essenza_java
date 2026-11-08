package com.essenza.draco.modules.sales.infrastructure.outbound.repositories.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.sales.application.dto.order.CustomerLookupDto;
import com.essenza.draco.modules.sales.application.dto.order.OrderDto;
import com.essenza.draco.modules.sales.application.dto.order.OrderFilter;
import com.essenza.draco.modules.sales.application.dto.order.OrderSummaryDto;
import com.essenza.draco.modules.sales.application.output.repository.OrderInsights;
import com.essenza.draco.modules.sales.domain.model.payment.OrderPaymentStatus;
import com.essenza.draco.modules.sales.infrastructure.outbound.mappers.OrderMapper;
import com.essenza.draco.modules.sales.infrastructure.outbound.persistence.mysql.shop.OrderEntity;
import com.essenza.draco.modules.sales.infrastructure.outbound.persistence.mysql.shop.PaymentEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Lecturas del panel de órdenes: búsqueda con filtros (criteria), KPIs agrupados por estado,
 * datos calculados por página (cobro, productos, preparación y nombres) y clientes recientes.
 */
@Repository
public class OrderInsightsAdapter implements OrderInsights {

    static final ZoneId ZONE = ZoneId.of("America/Bogota");
    static final Set<String> OPEN_STATES = Set.of("PENDING", "PAID", "PROCESSING", "READY", "SHIPPED");

    private final JpaOrderRepository jpa;
    private final OrderMapper mapper;
    private final EntityManager em;

    public OrderInsightsAdapter(JpaOrderRepository jpa, OrderMapper mapper, EntityManager em) {
        this.jpa = jpa;
        this.mapper = mapper;
        this.em = em;
    }

    @Override
    public Page<OrderDto> search(OrderFilter filter, Pageable pageable) {
        Specification<OrderEntity> spec = (root, query, cb) -> cb.and(predicates(filter, root, query, cb));
        return jpa.findAll(spec, pageable).map(mapper::toDto);
    }

    @Override
    public OrderSummaryDto summary(OrderFilter filter) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<OrderEntity> root = q.from(OrderEntity.class);
        Expression<BigDecimal> charged = cb.diff(cb.coalesce(root.<BigDecimal>get("total"), BigDecimal.ZERO),
                cb.coalesce(root.<BigDecimal>get("returnedAmount"), BigDecimal.ZERO));
        q.multiselect(root.<String>get("state"), cb.count(root), cb.sum(charged))
                .where(predicates(filter, root, q, cb))
                .groupBy(root.get("state"));
        Map<String, Long> byState = new LinkedHashMap<>();
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal marketplace = BigDecimal.ZERO;
        long count = 0;
        long valid = 0;
        for (Tuple t : em.createQuery(q).getResultList()) {
            String state = t.get(0, String.class);
            long n = t.get(1, Long.class);
            BigDecimal sum = t.get(2, BigDecimal.class);
            byState.merge(state == null ? "UNKNOWN" : state, n, Long::sum);
            count += n;
            if (!"CANCELLED".equals(state)) {
                valid += n;
                gross = gross.add(sum == null ? BigDecimal.ZERO : sum);
            }
        }
        {
            // V19: lo vendido por vendedores externos (no es ingreso propio). Consulta aparte, sin agrupar por la fórmula.
            CriteriaQuery<BigDecimal> mq = cb.createQuery(BigDecimal.class);
            Root<OrderEntity> m = mq.from(OrderEntity.class);
            Expression<BigDecimal> mCharged = cb.diff(cb.coalesce(m.<BigDecimal>get("total"), BigDecimal.ZERO),
                    cb.coalesce(m.<BigDecimal>get("returnedAmount"), BigDecimal.ZERO));
            List<Predicate> mPredicates = new ArrayList<>(List.of(predicates(filter, m, mq, cb)));
            mPredicates.add(cb.isTrue(m.<Boolean>get("thirdPartySale")));
            mPredicates.add(cb.notEqual(m.get("state"), "CANCELLED"));
            mq.select(cb.sum(mCharged)).where(mPredicates.toArray(Predicate[]::new));
            BigDecimal value = em.createQuery(mq).getSingleResult();
            marketplace = value == null ? BigDecimal.ZERO : value;
        }
        CriteriaQuery<Long> overdueQ = cb.createQuery(Long.class);
        Root<OrderEntity> o = overdueQ.from(OrderEntity.class);
        List<Predicate> overduePredicates = new ArrayList<>(List.of(predicates(filter, o, overdueQ, cb)));
        overduePredicates.add(overdue(o, cb));
        overdueQ.select(cb.count(o)).where(overduePredicates.toArray(Predicate[]::new));
        long overdue = em.createQuery(overdueQ).getSingleResult();
        BigDecimal average = valid == 0 ? BigDecimal.ZERO.setScale(2) : gross.divide(BigDecimal.valueOf(valid), 2, RoundingMode.HALF_UP);
        return new OrderSummaryDto(count, gross.setScale(2, RoundingMode.HALF_UP), average,
                byState.getOrDefault("PENDING", 0L),
                byState.getOrDefault("PAID", 0L) + byState.getOrDefault("PROCESSING", 0L),
                byState.getOrDefault("READY", 0L), byState.getOrDefault("SHIPPED", 0L),
                byState.getOrDefault("DELIVERED", 0L), byState.getOrDefault("CANCELLED", 0L), overdue, byState,
                gross.subtract(marketplace).setScale(2, RoundingMode.HALF_UP), marketplace.setScale(2, RoundingMode.HALF_UP));
    }

    private Predicate[] predicates(OrderFilter f, Root<OrderEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<Predicate> list = new ArrayList<>();
        if (f == null) {
            return list.toArray(Predicate[]::new);
        }
        String text = f.query() == null ? "" : f.query().trim();
        if (!text.isEmpty()) {
            String like = "%" + text.toLowerCase(Locale.ROOT) + "%";
            List<Predicate> any = new ArrayList<>();
            for (String field : List.of("orderNumber", "customerName", "customerDocNumber", "customerEmail", "customerPhone",
                    "complementaryOrder", "trackingNumber", "recipientName")) {
                any.add(cb.like(cb.lower(cb.coalesce(root.<String>get(field), "")), like));
            }
            if (text.matches("\\d{1,18}")) {
                any.add(cb.equal(root.get("id"), Long.valueOf(text)));
            }
            list.add(cb.or(any.toArray(Predicate[]::new)));
        }
        if (notBlank(f.state())) {
            list.add(cb.equal(root.get("state"), f.state().trim().toUpperCase(Locale.ROOT)));
        }
        if (notBlank(f.origin())) {
            // V19: MARKETPLACE = subpedidos de vendedores externos; OWN = el resto.
            Expression<Boolean> thirdParty = root.get("thirdPartySale");
            if ("MARKETPLACE".equalsIgnoreCase(f.origin().trim())) {
                list.add(cb.isTrue(thirdParty));
            } else if ("OWN".equalsIgnoreCase(f.origin().trim())) {
                list.add(cb.or(cb.isNull(thirdParty), cb.isFalse(thirdParty)));
            }
        }
        if (notBlank(f.channel())) {
            list.add(cb.equal(root.get("channel"), f.channel().trim().toUpperCase(Locale.ROOT)));
        }
        if (f.storeId() != null) {
            list.add(cb.equal(root.get("storeId"), f.storeId()));
        }
        if (notBlank(f.priority())) {
            list.add(cb.equal(root.get("priority"), f.priority().trim().toUpperCase(Locale.ROOT)));
        }
        if (notBlank(f.deliveryMethod())) {
            list.add(cb.equal(root.get("deliveryMethod"), f.deliveryMethod().trim().toUpperCase(Locale.ROOT)));
        }
        if (notBlank(f.period())) {
            Instant since = since(f.period().trim().toUpperCase(Locale.ROOT));
            if (since != null) {
                list.add(cb.greaterThanOrEqualTo(root.get("createdAt"), since));
            }
        }
        if (Boolean.TRUE.equals(f.overdue())) {
            list.add(overdue(root, cb));
        }
        if (notBlank(f.paymentStatus())) {
            Subquery<BigDecimal> paid = query.subquery(BigDecimal.class);
            Root<PaymentEntity> p = paid.from(PaymentEntity.class);
            Expression<BigDecimal> settled = cb.<BigDecimal>selectCase()
                    .when(cb.equal(p.get("status"), "APPROVED"),
                            cb.diff(p.<BigDecimal>get("amount"), cb.coalesce(p.<BigDecimal>get("refundedAmount"), BigDecimal.ZERO)))
                    .otherwise(BigDecimal.ZERO);
            paid.select(cb.coalesce(cb.sum(settled), BigDecimal.ZERO)).where(cb.equal(p.get("orderId"), root.get("id")));
            Expression<BigDecimal> due = cb.diff(cb.coalesce(root.<BigDecimal>get("total"), BigDecimal.ZERO),
                    cb.coalesce(root.<BigDecimal>get("returnedAmount"), BigDecimal.ZERO));
            // Igual que el estado calculado: devuelta por completo y sin saldo = pagada.
            Predicate settledByReturn = cb.and(cb.gt(cb.coalesce(root.<BigDecimal>get("returnedAmount"), BigDecimal.ZERO), BigDecimal.ZERO),
                    cb.le(due, BigDecimal.ZERO));
            switch (f.paymentStatus().trim().toUpperCase(Locale.ROOT)) {
                case "UNPAID" -> list.add(cb.and(cb.le(paid, BigDecimal.ZERO), cb.not(settledByReturn)));
                case "PARTIAL" -> list.add(cb.and(cb.gt(paid, BigDecimal.ZERO), cb.lessThan(paid, due)));
                case "PAID" -> list.add(cb.or(cb.and(cb.gt(paid, BigDecimal.ZERO), cb.greaterThanOrEqualTo(paid, due)),
                        cb.and(cb.le(paid, BigDecimal.ZERO), settledByReturn)));
                default -> {
                }
            }
        }
        return list.toArray(Predicate[]::new);
    }

    private static Predicate overdue(Root<OrderEntity> root, CriteriaBuilder cb) {
        return cb.and(cb.lessThan(root.get("promisedDate"), LocalDate.now(ZONE)), root.get("state").in(OPEN_STATES));
    }

    static Instant since(String period) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate start = switch (period) {
            case "TODAY" -> today;
            case "LAST_7" -> today.minusDays(6);
            case "LAST_30" -> today.minusDays(29);
            case "THIS_MONTH" -> today.withDayOfMonth(1);
            default -> null;
        };
        return start == null ? null : start.atStartOfDay(ZONE).toInstant();
    }

    // ── Datos calculados por página ───────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public List<OrderDto> enrich(List<OrderDto> orders) {
        if (orders.isEmpty()) {
            return orders;
        }
        List<Long> ids = orders.stream().map(OrderDto::getId).filter(Objects::nonNull).toList();
        Map<Long, BigDecimal> settled = new HashMap<>();
        ((List<Object[]>) em.createNativeQuery("""
                        SELECT order_id, SUM(CASE WHEN status = 'APPROVED' THEN amount - COALESCE(refunded_amount, 0) ELSE 0 END)
                          FROM payments WHERE deleted = 0 AND order_id IN (:ids) GROUP BY order_id""")
                .setParameter("ids", ids).getResultList())
                .forEach(r -> settled.put(((Number) r[0]).longValue(), decimal(r[1])));
        Map<Long, Object[]> lines = new HashMap<>();
        ((List<Object[]>) em.createNativeQuery("""
                        SELECT order_id, COUNT(*), COALESCE(SUM(quantity), 0), COALESCE(SUM(LEAST(picked_quantity, quantity)), 0),
                               COALESCE(SUM(subtotal - COALESCE(discount, 0)), 0)
                          FROM product_orders WHERE deleted = 0 AND order_id IN (:ids) GROUP BY order_id""")
                .setParameter("ids", ids).getResultList())
                .forEach(r -> lines.put(((Number) r[0]).longValue(), r));
        Map<Long, String> stores = names("SELECT id_store, name FROM stores WHERE id_store IN (:ids)",
                orders.stream().flatMap(o -> java.util.stream.Stream.of(o.getStoreId(), o.getPickupStoreId())).toList());
        Map<Long, String> warehouses = names("SELECT id_warehouse, name FROM warehouses WHERE id_warehouse IN (:ids)",
                orders.stream().map(OrderDto::getWarehouseId).toList());
        Map<Long, String> carriers = names("SELECT id_carrier, name FROM carriers WHERE id_carrier IN (:ids)",
                orders.stream().map(OrderDto::getCarrierId).toList());
        LocalDate today = LocalDate.now(ZONE);
        for (OrderDto o : orders) {
            BigDecimal total = o.getTotal() == null ? BigDecimal.ZERO : o.getTotal();
            BigDecimal returned = o.getReturnedAmount() == null ? BigDecimal.ZERO : o.getReturnedAmount();
            BigDecimal due = total.subtract(returned);
            BigDecimal paid = settled.getOrDefault(o.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            o.setPaidAmount(paid);
            o.setBalanceDue(due.subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            o.setPaymentStatus(paymentStatus(due, returned, paid).name());
            Object[] l = lines.get(o.getId());
            o.setItemsCount(l == null ? 0 : ((Number) l[1]).intValue());
            o.setUnitsCount(l == null ? 0 : ((Number) l[2]).intValue());
            o.setPickedUnits(l == null ? 0 : ((Number) l[3]).intValue());
            o.setItemsSubtotal(l == null ? BigDecimal.ZERO.setScale(2) : decimal(l[4]).setScale(2, RoundingMode.HALF_UP));
            o.setStoreName(stores.get(o.getStoreId()));
            o.setPickupStoreName(stores.get(o.getPickupStoreId()));
            o.setWarehouseName(warehouses.get(o.getWarehouseId()));
            o.setCarrierName(carriers.get(o.getCarrierId()));
            o.setOverdue(o.getPromisedDate() != null && o.getPromisedDate().isBefore(today) && OPEN_STATES.contains(o.getState()));
        }
        return orders;
    }

    static OrderPaymentStatus paymentStatus(BigDecimal due, BigDecimal returned, BigDecimal paid) {
        if (paid.signum() <= 0) {
            return returned.signum() > 0 && due.signum() <= 0 ? OrderPaymentStatus.PAID : OrderPaymentStatus.UNPAID;
        }
        int cmp = paid.compareTo(due);
        return cmp < 0 ? OrderPaymentStatus.PARTIAL : cmp == 0 ? OrderPaymentStatus.PAID : OrderPaymentStatus.OVERPAID;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, String> names(String sql, Collection<Long> ids) {
        Set<Long> unique = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> result = new HashMap<>();
        if (unique.isEmpty()) {
            return result;
        }
        ((List<Object[]>) em.createNativeQuery(sql).setParameter("ids", unique).getResultList())
                .forEach(r -> result.put(((Number) r[0]).longValue(), (String) r[1]));
        return result;
    }

    // ── Clientes recientes ────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public List<CustomerLookupDto> customers(String query, int limit) {
        String like = "%" + query.toLowerCase(Locale.ROOT) + "%";
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT customer_id, customer_kind, customer_doc_type, customer_doc_number, customer_name, customer_email,
                               customer_phone, billing_address, billing_city, recipient_name, recipient_phone, ship_address,
                               ship_address_detail, ship_country_id, ship_state_id, ship_city_id, ship_country, ship_state,
                               ship_city, ship_postal_code, created_at
                          FROM orders
                         WHERE deleted = 0 AND customer_name IS NOT NULL
                           AND (LOWER(customer_name) LIKE :q OR LOWER(COALESCE(customer_doc_number, '')) LIKE :q
                                OR LOWER(COALESCE(customer_email, '')) LIKE :q OR COALESCE(customer_phone, '') LIKE :q)
                         ORDER BY created_at DESC, id_order DESC
                         LIMIT 300""")
                .setParameter("q", like).getResultList();
        Map<String, Object[]> latest = new LinkedHashMap<>();
        Map<String, Long> counts = new HashMap<>();
        for (Object[] r : rows) {
            String key = r[3] != null ? "doc:" + r[3] : r[5] != null ? "mail:" + r[5] : "name:" + r[4] + "|" + r[6];
            latest.putIfAbsent(key, r);
            counts.merge(key, 1L, Long::sum);
        }
        return latest.entrySet().stream().limit(limit).map(e -> {
            Object[] r = e.getValue();
            return new CustomerLookupDto(longOf(r[0]), (String) r[1], (String) r[2], (String) r[3], (String) r[4], (String) r[5],
                    (String) r[6], (String) r[7], (String) r[8], (String) r[9], (String) r[10], (String) r[11], (String) r[12],
                    longOf(r[13]), longOf(r[14]), longOf(r[15]), (String) r[16], (String) r[17], (String) r[18], (String) r[19],
                    counts.get(e.getKey()), OrderEventLogAdapter.instant(r[20]));
        }).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public java.util.Optional<CustomerLookupDto> latestByDocument(String docNumber) {
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT customer_id, customer_kind, customer_doc_type, customer_doc_number, customer_name, customer_email,
                               customer_phone, billing_address, billing_city, recipient_name, recipient_phone, ship_address,
                               ship_address_detail, ship_country_id, ship_state_id, ship_city_id, ship_country, ship_state,
                               ship_city, ship_postal_code, created_at,
                               (SELECT COUNT(*) FROM orders x WHERE x.deleted = 0 AND x.customer_doc_number = :doc)
                          FROM orders
                         WHERE deleted = 0 AND customer_doc_number = :doc
                         ORDER BY created_at DESC, id_order DESC
                         LIMIT 1""")
                .setParameter("doc", docNumber).getResultList();
        return rows.stream().findFirst().map(r -> new CustomerLookupDto(longOf(r[0]), (String) r[1], (String) r[2], (String) r[3],
                (String) r[4], (String) r[5], (String) r[6], (String) r[7], (String) r[8], (String) r[9], (String) r[10],
                (String) r[11], (String) r[12], longOf(r[13]), longOf(r[14]), longOf(r[15]), (String) r[16], (String) r[17],
                (String) r[18], (String) r[19], ((Number) r[21]).longValue(), OrderEventLogAdapter.instant(r[20])));
    }

    private static Long longOf(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
