package com.essenza.draco.modules.sales.application.services.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.essenza.draco.modules.sales.application.dto.order.ChangeOrderStatusDto;
import com.essenza.draco.modules.sales.application.dto.order.OrderDto;
import com.essenza.draco.modules.sales.application.dto.payment.PaymentDto;
import com.essenza.draco.modules.sales.application.input.order.ChangeOrderStatusUseCase;
import com.essenza.draco.modules.sales.application.input.order.FindOrderByIdUseCase;
import com.essenza.draco.modules.sales.application.output.gateway.PaymentGateway;
import com.essenza.draco.modules.sales.application.output.gateway.PaymentGateways;
import com.essenza.draco.modules.sales.application.output.repository.OrderLedger;
import com.essenza.draco.modules.sales.application.output.repository.PaymentEventLog;
import com.essenza.draco.modules.sales.application.output.repository.PaymentMethodRepository;
import com.essenza.draco.modules.sales.application.output.repository.PaymentRepository;
import com.essenza.draco.modules.sales.domain.model.OrderStatus;
import com.essenza.draco.modules.sales.domain.model.payment.GatewayProvider;
import com.essenza.draco.modules.sales.domain.model.payment.OrderPaymentStatus;
import com.essenza.draco.modules.sales.domain.model.payment.Payment;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentMethod;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentRuleViolation;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentStatus;
import com.essenza.draco.shared.exceptions.NotFoundException;

/** Código compartido por los casos de uso de pagos. */
@Slf4j
@Component
@RequiredArgsConstructor
class PaymentSoporte {

    static final String CURRENCY = "COP";
    static final String SOURCE_PANEL = "PANEL";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final PaymentRepository payments;
    private final PaymentMethodRepository methods;
    private final PaymentEventLog events;
    private final PaymentGateways gateways;
    private final OrderLedger ledger;
    private final FindOrderByIdUseCase findOrder;
    private final ChangeOrderStatusUseCase changeOrderStatus;

    /** Datos de la orden que importan para cobrar. */
    record OrderView(Long id, OrderStatus state, BigDecimal total, BigDecimal returned) {

        OrderView(Long id, OrderStatus state, BigDecimal total) {
            this(id, state, total, BigDecimal.ZERO);
        }
    }

    OrderView order(Long orderId) {
        OrderDto order = findOrder.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Orden no encontrada: " + orderId));
        BigDecimal gross = order.getTotal() == null ? BigDecimal.ZERO : order.getTotal();
        BigDecimal returned = order.getReturnedAmount() == null ? BigDecimal.ZERO : order.getReturnedAmount();
        // Lo devuelto (acreditado al cliente) ya no se cobra.
        BigDecimal total = gross.subtract(returned);
        return new OrderView(order.getId(), OrderStatus.tryParse(order.getState()).orElse(OrderStatus.PENDING),
                total.signum() < 0 ? BigDecimal.ZERO.setScale(2) : total, returned);
    }

    /** Bloquea la orden (serializa pagos simultáneos sobre ella) y la devuelve. */
    OrderView lockedOrder(Long orderId) {
        ledger.lockOrder(orderId).orElseThrow(() -> new NotFoundException("Orden no encontrada: " + orderId));
        OrderView order = order(orderId);
        if (order.state() == OrderStatus.CANCELLED) {
            throw new PaymentRuleViolation("La orden " + orderId + " está cancelada: no admite pagos.");
        }
        return order;
    }

    /**
     * Marketplace: la tienda cobra los subpedidos de vendedores externos (V28) y les liquida desde
     * su estado de cuenta. Con {@code essenza.marketplace.collection=SELLER} el comprador le paga al
     * vendedor por fuera y aquí solo se registra el pago que el vendedor confirme (salvo consignación).
     */
    void rejectThirdPartyCollection(Long orderId, String how) {
        if (sellerCollects(orderId)) {
            throw new PaymentRuleViolation("La orden " + orderId + " es el subpedido de un vendedor externo: el comprador "
                    + "le paga directamente al vendedor y la tienda no lo cobra " + how + ". Registra el pago cuando el "
                    + "vendedor lo confirme.");
        }
    }

    /** Subpedido de vendedor externo que el comprador le paga al vendedor por fuera (no lo cobra la tienda). */
    boolean sellerCollects(Long orderId) {
        return ledger.marketplaceLink(orderId).map(OrderLedger.MarketplaceLink::thirdParty).orElse(false)
                && !ledger.storeCollects(orderId);
    }

