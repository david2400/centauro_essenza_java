package com.essenza.draco.modules.marketplace.domain.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.essenza.draco.modules.marketplace.domain.plan.Money;

/**
 * Estado de cuenta de un vendedor con la plataforma. Cargos (comisiones, mensualidades, ajustes a
 * favor de la plataforma) son positivos; abonos (pagos, reversos) son negativos. Los abonos se
 * aplican a los cargos más antiguos primero (por fecha de vencimiento), así se sabe qué parte
 * está vencida. El interés de mora es una estimación simple (no se asienta solo).
 */
public final class SellerStatement {

    private SellerStatement() {
    }

    /**
     * @param dueOn             vencimiento del cargo (fecha + días de gracia); nulo en abonos
     * @param interestMonthlyPct interés de mora mensual pactado para ese cargo
     */
    public record Entry(long id, BigDecimal amount, LocalDate date, LocalDate dueOn, BigDecimal interestMonthlyPct) {
    }

    public record OpenCharge(long entryId, BigDecimal open, LocalDate dueOn, long daysOverdue, BigDecimal interest) {
    }

    public record Result(String currency, BigDecimal charges, BigDecimal credits, BigDecimal balance, BigDecimal overdue,
                         BigDecimal lateInterest, BigDecimal unappliedCredit, List<OpenCharge> open) {
    }

    public static Result of(String currency, List<Entry> entries, LocalDate asOf) {
        BigDecimal charges = Money.zero(currency);
        BigDecimal credits = Money.zero(currency);
        List<Entry> debits = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.date().isAfter(asOf)) {
                continue;
            }
            if (entry.amount().signum() > 0) {
                charges = charges.add(entry.amount());
                debits.add(entry);
            } else {
                credits = credits.add(entry.amount().negate());
            }
        }
        debits.sort(Comparator.comparing((Entry e) -> e.dueOn() == null ? e.date() : e.dueOn()).thenComparing(Entry::id));
        BigDecimal available = credits;
        BigDecimal overdue = Money.zero(currency);
        BigDecimal interest = Money.zero(currency);
        List<OpenCharge> open = new ArrayList<>();
        for (Entry debit : debits) {
            BigDecimal applied = available.min(debit.amount());
            available = available.subtract(applied);
            BigDecimal remaining = debit.amount().subtract(applied);
            if (remaining.signum() == 0) {
                continue;
            }
            LocalDate due = debit.dueOn() == null ? debit.date() : debit.dueOn();
            long days = due.isBefore(asOf) ? ChronoUnit.DAYS.between(due, asOf) : 0;
            BigDecimal charge = Money.zero(currency);
            if (days > 0) {
                overdue = overdue.add(remaining);
                BigDecimal pct = debit.interestMonthlyPct() == null ? BigDecimal.ZERO : debit.interestMonthlyPct();
                charge = Money.round(remaining.multiply(pct).multiply(BigDecimal.valueOf(days))
                        .divide(BigDecimal.valueOf(3000), 10, RoundingMode.HALF_UP), currency);
                interest = interest.add(charge);
            }
            open.add(new OpenCharge(debit.id(), remaining, due, days, charge));
        }
        BigDecimal balance = charges.subtract(credits);
        return new Result(currency, charges, credits, balance, overdue, interest, available, List.copyOf(open));
    }
}
