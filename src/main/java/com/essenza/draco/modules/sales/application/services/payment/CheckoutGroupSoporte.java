package com.essenza.draco.modules.sales.application.services.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.essenza.draco.modules.sales.application.output.repository.CheckoutGroupStore;
import com.essenza.draco.modules.sales.application.output.repository.PaymentRepository;
import com.essenza.draco.modules.sales.domain.model.payment.Payment;
import com.essenza.draco.modules.sales.domain.model.payment.PaymentStatus;

/**
 * Aplica el estado de una transacción de la pasarela a todos los pagos de su grupo (compra
 * multivendedor). Si el monto aprobado no coincide con el del grupo, ningún pago se aprueba: quedan
 * pendientes con la nota para revisarlos.
 */
@Component
class CheckoutGroupSoporte {

    private final CheckoutGroupStore groups;
    private final PaymentRepository payments;
    private final PaymentSoporte soporte;

    CheckoutGroupSoporte(CheckoutGroupStore groups, PaymentRepository payments, PaymentSoporte soporte) {
        this.groups = groups;
        this.payments = payments;
        this.soporte = soporte;
    }

    CheckoutGroupStore store() {
        return groups;
    }

    boolean apply(CheckoutGroupStore.Group group, PaymentStatus status, String providerRef, BigDecimal amount, String detail,
                  String source, String eventKey, String payload) {
        boolean matches = amount == null || amount.setScale(2, RoundingMode.HALF_UP).compareTo(group.amount()) == 0;
        boolean changed = false;
        boolean first = true;
        for (Long paymentId : group.paymentIds()) { // ascendente: orden fijo de bloqueo
            Payment payment = payments.lockById(paymentId).orElse(null);
            if (payment == null) {
                continue;
            }
            String key = eventKey == null ? null : first ? eventKey : eventKey + "#" + paymentId;
            String note = detail == null ? "Compra · " + group.reference() : detail + " · compra " + group.reference();
            PaymentStatus target = status;
            if (!matches && status == PaymentStatus.APPROVED) {
                // Un total distinto no aprueba ninguna parte (aunque alguna coincida por casualidad con ese valor).
                target = PaymentStatus.PENDING;
                note = "Monto aprobado en la pasarela (" + amount.toPlainString() + ") distinto al de la compra ("
                        + group.amount().toPlainString() + "): revísalo antes de confirmar. " + note;
                payment.annotate(note);
            }
            // Cada pago se concilia por su parte del total.
            changed |= soporte.applyGatewayStatus(payment, target, providerRef, payment.getAmount(), note, source, key,
                    first ? payload : null);
            first = false;
        }
        return changed;
    }
}