    PaymentMethod method(Long methodId) {
        PaymentMethod method = methods.findById(methodId)
                .orElseThrow(() -> new NotFoundException("Medio de pago no encontrado: " + methodId));
        if (!method.active()) {
            throw new PaymentRuleViolation("El medio de pago \"" + method.name() + "\" está inactivo.");
        }
        return method;
    }

    Payment locked(Long paymentId) {
        return payments.lockById(paymentId).orElseThrow(() -> new NotFoundException("Pago no encontrado: " + paymentId));
    }

    OrderPaymentStatus.Balance balance(OrderView order) {
        return OrderPaymentStatus.of(order.total(), order.returned(), payments.findByOrderId(order.id()));
    }

    /**
     * Antes de cobrar (con la orden bloqueada): vence los links de pago en línea cuya vigencia
     * pasó, para que no sigan reservando el saldo. Si la pasarela los aprueba después, el
     * webhook igual los acepta (EXPIRED → APPROVED).
     */
    void expireStaleCheckouts(Long orderId) {
        Instant now = Instant.now();
        for (Payment payment : payments.findByOrderId(orderId)) {
            if (payment.getStatus() == PaymentStatus.PENDING && payment.getProvider() != null
                    && payment.getExpiresAt() != null && payment.getExpiresAt().isBefore(now)) {
                expire(payment, "Link de pago vencido sin confirmación de la pasarela.");
            }
        }
    }

    void expire(Payment payment, String reason) {
        PaymentStatus from = payment.getStatus();
        payment.expire(reason);
        Payment saved = payments.save(payment);
        log(saved, SOURCE_PANEL, null, "EXPIRED", from, saved.getAmount(), reason, null);
    }

    /** Monto a 2 decimales; debe caber en lo que falta por cobrar. */
    BigDecimal checkCollectable(BigDecimal amount, OrderPaymentStatus.Balance balance) {
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal collectable = balance.collectable();
        if (collectable.signum() <= 0) {
            throw new PaymentRuleViolation(balance.pending().signum() > 0
                    ? "La orden ya tiene pagos pendientes por el saldo: confírmalos o recházalos primero."
                    : "La orden no tiene saldo por cobrar.");
        }
        if (value.compareTo(collectable) > 0) {
            throw new PaymentRuleViolation("El monto supera lo que falta por cobrar (" + collectable.toPlainString() + ").");
        }
        return value;
    }

