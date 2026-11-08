package com.essenza.draco.modules.marketplace.domain.assignment;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;

/**
 * Vigencias de planes de un vendedor. Reglas de la primera versión (sin prorrateos):
 *
 * <ul>
 *   <li>Las vigencias de un vendedor no se solapan; un vendedor tiene a lo sumo un plan vigente.</li>
 *   <li>Sin plan vigente, la asignación empieza hoy o en una fecha futura.</li>
 *   <li>Con plan vigente, el cambio solo se programa para el inicio de un ciclo futuro (día 1 de
 *       un mes posterior); el plan actual termina el día anterior. Por defecto, el próximo ciclo.</li>
 *   <li>Solo un cambio programado a la vez; para reprogramar se cancela el anterior.</li>
 *   <li>Solo se cancela una asignación programada (no iniciada). Las vigentes y pasadas son
 *       historia: no se editan, así los cargos ya calculados conservan sus condiciones.</li>
 * </ul>
 */
public final class PlanSchedule {

    private PlanSchedule() {
    }

    public enum State {
        SCHEDULED,
        CURRENT,
        ENDED,
        CANCELLED
    }

    /** Una vigencia existente. */
    public record Period(Long id, LocalDate startsOn, LocalDate endsOn, boolean cancelled) {

        public State stateOn(LocalDate today) {
            if (cancelled) {
                return State.CANCELLED;
            }
            if (startsOn.isAfter(today)) {
                return State.SCHEDULED;
            }
            return endsOn == null || !endsOn.isBefore(today) ? State.CURRENT : State.ENDED;
        }
    }

    /**
     * Resultado de programar: inicio de la nueva vigencia y, si había plan vigente, cuál se cierra
     * y en qué fecha.
     */
    public record Decision(LocalDate startsOn, Long closesPeriodId, LocalDate closesOn) {
    }

    public static LocalDate nextCycleStart(LocalDate today) {
        return today.withDayOfMonth(1).plusMonths(1);
    }

    public static Decision schedule(Collection<Period> existing, LocalDate requestedStart, LocalDate today) {
        Optional<Period> scheduled = existing.stream().filter(p -> p.stateOn(today) == State.SCHEDULED).findFirst();
        MarketplaceRuleViolation.check(scheduled.isEmpty(),
                "Ya hay un cambio de plan programado desde el " + scheduled.map(Period::startsOn).orElse(null)
                        + ". Cancélalo antes de programar otro.");
        Optional<Period> current = existing.stream().filter(p -> p.stateOn(today) == State.CURRENT).findFirst();

        if (current.isEmpty()) {
            LocalDate start = requestedStart == null ? today : requestedStart;
            MarketplaceRuleViolation.check(!start.isBefore(today), "La vigencia no puede empezar en el pasado.");
            return new Decision(start, null, null);
        }

        LocalDate start = requestedStart == null ? nextCycleStart(today) : requestedStart;
        MarketplaceRuleViolation.check(start.getDayOfMonth() == 1 && start.isAfter(today),
                "El vendedor ya tiene un plan vigente: el cambio aplica desde el inicio de un ciclo futuro (día 1 de un mes), "
                        + "por ejemplo el " + nextCycleStart(today) + ".");
        Period now = current.get();
        LocalDate closesOn = start.minusDays(1);
        if (now.endsOn() != null && now.endsOn().isBefore(closesOn)) {
            // El plan vigente ya termina antes: no se extiende, queda un período sin plan.
            return new Decision(start, null, null);
        }
        return new Decision(start, now.id(), closesOn);
    }

    /**
     * Valida que la vigencia se pueda cancelar (solo programadas) y devuelve la vigencia actual
     * que se debe reabrir (la que se cerró al programar el cambio), si existe.
     */
    public static Optional<Period> cancel(Period scheduled, Collection<Period> existing, LocalDate today) {
        MarketplaceRuleViolation.check(scheduled.stateOn(today) == State.SCHEDULED,
                "Solo se cancela un cambio de plan programado que todavía no empieza.");
        LocalDate closedOn = scheduled.startsOn().minusDays(1);
        return existing.stream()
                .filter(p -> !p.cancelled() && p.stateOn(today) == State.CURRENT && closedOn.equals(p.endsOn()))
                .findFirst();
    }
}
