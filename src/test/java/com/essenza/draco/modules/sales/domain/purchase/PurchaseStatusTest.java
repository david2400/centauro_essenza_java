package com.essenza.draco.modules.sales.domain.purchase;

import static com.essenza.draco.modules.sales.domain.model.OrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.sales.domain.model.purchase.PurchaseStatus;
import com.essenza.draco.modules.sales.domain.model.purchase.PurchaseStatus.Counts;

class PurchaseStatusTest {

    @Test
    void neverPaidWhenOnlyPartIsPaid() {
        assertThat(PurchaseStatus.derive(List.of(PENDING, PENDING))).isEqualTo(PurchaseStatus.PENDING);
        assertThat(PurchaseStatus.derive(List.of(PAID, PENDING))).isEqualTo(PurchaseStatus.PARTIALLY_PAID);
        assertThat(PurchaseStatus.derive(List.of(DELIVERED, PENDING))).isEqualTo(PurchaseStatus.PARTIALLY_PAID);
        assertThat(PurchaseStatus.derive(List.of(PAID, PROCESSING, READY))).isEqualTo(PurchaseStatus.PAID);
    }

    @Test
    void deliveryProgress() {
        assertThat(PurchaseStatus.derive(List.of(SHIPPED, PAID))).isEqualTo(PurchaseStatus.IN_DELIVERY);
        assertThat(PurchaseStatus.derive(List.of(DELIVERED, READY))).isEqualTo(PurchaseStatus.IN_DELIVERY);
        assertThat(PurchaseStatus.derive(List.of(DELIVERED, DELIVERED))).isEqualTo(PurchaseStatus.DELIVERED);
    }

    @Test
    void cancelledSubOrdersDoNotCountAsActive() {
        assertThat(PurchaseStatus.derive(List.of(CANCELLED, CANCELLED))).isEqualTo(PurchaseStatus.CANCELLED);
        assertThat(PurchaseStatus.derive(List.of(CANCELLED, PAID))).isEqualTo(PurchaseStatus.PAID);
        assertThat(PurchaseStatus.derive(List.of(CANCELLED, DELIVERED))).isEqualTo(PurchaseStatus.DELIVERED);
        assertThat(PurchaseStatus.derive(List.of(CANCELLED, PENDING))).isEqualTo(PurchaseStatus.PENDING);
        assertThat(Counts.of(List.of(CANCELLED, PAID)).partiallyCancelled()).isTrue();
        assertThat(Counts.of(List.of(CANCELLED, CANCELLED)).partiallyCancelled()).isFalse();
    }

    @Test
    void inconsistentCountsAreRejected() {
        assertThatThrownBy(() -> new Counts(1, 1, 1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Counts(2, 0, 0, 0, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