    /** Referencia única del pago (la que viaja a la pasarela): PAY-&lt;orden&gt;-&lt;8 caracteres&gt;. */
    String newPaymentReference(Long orderId) {
        StringBuilder code = new StringBuilder("PAY-").append(orderId).append('-');
        for (int i = 0; i < 8; i++) {
            code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }

    void log(Payment payment, String source, String eventKey, String type, PaymentStatus from, BigDecimal amount,
             String detail, String payload) {
        events.record(new PaymentEventLog.Event(null, payment == null ? null : payment.getId(), source, eventKey, type,
                from == null ? null : from.name(), payment == null ? null : payment.getStatus().name(), amount,
                truncate(detail, 500), payload, Instant.now()));
    }

    /**
     * Marca la orden como PAGADA cuando lo aprobado cubre el total (solo desde PENDING y si tiene
     * líneas: pagar descuenta el stock reservado). Se valida antes para que una orden que no
     * puede pasar a PAID no tumbe la transacción del pago (p. ej. en un webhook).
     */
    void settleOrder(Long orderId) {
        OrderView order = order(orderId);
        if (order.state() != OrderStatus.PENDING || order.total().signum() <= 0) {
            return;
        }
        OrderPaymentStatus.Balance balance = balance(order);
        if (balance.status() != OrderPaymentStatus.PAID && balance.status() != OrderPaymentStatus.OVERPAID) {
            return;
        }
        if (ledger.lines(orderId).isEmpty()) {
            log.warn("Orden {} cubierta por pagos pero sin líneas: no se marca como pagada", orderId);
            return;
        }
        changeOrderStatus.changeStatus(orderId, new ChangeOrderStatusDto(OrderStatus.PAID.name(), null));
    }

    /**
     * Aplica un estado informado por la pasarela (webhook o consulta). Ignora lo que no es una
     * transición válida (notificación tardía o repetida) y no aprueba si el monto no coincide.
     *
     * @return {@code true} si cambió el estado del pago
     */
    boolean applyGatewayStatus(Payment payment, PaymentStatus target, String providerRef, BigDecimal amount,
                               String detail, String source, String eventKey, String payload) {
        PaymentStatus from = payment.getStatus();
        payment.linkProvider(providerRef);
        String note = detail;
        if (target == PaymentStatus.APPROVED && amount != null
                && amount.setScale(2, RoundingMode.HALF_UP).compareTo(payment.getAmount()) != 0) {
            note = "Monto aprobado en la pasarela (" + amount.toPlainString() + ") distinto al del pago ("
                    + payment.getAmount().toPlainString() + "): revísalo antes de confirmarlo.";
            target = PaymentStatus.PENDING;
            payment.annotate(note);
        }
        if (target != null && target != from) {
            switch (target) {
                case APPROVED -> {
                    if (from == PaymentStatus.PENDING || from == PaymentStatus.EXPIRED) {
                        payment.approve(Instant.now(), providerRef, note);
                    }
                }
                case DECLINED -> {
                    if (from == PaymentStatus.PENDING) payment.decline(providerRef, note);
                }
                case EXPIRED -> {
                    if (from == PaymentStatus.PENDING) payment.expire(note);
                }
                case VOIDED -> {
                    if (from == PaymentStatus.PENDING
                            || from == PaymentStatus.APPROVED && payment.getRefundedAmount().signum() == 0) {
                        payment.voidPayment(note);
                    }
                }
                case REFUNDED -> {
                    if (from == PaymentStatus.APPROVED) payment.refund(payment.refundable(), note);
                }
                case PENDING -> {
                }
            }
        }
        boolean changed = payment.getStatus() != from;
        Payment saved = payments.save(payment);
        log(saved, source, eventKey, changed ? "STATUS_" + saved.getStatus().name() : "NOTIFICATION", from, amount,
                note, payload);
        if (changed && saved.getStatus() == PaymentStatus.APPROVED) {
            settleOrder(saved.getOrderId());
        }
        return changed;
    }

    PaymentGateway gateway(GatewayProvider provider) {
        PaymentGateway gateway = gateways.find(provider)
                .orElseThrow(() -> new PaymentRuleViolation("La pasarela " + provider + " no está disponible."));
        if (!gateway.configured()) {
            throw new PaymentRuleViolation("La pasarela " + provider
                    + " no está configurada en el servidor: faltan sus credenciales.");
        }
        return gateway;
    }

    List<PaymentDto> toDtos(List<Payment> list) {
        Map<Long, PaymentMethod> byId = methods.findAll().stream()
                .collect(Collectors.toMap(PaymentMethod::id, Function.identity(), (a, b) -> a));
        return list.stream().map(payment -> toDto(payment, byId.get(payment.getMethodId()))).toList();
    }

    PaymentDto toDto(Payment payment) {
        return toDto(payment, methods.findById(payment.getMethodId()).orElse(null));
    }

    private PaymentDto toDto(Payment p, PaymentMethod method) {
        return new PaymentDto(p.getId(), p.getOrderId(), p.getMethodId(), method == null ? null : method.code(),
                method == null ? null : method.name(), p.getChannel().name(), p.getKind().name(),
                p.getProvider() == null ? null : p.getProvider().name(), p.getStatus().name(), p.getAmount(),
                p.getFeeAmount(), p.getTenderedAmount(), p.getChangeAmount(), p.getRefundedAmount(), p.settledAmount(),
                CURRENCY, p.getReference(), p.getPaymentReference(), p.getProviderReference(), p.getCheckoutUrl(),
                p.getPayerEmail(), p.getNotes(), p.getStatusDetail(), p.getApprovedAt(), p.getExpiresAt(),
                p.getCreatedAt(), actions(p), p.getCashSessionId());
    }

    /** Acciones disponibles para el panel. */
    static List<String> actions(Payment p) {
        List<String> actions = new ArrayList<>();
        if (p.getStatus() == PaymentStatus.PENDING) {
            actions.add("confirm");
            actions.add("decline");
            actions.add("void");
            if (p.getProvider() != null) {
                actions.add("sync");
            }
        } else if (p.getStatus() == PaymentStatus.APPROVED) {
            if (p.getRefundedAmount().signum() == 0) {
                actions.add("void");
            }
            actions.add("refund");
            if (p.getProvider() != null) {
                actions.add("sync");
            }
        } else if (p.getStatus() == PaymentStatus.EXPIRED && p.getProvider() != null) {
            actions.add("sync"); // por si la pasarela lo aprobó tarde
        }
        return actions;
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
