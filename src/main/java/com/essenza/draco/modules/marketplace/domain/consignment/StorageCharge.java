package com.essenza.draco.modules.marketplace.domain.consignment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.domain.plan.Money;

/**
 * Almacenamiento de la mercancía en consignación: unidades-día del ciclo (las unidades que el
 * vendedor tenía en bodega al cierre de cada día, en todas las bodegas) por la tarifa de su plan.
 */
public final class StorageCharge {

    private StorageCharge() {
    }

    /**
     * @param opening      unidades al iniciar {@code from}
     * @param deltasByDay  entradas (+) y salidas (−) de cada día del período
     */
    public static long unitDays(int opening, Map<LocalDate, Integer> deltasByDay, LocalDate from, LocalDate to) {
        MarketplaceRuleViolation.check(from != null && to != null && !to.isBefore(from), "El período de almacenamiento no es válido.");
        long total = 0;
        int balance = opening;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            balance += deltasByDay.getOrDefault(day, 0);
            total += Math.max(0, balance);
        }
        return total;
    }

    public static BigDecimal amount(long unitDays, BigDecimal ratePerUnitDay, String currency) {
        if (ratePerUnitDay == null || ratePerUnitDay.signum() <= 0 || unitDays <= 0) {
            return Money.zero(currency);
        }
        return Money.round(ratePerUnitDay.multiply(BigDecimal.valueOf(unitDays)), currency);
    }
}
