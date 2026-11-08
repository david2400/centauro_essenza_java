package com.essenza.draco.modules.sales.domain.model.dispatch;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Fecha límite de despacho de un subpedido: el día del pago más los días hábiles de alistamiento
 * (lunes a viernes). Con 0 días se despacha el mismo día hábil. Los festivos no se descuentan
 * todavía.
 */
public final class DispatchPromise {

    /** Máximo de días de alistamiento que se aceptan (evita fechas absurdas por datos erróneos). */
    public static final int MAX_HANDLING_DAYS = 60;

    private DispatchPromise() {
    }

    public static LocalDate shipBy(LocalDate paidOn, int handlingDays) {
        if (paidOn == null) {
            throw new IllegalArgumentException("Falta la fecha de pago.");
        }
        int days = Math.max(0, Math.min(handlingDays, MAX_HANDLING_DAYS));
        LocalDate date = nextBusinessDay(paidOn);
        while (days > 0) {
            date = date.plusDays(1);
            if (isBusinessDay(date)) {
                days--;
            }
        }
        return date;
    }

    public static Urgency urgency(LocalDate shipBy, LocalDate today, boolean pending) {
        if (!pending || shipBy == null) {
            return Urgency.NONE;
        }
        if (shipBy.isBefore(today)) {
            return Urgency.OVERDUE;
        }
        return shipBy.isEqual(today) ? Urgency.DUE_TODAY : Urgency.ON_TIME;
    }

    /** Días de atraso (0 si no está vencido). */
    public static long daysLate(LocalDate shipBy, LocalDate today) {
        return shipBy == null || !shipBy.isBefore(today) ? 0 : ChronoUnit.DAYS.between(shipBy, today);
    }

    static LocalDate nextBusinessDay(LocalDate date) {
        LocalDate d = date;
        while (!isBusinessDay(d)) {
            d = d.plusDays(1);
        }
        return d;
    }

    static boolean isBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    /** NONE: ya despachado o sin fecha. */
    public enum Urgency {
        NONE, ON_TIME, DUE_TODAY, OVERDUE
    }
}
