package com.essenza.draco.modules.marketplace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.marketplace.domain.consignment.ConsignmentRules;
import com.essenza.draco.modules.marketplace.domain.consignment.ConsignmentRules.Bin;
import com.essenza.draco.modules.marketplace.domain.consignment.ConsignmentRules.Line;
import com.essenza.draco.modules.marketplace.domain.consignment.ConsignmentRules.Pick;

class ConsignmentRulesTest {

    @Test
    void linesNeedPositiveQuantitiesAndNoRepeatedSku() {
        assertThat(ConsignmentRules.checkLines(List.of(new Line(1, 5), new Line(2, 1)))).hasSize(2);
        assertThatThrownBy(() -> ConsignmentRules.checkLines(List.of())).hasMessageContaining("al menos un SKU");
        assertThatThrownBy(() -> ConsignmentRules.checkLines(List.of(new Line(1, 0)))).hasMessageContaining("entre 1");
        assertThatThrownBy(() -> ConsignmentRules.checkLines(List.of(new Line(1, 2), new Line(1, 3)))).hasMessageContaining("repetido");
    }

    @Test
    void allocateTakesFromTheFullestWarehouseFirst() {
        List<Bin> bins = List.of(new Bin(10, 1, 2), new Bin(11, 2, 5), new Bin(12, 3, 5));
        assertThat(ConsignmentRules.allocate(bins, 4)).containsExactly(new Pick(11, 2, 4));
        assertThat(ConsignmentRules.allocate(bins, 11)).containsExactly(new Pick(11, 2, 5), new Pick(12, 3, 5), new Pick(10, 1, 1));
        assertThatThrownBy(() -> ConsignmentRules.allocate(bins, 13)).hasMessageContaining("no alcanza");
        assertThatThrownBy(() -> ConsignmentRules.allocate(bins, 0)).hasMessageContaining("mayor que cero");
    }

    @Test
    void adjustmentKeepsReservedUnits() {
        assertThat(ConsignmentRules.adjustment(10, 12, 0, "Conteo mensual")).isEqualTo(2);
        assertThat(ConsignmentRules.adjustment(10, 7, 3, "Faltante")).isEqualTo(-3);
        assertThatThrownBy(() -> ConsignmentRules.adjustment(10, 4, 5, "Faltante")).hasMessageContaining("reservadas");
        assertThatThrownBy(() -> ConsignmentRules.adjustment(10, 10, 0, "Igual")).hasMessageContaining("coincide");
        assertThatThrownBy(() -> ConsignmentRules.adjustment(10, 9, 0, "x")).hasMessageContaining("5 caracteres");
        assertThatThrownBy(() -> ConsignmentRules.adjustment(10, -1, 0, "Negativo")).hasMessageContaining("entre 0");
    }

    @Test
    void parsesKindsAndNumbers() {
        assertThat(ConsignmentRules.DocumentKind.parse("receipt")).isEqualTo(ConsignmentRules.DocumentKind.RECEIPT);
        assertThatThrownBy(() -> ConsignmentRules.DocumentKind.parse("SALE")).hasMessageContaining("no válido");
        assertThat(ConsignmentRules.number(42)).isEqualTo("CSG-000042");
    }
}
