package com.essenza.draco.modules.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.inventory.domain.model.purchase.MovingAverageCost;
import com.essenza.draco.modules.inventory.domain.model.purchase.ReorderPolicy;

class PurchaseCostAndReorderTest {

    @Test
    void movingAverage() {
        // 10 u. a 1000 + 5 u. a 1600 = 18000 / 15 = 1200
        assertThat(MovingAverageCost.next(new BigDecimal("1000"), null, 10, 5, new BigDecimal("1600"))).isEqualByComparingTo("1200");
        // sin existencias: el costo de la compra
        assertThat(MovingAverageCost.next(new BigDecimal("1000"), null, 0, 3, new BigDecimal("700"))).isEqualByComparingTo("700");
        assertThat(MovingAverageCost.next(new BigDecimal("1000"), null, -2, 3, new BigDecimal("700"))).isEqualByComparingTo("700");
        // sin promedio previo: existencias al costo del catálogo; sin catálogo, al de la compra
        assertThat(MovingAverageCost.next(null, new BigDecimal("900"), 3, 1, new BigDecimal("1300"))).isEqualByComparingTo("1000");
        assertThat(MovingAverageCost.next(null, null, 3, 1, new BigDecimal("1300"))).isEqualByComparingTo("1300");
        // cuatro decimales
        assertThat(MovingAverageCost.next(new BigDecimal("1"), null, 2, 1, new BigDecimal("2"))).isEqualByComparingTo("1.3333");
        assertThatThrownBy(() -> MovingAverageCost.next(null, null, 1, 0, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MovingAverageCost.next(null, null, 1, 1, new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void suggestedQuantity() {
        assertThat(ReorderPolicy.suggestedQuantity(11, 10, 0, 1)).isZero();          // sobre el mínimo
        assertThat(ReorderPolicy.suggestedQuantity(5, 0, 0, 1)).isZero();            // sin mínimo
        assertThat(ReorderPolicy.suggestedQuantity(4, 10, 0, 1)).isEqualTo(16);      // hasta 20
        assertThat(ReorderPolicy.suggestedQuantity(4, 10, 6, 1)).isEqualTo(10);      // descuenta lo que viene
        assertThat(ReorderPolicy.suggestedQuantity(4, 10, 20, 1)).isZero();          // ya viene suficiente
        assertThat(ReorderPolicy.suggestedQuantity(10, 10, 0, 24)).isEqualTo(24);    // mínimo del proveedor
        assertThat(ReorderPolicy.suggestedQuantity(-3, 10, 0, null)).isEqualTo(20);  // disponible negativo cuenta como 0
    }

    record C(long supplierId, boolean preferred, BigDecimal unitCost) implements ReorderPolicy.Candidate {
    }

    @Test
    void pickSupplier() {
        assertThat(ReorderPolicy.pickSupplier(List.of(new C(1, false, new BigDecimal("10")), new C(2, true, new BigDecimal("50"))))
                .map(C::supplierId)).contains(2L);
        assertThat(ReorderPolicy.pickSupplier(List.of(new C(3, false, new BigDecimal("10")), new C(2, false, new BigDecimal("9"))))
                .map(C::supplierId)).contains(2L);
        assertThat(ReorderPolicy.pickSupplier(List.of(new C(3, false, BigDecimal.TEN), new C(2, false, BigDecimal.TEN)))
                .map(C::supplierId)).contains(2L);
        assertThat(ReorderPolicy.pickSupplier(List.<C>of())).isEmpty();
    }
}
