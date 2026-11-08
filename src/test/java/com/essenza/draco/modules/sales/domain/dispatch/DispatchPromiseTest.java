package com.essenza.draco.modules.sales.domain.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.sales.domain.model.dispatch.DispatchPromise;
import com.essenza.draco.modules.sales.domain.model.dispatch.DispatchPromise.Urgency;

class DispatchPromiseTest {

    static final LocalDate THU = LocalDate.of(2026, 11, 5);
    static final LocalDate SAT = LocalDate.of(2026, 11, 7);

    @Test
    void addsBusinessDaysSkippingWeekends() {
        assertThat(DispatchPromise.shipBy(THU, 0)).isEqualTo(THU);
        assertThat(DispatchPromise.shipBy(THU, 1)).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(DispatchPromise.shipBy(THU, 2)).isEqualTo(LocalDate.of(2026, 11, 9)); // lunes
        assertThat(DispatchPromise.shipBy(THU, 5)).isEqualTo(LocalDate.of(2026, 11, 12));
    }

    @Test
    void paidOnWeekendStartsOnMonday() {
        assertThat(DispatchPromise.shipBy(SAT, 0)).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(DispatchPromise.shipBy(SAT, 1)).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    void clampsInvalidDays() {
        assertThat(DispatchPromise.shipBy(THU, -3)).isEqualTo(THU);
        assertThat(DispatchPromise.shipBy(THU, 1000)).isEqualTo(DispatchPromise.shipBy(THU, DispatchPromise.MAX_HANDLING_DAYS));
        assertThatThrownBy(() -> DispatchPromise.shipBy(null, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void urgencyOnlyForPendingDispatch() {
        assertThat(DispatchPromise.urgency(THU, SAT, true)).isEqualTo(Urgency.OVERDUE);
        assertThat(DispatchPromise.urgency(SAT, SAT, true)).isEqualTo(Urgency.DUE_TODAY);
        assertThat(DispatchPromise.urgency(SAT.plusDays(3), SAT, true)).isEqualTo(Urgency.ON_TIME);
        assertThat(DispatchPromise.urgency(THU, SAT, false)).isEqualTo(Urgency.NONE);
        assertThat(DispatchPromise.urgency(null, SAT, true)).isEqualTo(Urgency.NONE);
        assertThat(DispatchPromise.daysLate(THU, SAT)).isEqualTo(2);
        assertThat(DispatchPromise.daysLate(SAT, SAT)).isZero();
    }
}
