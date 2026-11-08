package com.essenza.draco.modules.marketplace.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.marketplace.domain.consignment.StorageCharge;

class StorageChargeTest {

    static final LocalDate D1 = LocalDate.of(2026, 10, 1);

    @Test
    void countsUnitsAtTheEndOfEachDay() {
        // 10 al iniciar; el día 2 entran 5; el día 4 salen 12.
        Map<LocalDate, Integer> deltas = Map.of(D1.plusDays(1), 5, D1.plusDays(3), -12);
        assertThat(StorageCharge.unitDays(10, deltas, D1, D1.plusDays(4))).isEqualTo(10 + 15 + 15 + 3 + 3);
        assertThat(StorageCharge.unitDays(0, Map.of(), D1, D1.plusDays(30))).isZero();
    }

    @Test
    void amountUsesThePlanRate() {
        assertThat(StorageCharge.amount(46, new BigDecimal("150"), "COP")).isEqualByComparingTo("6900");
        assertThat(StorageCharge.amount(46, BigDecimal.ZERO, "COP")).isEqualByComparingTo("0");
        assertThat(StorageCharge.amount(46, null, "COP")).isEqualByComparingTo("0");
    }
}
