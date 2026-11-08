package com.essenza.draco.modules.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.inventory.domain.model.purchase.PurchaseOrderRules;
import com.essenza.draco.modules.inventory.domain.model.purchase.PurchaseOrderRules.Line;
import com.essenza.draco.modules.inventory.domain.model.purchase.PurchaseOrderRules.Pending;
import com.essenza.draco.modules.inventory.domain.model.purchase.PurchaseOrderRules.Status;

class PurchaseOrderRulesTest {

    @Test
    void linesAndTotal() {
        List<Line> lines = PurchaseOrderRules.checkLines(List.of(new Line(1, 10, new BigDecimal("12500")), new Line(2, 3, new BigDecimal("999.5"))));
        assertThat(PurchaseOrderRules.total(lines)).isEqualByComparingTo("127998.50");
        assertThatThrownBy(() -> PurchaseOrderRules.checkLines(List.of())).hasMessageContaining("al menos un producto");
        assertThatThrownBy(() -> PurchaseOrderRules.checkLines(List.of(new Line(1, 1, BigDecimal.ONE), new Line(1, 2, BigDecimal.ONE))))
                .hasMessageContaining("repetido");
        assertThatThrownBy(() -> PurchaseOrderRules.checkLines(List.of(new Line(1, 0, BigDecimal.ONE)))).hasMessageContaining("entre 1");
        assertThatThrownBy(() -> PurchaseOrderRules.checkLines(List.of(new Line(1, 1, new BigDecimal("-1"))))).hasMessageContaining("costo");
        assertThatThrownBy(() -> PurchaseOrderRules.checkLines(List.of(new Line(1, 1, new BigDecimal("1.234"))))).hasMessageContaining("decimales");
    }

    @Test
    void sendNeedsActiveSupplierAndMinimum() {
        PurchaseOrderRules.checkSend(Status.DRAFT, true, new BigDecimal("500000"), new BigDecimal("300000"));
        assertThatThrownBy(() -> PurchaseOrderRules.checkSend(Status.DRAFT, false, BigDecimal.TEN, null)).hasMessageContaining("no está activo");
        assertThatThrownBy(() -> PurchaseOrderRules.checkSend(Status.DRAFT, true, BigDecimal.TEN, new BigDecimal("300000")))
                .hasMessageContaining("mínimo");
        assertThatThrownBy(() -> PurchaseOrderRules.checkSend(Status.SENT, true, BigDecimal.TEN, null)).hasMessageContaining("borrador");
        assertThatThrownBy(() -> PurchaseOrderRules.checkEditable(Status.SENT)).hasMessageContaining("borrador");
    }

    @Test
    void partialAndCompleteReceipts() {
        List<Pending> lines = List.of(new Pending(10, "SKU-A", 10, 0), new Pending(11, "SKU-B", 5, 2));
        assertThat(PurchaseOrderRules.checkReceive(Status.SENT, lines, Map.of(10L, 4))).isEqualTo(Status.PARTIALLY_RECEIVED);
        assertThat(PurchaseOrderRules.checkReceive(Status.PARTIALLY_RECEIVED, lines, Map.of(10L, 10, 11L, 3))).isEqualTo(Status.RECEIVED);
        assertThatThrownBy(() -> PurchaseOrderRules.checkReceive(Status.SENT, lines, Map.of(11L, 4))).hasMessageContaining("quedan 3");
        assertThatThrownBy(() -> PurchaseOrderRules.checkReceive(Status.SENT, lines, Map.of(99L, 1))).hasMessageContaining("no es de esta orden");
        assertThatThrownBy(() -> PurchaseOrderRules.checkReceive(Status.SENT, lines, Map.of(10L, 0))).hasMessageContaining("al menos una");
        assertThatThrownBy(() -> PurchaseOrderRules.checkReceive(Status.DRAFT, lines, Map.of(10L, 1))).hasMessageContaining("Envía");
    }

    @Test
    void cancelOrClose() {
        PurchaseOrderRules.checkCancel(Status.SENT, false, "Proveedor sin stock");
        assertThatThrownBy(() -> PurchaseOrderRules.checkCancel(Status.SENT, true, "Ya llegó algo")).hasMessageContaining("ciérrala");
        assertThatThrownBy(() -> PurchaseOrderRules.checkCancel(Status.RECEIVED, false, "Tarde")).hasMessageContaining("borrador o enviada");
        PurchaseOrderRules.checkClose(Status.PARTIALLY_RECEIVED, "El resto se agotó");
        assertThatThrownBy(() -> PurchaseOrderRules.checkClose(Status.SENT, "Cerrar")).hasMessageContaining("en parte");
        assertThat(PurchaseOrderRules.orderNumber(5)).isEqualTo("OC-000005");
        assertThat(PurchaseOrderRules.receiptNumber(5)).isEqualTo("REC-000005");
    }
}
