package com.essenza.draco.modules.marketplace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.marketplace.domain.dispute.DisputeWorkflow;
import com.essenza.draco.modules.marketplace.domain.dispute.DisputeWorkflow.Resolution;
import com.essenza.draco.modules.marketplace.domain.dispute.DisputeWorkflow.Status;

class DisputeWorkflowTest {

    static final LocalDate OPENED = LocalDate.of(2026, 11, 2);
    static final LocalDate DUE = DisputeWorkflow.respondBy(OPENED, 3);
    static final BigDecimal TOTAL = new BigDecimal("100000");

    @Test
    void onlyPaidThirdPartyOrdersCanBeDisputed() {
        DisputeWorkflow.checkCanOpen(true, "DELIVERED", false, "No llegó el pedido completo");
        assertThatThrownBy(() -> DisputeWorkflow.checkCanOpen(false, "PAID", false, "No llegó el pedido")).hasMessageContaining("externos");
        assertThatThrownBy(() -> DisputeWorkflow.checkCanOpen(true, "PENDING", false, "No llegó el pedido")).hasMessageContaining("pagadas");
        assertThatThrownBy(() -> DisputeWorkflow.checkCanOpen(true, "PAID", true, "No llegó el pedido")).hasMessageContaining("ya tiene");
        assertThatThrownBy(() -> DisputeWorkflow.checkCanOpen(true, "PAID", false, "corto")).hasMessageContaining("10 caracteres");
        assertThat(DUE).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(DisputeWorkflow.respondBy(OPENED, 999)).isEqualTo(OPENED.plusDays(30));
    }

    @Test
    void sellerAnswersOnceAndStoreDecides() {
        DisputeWorkflow.checkRespond(Status.OPEN, "Se envió con guía 123");
        assertThatThrownBy(() -> DisputeWorkflow.checkRespond(Status.SELLER_RESPONDED, "Otra vez")).hasMessageContaining("ya respondió");
        assertThat(DisputeWorkflow.checkResolve(Status.SELLER_RESPONDED, DUE, OPENED, Resolution.REFUND, new BigDecimal("40000"),
                new BigDecimal("40000"), TOTAL, "Llegó roto")).isEqualTo(Status.RESOLVED_BUYER);
        assertThat(DisputeWorkflow.checkResolve(Status.SELLER_RESPONDED, DUE, OPENED, Resolution.REJECTED, null, null, TOTAL,
                "La guía muestra entrega")).isEqualTo(Status.RESOLVED_SELLER);
    }

    @Test
    void storeWaitsForTheSellerUnlessTheDeadlinePassed() {
        assertThatThrownBy(() -> DisputeWorkflow.checkResolve(Status.OPEN, DUE, DUE, Resolution.REFUND, BigDecimal.TEN, null, TOTAL,
                "Sin respuesta")).hasMessageContaining("tiene hasta");
        assertThat(DisputeWorkflow.checkResolve(Status.OPEN, DUE, DUE.plusDays(1), Resolution.REPLACEMENT, null, null, TOTAL,
                "Sin respuesta del vendedor")).isEqualTo(Status.RESOLVED_BUYER);
        assertThat(DisputeWorkflow.overdue(Status.OPEN, DUE, DUE.plusDays(1))).isTrue();
        assertThat(DisputeWorkflow.overdue(Status.SELLER_RESPONDED, DUE, DUE.plusDays(1))).isFalse();
    }

    @Test
    void amountsAreBounded() {
        assertThatThrownBy(() -> DisputeWorkflow.checkResolve(Status.SELLER_RESPONDED, DUE, OPENED, Resolution.REFUND,
                new BigDecimal("100001"), null, TOTAL, "Demasiado")).hasMessageContaining("superar el total");
        assertThatThrownBy(() -> DisputeWorkflow.checkResolve(Status.SELLER_RESPONDED, DUE, OPENED, Resolution.REFUND, null, null,
                TOTAL, "Sin valor")).hasMessageContaining("valor del reembolso");
        assertThatThrownBy(() -> DisputeWorkflow.checkResolve(Status.SELLER_RESPONDED, DUE, OPENED, Resolution.REJECTED,
                BigDecimal.ONE, null, TOTAL, "Rechazo")).hasMessageContaining("no hay reembolso");
        assertThatThrownBy(() -> DisputeWorkflow.checkResolve(Status.RESOLVED_BUYER, DUE, OPENED, Resolution.REJECTED, null, null,
                TOTAL, "Otra vez")).hasMessageContaining("cerrado");
        assertThatThrownBy(() -> DisputeWorkflow.checkCancel(Status.CANCELLED, "Retiro")).hasMessageContaining("cerrado");
        assertThat(DisputeWorkflow.number(9)).isEqualTo("DSP-000009");
    }
}
