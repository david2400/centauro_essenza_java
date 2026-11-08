package com.essenza.draco.modules.marketplace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.marketplace.domain.assignment.PlanSchedule;
import com.essenza.draco.modules.marketplace.domain.assignment.PlanSchedule.Decision;
import com.essenza.draco.modules.marketplace.domain.assignment.PlanSchedule.Period;
import com.essenza.draco.modules.marketplace.domain.assignment.PlanSchedule.State;

class PlanScheduleTest {

    private final LocalDate today = LocalDate.of(2026, 11, 5);

    @Test
    void firstAssignmentStartsTodayOrLater() {
        Decision d = PlanSchedule.schedule(List.of(), null, today);
        assertThat(d.startsOn()).isEqualTo(today);
        assertThat(d.closesPeriodId()).isNull();
        assertThat(PlanSchedule.schedule(List.of(), today.plusDays(3), today).startsOn()).isEqualTo(today.plusDays(3));
        assertThatThrownBy(() -> PlanSchedule.schedule(List.of(), today.minusDays(1), today)).hasMessageContaining("pasado");
    }

    @Test
    void changeGoesToNextCycleAndClosesCurrent() {
        List<Period> existing = List.of(new Period(7L, LocalDate.of(2026, 9, 1), null, false));
        Decision d = PlanSchedule.schedule(existing, null, today);
        assertThat(d.startsOn()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(d.closesPeriodId()).isEqualTo(7L);
        assertThat(d.closesOn()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThatThrownBy(() -> PlanSchedule.schedule(existing, LocalDate.of(2026, 12, 15), today))
                .hasMessageContaining("día 1");
        assertThatThrownBy(() -> PlanSchedule.schedule(existing, LocalDate.of(2026, 11, 1), today))
                .hasMessageContaining("ciclo futuro");
        assertThat(PlanSchedule.schedule(existing, LocalDate.of(2027, 2, 1), today).closesOn())
                .isEqualTo(LocalDate.of(2027, 1, 31));
    }

    @Test
    void onlyOneScheduledChange() {
        List<Period> existing = List.of(
                new Period(7L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30), false),
                new Period(8L, LocalDate.of(2026, 12, 1), null, false));
        assertThatThrownBy(() -> PlanSchedule.schedule(existing, null, today)).hasMessageContaining("Cancélalo");
    }

    @Test
    void cancellingAScheduledChangeReopensTheCurrentPlan() {
        Period current = new Period(7L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30), false);
        Period scheduled = new Period(8L, LocalDate.of(2026, 12, 1), null, false);
        assertThat(PlanSchedule.cancel(scheduled, List.of(current, scheduled), today)).contains(current);
        assertThatThrownBy(() -> PlanSchedule.cancel(current, List.of(current, scheduled), today))
                .hasMessageContaining("programado");
    }

    @Test
    void states() {
        assertThat(new Period(1L, today, null, false).stateOn(today)).isEqualTo(State.CURRENT);
        assertThat(new Period(1L, today.plusDays(1), null, false).stateOn(today)).isEqualTo(State.SCHEDULED);
        assertThat(new Period(1L, today.minusDays(9), today.minusDays(1), false).stateOn(today)).isEqualTo(State.ENDED);
        assertThat(new Period(1L, today.plusDays(1), null, true).stateOn(today)).isEqualTo(State.CANCELLED);
        assertThat(PlanSchedule.nextCycleStart(LocalDate.of(2026, 12, 31))).isEqualTo(LocalDate.of(2027, 1, 1));
    }
}
