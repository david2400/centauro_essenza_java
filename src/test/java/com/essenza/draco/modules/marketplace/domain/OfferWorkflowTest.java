package com.essenza.draco.modules.marketplace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.marketplace.domain.offer.OfferEnums.Action;
import com.essenza.draco.modules.marketplace.domain.offer.OfferEnums.Condition;
import com.essenza.draco.modules.marketplace.domain.offer.OfferEnums.Fulfillment;
import com.essenza.draco.modules.marketplace.domain.offer.OfferEnums.Status;
import com.essenza.draco.modules.marketplace.domain.offer.OfferTerms;
import com.essenza.draco.modules.marketplace.domain.offer.OfferWorkflow;
import com.essenza.draco.modules.marketplace.domain.offer.OfferWorkflow.Context;

class OfferWorkflowTest {

    private static final Context OK = new Context(true, true, false, false);

    private static OfferTerms terms(String price, int qty) {
        return OfferTerms.of("cop", new BigDecimal(price), qty, 2, Condition.NEW, null, " REF-01 ");
    }

    @Test
    void termsAreValidated() {
        OfferTerms t = terms("89900", 5);
        assertThat(t.currency()).isEqualTo("COP");
        assertThat(t.fulfillment()).isEqualTo(Fulfillment.SELLER);
        assertThat(t.sellerSku()).isEqualTo("REF-01");
        assertThatThrownBy(() -> terms("0", 1)).hasMessageContaining("mayor que cero");
        assertThatThrownBy(() -> terms("10.123", 1)).hasMessageContaining("decimales");
        assertThatThrownBy(() -> terms("10", -1)).hasMessageContaining("cantidad");
        assertThatThrownBy(() -> OfferTerms.of("COP", BigDecimal.TEN, 1, 61, null, null, null)).hasMessageContaining("alistamiento");
        // Consignación (V23): el despacho de la plataforma se acepta; su disponible lo fija la consignación.
        assertThat(OfferTerms.of("COP", BigDecimal.TEN, 1, 1, null, Fulfillment.PLATFORM, null).withAvailableQuantity(7)
                .availableQuantity()).isEqualTo(7);
        assertThatThrownBy(() -> OfferTerms.of("COP", BigDecimal.TEN, 1, 1, null, null, "<script>")).hasMessageContaining("referencia");
    }

    @Test
    void moderationFlow() {
        Status s = OfferWorkflow.transition(Status.DRAFT, Action.SUBMIT, null, OK);
        assertThat(s).isEqualTo(Status.PENDING_REVIEW);
        assertThat(OfferWorkflow.transition(s, Action.REJECT, "Foto no corresponde", OK)).isEqualTo(Status.REJECTED);
        assertThat(OfferWorkflow.transition(Status.REJECTED, Action.SUBMIT, null, OK)).isEqualTo(Status.PENDING_REVIEW);
        s = OfferWorkflow.transition(s, Action.APPROVE, null, OK);
        assertThat(s).isEqualTo(Status.ACTIVE);
        s = OfferWorkflow.transition(s, Action.PAUSE, null, OK);
        assertThat(OfferWorkflow.transition(s, Action.RESUME, null, OK)).isEqualTo(Status.ACTIVE);
        assertThat(OfferWorkflow.transition(Status.ACTIVE, Action.CLOSE, "Producto descontinuado", OK)).isEqualTo(Status.CLOSED);
        assertThat(OfferWorkflow.actionsFrom(Status.CLOSED)).isEmpty();
        assertThat(OfferWorkflow.actionsFrom(Status.PENDING_REVIEW)).containsExactly(Action.APPROVE, Action.REJECT, Action.CLOSE);
    }

    @Test
    void preconditionsAndFourEyes() {
        assertThatThrownBy(() -> OfferWorkflow.transition(Status.DRAFT, Action.APPROVE, null, OK)).hasMessageContaining("DRAFT");
        assertThatThrownBy(() -> OfferWorkflow.transition(Status.PENDING_REVIEW, Action.REJECT, "", OK)).hasMessageContaining("motivo");
        assertThatThrownBy(() -> OfferWorkflow.transition(Status.DRAFT, Action.SUBMIT, null, new Context(false, true, false, false)))
                .hasMessageContaining("aprobado");
        assertThatThrownBy(() -> OfferWorkflow.transition(Status.PAUSED, Action.RESUME, null, new Context(true, false, false, false)))
                .hasMessageContaining("SKU");
        assertThatThrownBy(() -> OfferWorkflow.transition(Status.PENDING_REVIEW, Action.APPROVE, null, new Context(true, true, true, true)))
                .hasMessageContaining("otra persona");
        // Sin cuatro ojos, quien la creó puede moderar; pausar no exige vendedor aprobado.
        assertThat(OfferWorkflow.transition(Status.PENDING_REVIEW, Action.APPROVE, null, new Context(true, true, true, false)))
                .isEqualTo(Status.ACTIVE);
        assertThat(OfferWorkflow.transition(Status.ACTIVE, Action.PAUSE, null, new Context(false, false, false, false)))
                .isEqualTo(Status.PAUSED);
    }

    @Test
    void editingRulesByStatus() {
        OfferTerms before = terms("89900", 5);
        assertThat(OfferWorkflow.checkEdit(Status.ACTIVE, before, terms("79900", 8))).containsExactly("price", "available_quantity");
        OfferTerms used = OfferTerms.of("COP", new BigDecimal("89900"), 5, 2, Condition.USED, null, "REF-01");
        assertThatThrownBy(() -> OfferWorkflow.checkEdit(Status.ACTIVE, before, used)).hasMessageContaining("condition");
        assertThat(OfferWorkflow.checkEdit(Status.DRAFT, before, used)).containsExactly("condition");
        assertThatThrownBy(() -> OfferWorkflow.checkEdit(Status.PENDING_REVIEW, before, before)).hasMessageContaining("moderación");
        assertThatThrownBy(() -> OfferWorkflow.checkEdit(Status.CLOSED, before, before)).hasMessageContaining("cerrada");
    }

    @Test
    void priceDeviationAndVisibility() {
        OfferTerms t = terms("150000", 1);
        assertThat(t.deviationFrom(new BigDecimal("100000"), "COP")).isEqualByComparingTo("50.00");
        assertThat(OfferTerms.deviationAlert(new BigDecimal("50.00"))).isFalse();
        assertThat(OfferTerms.deviationAlert(new BigDecimal("-60"))).isTrue();
        assertThat(t.deviationFrom(null, "COP")).isNull();
        assertThat(t.deviationFrom(new BigDecimal("100"), "USD")).isNull();
        assertThat(OfferWorkflow.visible(Status.ACTIVE, true, true)).isTrue();
        assertThat(OfferWorkflow.visible(Status.ACTIVE, false, true)).isFalse();
        assertThat(OfferWorkflow.visible(Status.PAUSED, true, true)).isFalse();
    }
}
