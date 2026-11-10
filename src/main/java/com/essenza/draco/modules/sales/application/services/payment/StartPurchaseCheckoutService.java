package com.essenza.draco.modules.sales.application.services.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.sales.application.dto.payment.PaymentDto;
import com.essenza.draco.modules.sales.application.dto.payment.PurchaseCheckoutDto;
import com.essenza.draco.modules.sales.application.dto.payment.StartPurchaseCheckoutDto;
import com.essenza.draco.modules.sales.application.output.gateway.PaymentGateway;
import com.essenza.draco.modules.sales.application.output.gateway.PaymentGateways;
import com.essenza.draco.modules.sales.application.output.repository.CheckoutGroupStore;
import com.essenza.draco.modules.sales.application.output.repository.PaymentRepository;
import com.essenza.draco.modules.sales.application.output.repository.PurchaseLedger;
import com.essenza.draco.modules.sales.domain.model.payment.OrderPaymentStatus;
import com.essenza.draco.modules.sales.domain.model.payment.Payment;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentMethod;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentRuleViolation;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentStatus;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Pago en línea de una compra multivendedor: un solo link por el saldo de todos sus subpedidos.
 * Crea un pago PENDIENTE por subpedido (cada uno con su parte y su comisión) y la pasarela recibe
 * una sola transacción con la referencia del grupo; la aprobación los aprueba a todos.
 * Lo cobra la tienda (también los subpedidos de vendedores externos), que luego le liquida a cada vendedor.
 */
@Service
@Transactional
public class StartPurchaseCheckoutService {

    static final String MANAGE = "VENTAS.GESTIONAR";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final ActorProvider actors;
    private final PurchaseLedger purchases;
    private final PaymentRepository payments;
    private final PaymentGateways gateways;
    private final PaymentSoporte soporte;
    private final CheckoutGroupStore groups;

    public StartPurchaseCheckoutService(ActorProvider actors, PurchaseLedger purchases, PaymentRepository payments,
                                        PaymentGateways gateways, PaymentSoporte soporte, CheckoutGroupStore groups) {
        this.actors = actors;
        this.purchases = purchases;
        this.payments = payments;
        this.gateways = gateways;
        this.soporte = soporte;
        this.groups = groups;
    }

    public PurchaseCheckoutDto start(long purchaseId, StartPurchaseCheckoutDto input) {
        RequestActor actor = actors.current().require(MANAGE);
        PurchaseLedger.PurchaseRow purchase = purchases.find(actor.companyId(), purchaseId)
                .orElseThrow(() -> new NotFoundException("Compra no encontrada: " + purchaseId));
        if (input == null || input.paymentMethodId() == null) {
            throw new PaymentRuleViolation("Indica el medio de pago en línea.");
        }
        PaymentMethod method = soporte.method(input.paymentMethodId());
        if (!method.isGateway()) {
            throw new PaymentRuleViolation("\"" + method.name() + "\" no es una pasarela en línea.");
        }
        PaymentGateway gateway = soporte.gateway(method.provider());

        record Part(Long orderId, BigDecimal amount) {
        }
        List<Part> parts = new ArrayList<>();
        List<PurchaseLedger.SubOrderRow> subOrders = new ArrayList<>(purchases.subOrders(purchaseId));
        subOrders.sort(Comparator.comparingLong(PurchaseLedger.SubOrderRow::orderId)); // orden fijo de bloqueo
        for (PurchaseLedger.SubOrderRow sub : subOrders) {
            if ("CANCELLED".equals(sub.state())) {
                continue;
            }
            PaymentSoporte.OrderView order = soporte.lockedOrder(sub.orderId());
            soporte.rejectThirdPartyCollection(order.id(), "en línea");
            soporte.expireStaleCheckouts(order.id());
            payments.findByOrderId(order.id()).stream()
                    .filter(previous -> previous.getStatus() == PaymentStatus.PENDING && previous.getProvider() != null
                            && previous.getProviderReference() == null)
                    .forEach(previous -> soporte.expire(previous, "Reemplazado por el link de pago de la compra " + purchase.number() + "."));
            OrderPaymentStatus.Balance balance = soporte.balance(order);
            if (balance.collectable().signum() > 0) {
                parts.add(new Part(order.id(), balance.collectable().setScale(2, RoundingMode.HALF_UP)));
            }
        }
        if (parts.isEmpty()) {
            throw new PaymentRuleViolation("La compra " + purchase.number() + " no tiene saldo por cobrar en línea.");
        }
        BigDecimal total = parts.stream().map(Part::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        method.checkAmount(total);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(StartCheckoutService.CHECKOUT_TTL);
        String returnUrl = PaymentSoporte.blankToNull(input.returnUrl()) == null ? gateways.defaultReturnUrl() : input.returnUrl().trim();
        String email = PaymentSoporte.blankToNull(input.payerEmail()) != null ? input.payerEmail().trim() : purchase.customerEmail();
        String reference = newReference(purchaseId);
        // La comisión del medio se calcula sobre el total (la fija se cobra una vez) y se reparte por monto.
        BigDecimal fee = method.feeFor(total);
        BigDecimal feeLeft = fee;
        List<Payment> created = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            BigDecimal partFee = i == parts.size() - 1 ? feeLeft
                    : fee.multiply(part.amount()).divide(total, 2, RoundingMode.HALF_UP);
            feeLeft = feeLeft.subtract(partFee);
            Payment payment = new Payment(null, part.orderId(), method.id(), method.channel(), method.kind(), method.provider(),
                    PaymentStatus.PENDING, part.amount(), partFee, null, null, BigDecimal.ZERO, null,
                    soporte.newPaymentReference(part.orderId()), null, null, returnUrl, email, null, null, null, expiresAt, now);
            created.add(payments.save(payment));
        }
        PaymentGateway.CheckoutSession session = gateway.createCheckout(new PaymentGateway.CheckoutRequest(parts.get(0).orderId(),
                reference, total, PaymentSoporte.CURRENCY, "Compra " + purchase.number(), email, returnUrl, expiresAt));
        List<PaymentDto> dtos = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (Payment payment : created) {
            payment.attachCheckout(session.url(), session.providerReference());
            Payment saved = payments.save(payment);
            soporte.log(saved, method.provider().name(), null, "CHECKOUT_CREATED", null, saved.getAmount(),
                    "Link de la compra " + purchase.number() + " (" + reference + ", total " + total.toPlainString() + ")", null);
            dtos.add(soporte.toDto(saved));
            ids.add(saved.getId());
        }
        groups.create(reference, actor.companyId(), purchaseId, method.provider().name(), total, ids.stream().sorted().toList(),
                actor.userId(), now);
        return new PurchaseCheckoutDto(reference, total, session.url(), dtos);
    }

    static String newReference(long purchaseId) {
        StringBuilder code = new StringBuilder("PUR-").append(purchaseId).append('-');
        for (int i = 0; i < 8; i++) {
            code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }
}
