package com.essenza.draco.modules.sales.infrastructure.outbound.repositories.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import com.essenza.draco.modules.sales.application.dto.payment.PaymentFilter;
import com.essenza.draco.modules.sales.application.output.repository.PaymentRepository;
import com.essenza.draco.modules.sales.domain.model.payment.GatewayProvider;
import com.essenza.draco.modules.sales.domain.model.payment.Payment;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentChannel;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentMethodKind;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentStatus;
import com.essenza.draco.modules.sales.infrastructure.outbound.persistence.mysql.shop.PaymentEntity;

@Repository
public class PaymentRepositoryAdapter implements PaymentRepository {

    private final JpaPaymentRepository jpa;

    @PersistenceContext
    private EntityManager em;

    public PaymentRepositoryAdapter(JpaPaymentRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Payment save(Payment payment) {
        // Al actualizar se modifica la entidad gestionada (conserva la versión optimista y la auditoría).
        PaymentEntity entity = payment.getId() == null ? new PaymentEntity()
                : jpa.findById(payment.getId()).orElseThrow(() -> new IllegalStateException("Pago no encontrado: " + payment.getId()));
        entity.setOrderId(payment.getOrderId());
        entity.setPaymentTypeId(payment.getMethodId());
        entity.setChannel(payment.getChannel().name());
        entity.setKind(payment.getKind().name());
        entity.setProvider(payment.getProvider() == null ? null : payment.getProvider().name());
        entity.setStatus(payment.getStatus().name());
        entity.setAmount(payment.getAmount());
        entity.setFeeAmount(payment.getFeeAmount());
        entity.setTenderedAmount(payment.getTenderedAmount());
        entity.setChangeAmount(payment.getChangeAmount());
        entity.setRefundedAmount(payment.getRefundedAmount());
        entity.setCurrency("COP");
        entity.setReference(payment.getReference());
        entity.setPaymentReference(payment.getPaymentReference());
        entity.setProviderReference(payment.getProviderReference());
        entity.setCheckoutUrl(payment.getCheckoutUrl());
        entity.setReturnUrl(payment.getReturnUrl());
        entity.setPayerEmail(payment.getPayerEmail());
        entity.setNotes(payment.getNotes());
        entity.setStatusDetail(truncate(payment.getStatusDetail(), 255));
        entity.setApprovedAt(payment.getApprovedAt());
        entity.setExpiresAt(payment.getExpiresAt());
        entity.setCashSessionId(payment.getCashSessionId());
        return toDomain(jpa.saveAndFlush(entity));
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return jpa.findById(id).map(PaymentRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Payment> lockById(Long id) {
        return jpa.lockById(id).map(PaymentRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Payment> lockByPaymentReference(String paymentReference) {
        return jpa.lockByPaymentReference(paymentReference).map(PaymentRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Payment> lockByProvider(String provider, String providerReference) {
        return jpa.lockByProvider(provider, providerReference).stream().findFirst().map(PaymentRepositoryAdapter::toDomain);
    }

    @Override
    public List<Long> findPendingOnline(Instant from, Instant to, int limit) {
        return jpa.findPendingOnlineIds(from, to, org.springframework.data.domain.PageRequest.of(0, Math.max(1, limit)));
    }

    @Override
    public List<Payment> findByOrderId(Long orderId) {
        return jpa.findByOrderIdOrderByIdAsc(orderId).stream().map(PaymentRepositoryAdapter::toDomain).toList();
    }

    @Override
    public Page<Payment> search(PaymentFilter filter, Pageable pageable) {
        Specification<PaymentEntity> spec = (root, query, cb) -> cb.and(predicates(filter, root, cb).toArray(Predicate[]::new));
        return jpa.findAll(spec, pageable).map(PaymentRepositoryAdapter::toDomain);
    }

    @Override
    public Totals totals(PaymentFilter filter) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // Totales generales: aprobado (incluye lo luego reembolsado), pendiente, reembolsado y comisiones.
        CriteriaQuery<Tuple> total = cb.createTupleQuery();
        Root<PaymentEntity> root = total.from(PaymentEntity.class);
        Expression<String> status = root.get("status");
        Expression<BigDecimal> amount = root.get("amount");
        Expression<BigDecimal> refunded = root.get("refundedAmount");
        Expression<BigDecimal> fee = root.get("feeAmount");
        Predicate settled = status.in(PaymentStatus.APPROVED.name(), PaymentStatus.REFUNDED.name());
        total.multiselect(cb.count(root),
                cb.sum(cb.<BigDecimal>selectCase().when(settled, amount).otherwise(BigDecimal.ZERO)),
                cb.sum(cb.<BigDecimal>selectCase().when(cb.equal(status, PaymentStatus.PENDING.name()), amount).otherwise(BigDecimal.ZERO)),
                cb.sum(cb.<BigDecimal>selectCase().when(settled, refunded).otherwise(BigDecimal.ZERO)),
                cb.sum(cb.<BigDecimal>selectCase().when(settled, fee).otherwise(BigDecimal.ZERO)))
                .where(predicates(filter, root, cb).toArray(Predicate[]::new));
        Tuple t = em.createQuery(total).getSingleResult();

        CriteriaQuery<Tuple> byMethod = cb.createTupleQuery();
        Root<PaymentEntity> m = byMethod.from(PaymentEntity.class);
        Predicate mSettled = m.<String>get("status").in(PaymentStatus.APPROVED.name(), PaymentStatus.REFUNDED.name());
        byMethod.multiselect(m.get("paymentTypeId"), cb.count(m),
                        cb.sum(cb.<BigDecimal>selectCase().when(mSettled, m.<BigDecimal>get("amount")).otherwise(BigDecimal.ZERO)),
                        cb.sum(cb.<BigDecimal>selectCase().when(mSettled, m.<BigDecimal>get("refundedAmount")).otherwise(BigDecimal.ZERO)),
                        cb.sum(cb.<BigDecimal>selectCase().when(mSettled, m.<BigDecimal>get("feeAmount")).otherwise(BigDecimal.ZERO)))
                .where(predicates(filter, m, cb).toArray(Predicate[]::new))
                .groupBy(m.get("paymentTypeId"));
        List<MethodTotals> methods = new ArrayList<>();
        for (Tuple row : em.createQuery(byMethod).getResultList()) {
            methods.add(new MethodTotals(row.get(0, Long.class), row.get(1, Long.class), money(row.get(2)),
                    money(row.get(3)), money(row.get(4))));
        }
        methods.sort((a, b) -> b.approved().compareTo(a.approved()));

        CriteriaQuery<Tuple> byStatus = cb.createTupleQuery();
        Root<PaymentEntity> s = byStatus.from(PaymentEntity.class);
        byStatus.multiselect(s.get("status"), cb.count(s), cb.sum(s.<BigDecimal>get("amount")))
                .where(predicates(filter, s, cb).toArray(Predicate[]::new))
                .groupBy(s.get("status"));
        List<StatusTotals> statuses = new ArrayList<>();
        for (Tuple row : em.createQuery(byStatus).getResultList()) {
            statuses.add(new StatusTotals(row.get(0, String.class), row.get(1, Long.class), money(row.get(2))));
        }
        return new Totals(t.get(0, Long.class), money(t.get(1)), money(t.get(2)), money(t.get(3)), money(t.get(4)),
                methods, statuses);
    }

    private static List<Predicate> predicates(PaymentFilter f, Root<PaymentEntity> root, CriteriaBuilder cb) {
        List<Predicate> list = new ArrayList<>();
        if (f == null) {
            return list;
        }
        if (f.status() != null && !f.status().isBlank()) {
            List<String> statuses = List.of(f.status().toUpperCase(Locale.ROOT).split(",")).stream().map(String::trim).toList();
            list.add(root.get("status").in(statuses));
        }
        if (f.paymentMethodId() != null) {
            list.add(cb.equal(root.get("paymentTypeId"), f.paymentMethodId()));
        }
        if (f.channel() != null && !f.channel().isBlank()) {
            list.add(cb.equal(root.get("channel"), f.channel().trim().toUpperCase(Locale.ROOT)));
        }
        if (f.provider() != null && !f.provider().isBlank()) {
            list.add(cb.equal(root.get("provider"), f.provider().trim().toUpperCase(Locale.ROOT)));
        }
        if (f.orderId() != null) {
            list.add(cb.equal(root.get("orderId"), f.orderId()));
        }
        if (f.from() != null) {
            list.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), f.from()));
        }
        if (f.to() != null) {
            list.add(cb.lessThan(root.<Instant>get("createdAt"), f.to()));
        }
        if (f.q() != null && !f.q().isBlank()) {
            String q = f.q().trim();
            String like = "%" + q.toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
            List<Predicate> any = new ArrayList<>();
            any.add(cb.like(cb.lower(root.get("paymentReference")), like));
            any.add(cb.like(cb.lower(root.get("reference")), like));
            any.add(cb.like(cb.lower(root.get("providerReference")), like));
            any.add(cb.like(cb.lower(root.get("payerEmail")), like));
            String digits = q.startsWith("#") ? q.substring(1) : q;
            if (digits.matches("\\d{1,18}")) {
                any.add(cb.equal(root.get("orderId"), Long.parseLong(digits)));
                any.add(cb.equal(root.get("id"), Long.parseLong(digits)));
            }
            list.add(cb.or(any.toArray(Predicate[]::new)));
        }
        return list;
    }

    private static BigDecimal money(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    static Payment toDomain(PaymentEntity e) {
        PaymentMethodKind kind;
        try {
            kind = PaymentMethodKind.valueOf(e.getKind());
        } catch (IllegalArgumentException | NullPointerException ex) {
            kind = PaymentMethodKind.OTHER;
        }
        Payment payment = new Payment(e.getId(), e.getOrderId(), e.getPaymentTypeId(),
                "VIRTUAL".equals(e.getChannel()) ? PaymentChannel.VIRTUAL : PaymentChannel.PHYSICAL, kind,
                GatewayProvider.tryParse(e.getProvider()).orElse(null),
                PaymentStatus.tryParse(e.getStatus()).orElse(PaymentStatus.PENDING), e.getAmount(), e.getFeeAmount(),
                e.getTenderedAmount(), e.getChangeAmount(), e.getRefundedAmount(), e.getReference(),
                e.getPaymentReference(), e.getProviderReference(), e.getCheckoutUrl(), e.getReturnUrl(), e.getPayerEmail(),
                e.getNotes(), e.getStatusDetail(), e.getApprovedAt(), e.getExpiresAt(), e.getCreatedAt());
        payment.assignCashSession(e.getCashSessionId());
        return payment;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
