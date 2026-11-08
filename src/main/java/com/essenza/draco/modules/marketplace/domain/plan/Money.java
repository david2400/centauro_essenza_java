package com.essenza.draco.modules.marketplace.domain.plan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;

/**
 * Reglas monetarias del marketplace.
 *
 * <ul>
 *   <li>Toda cifra lleva moneda ISO 4217 explícita.</li>
 *   <li>Redondeo: {@link RoundingMode#HALF_UP} a los decimales de la moneda (COP y USD: 2).</li>
 *   <li>Se redondea <b>por operación</b> (cada venta) y luego se suma; nunca al final de un lote.</li>
 *   <li>Porcentajes con hasta 4 decimales (12.3456 %).</li>
 * </ul>
 */
public final class Money {

    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final int PERCENT_SCALE = 4;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private Money() {
    }

    /** Moneda ISO válida en mayúsculas; error de negocio si no existe. */
    public static String currency(String code) {
        String value = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        try {
            return Currency.getInstance(value).getCurrencyCode();
        } catch (IllegalArgumentException ex) {
            throw new MarketplaceRuleViolation("La moneda '" + code + "' no es un código ISO 4217 válido.");
        }
    }

    public static int digits(String currency) {
        int digits = Currency.getInstance(currency).getDefaultFractionDigits();
        return Math.max(digits, 0);
    }

    public static BigDecimal round(BigDecimal amount, String currency) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(digits(currency), ROUNDING);
    }

    public static BigDecimal zero(String currency) {
        return BigDecimal.ZERO.setScale(digits(currency), ROUNDING);
    }

    /** {@code amount × percent / 100}, redondeado a la moneda. */
    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent, String currency) {
        if (amount == null || percent == null || percent.signum() == 0) {
            return zero(currency);
        }
        return round(amount.multiply(percent).divide(HUNDRED, digits(currency) + 6, ROUNDING), currency);
    }

    /**
     * Valida un importe de entrada: no negativo y sin más decimales que la moneda (no se
     * redondea en silencio lo que escribe el usuario). Null se toma como cero.
     */
    public static BigDecimal amount(BigDecimal value, String currency, String label) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value.stripTrailingZeros();
        MarketplaceRuleViolation.check(v.signum() >= 0, label + " no puede ser negativo.");
        MarketplaceRuleViolation.check(Math.max(v.scale(), 0) <= digits(currency),
                label + " admite máximo " + digits(currency) + " decimales en " + currency + ".");
        return v.setScale(digits(currency), ROUNDING);
    }

    /** Porcentaje entre 0 y 100 con máximo 4 decimales. */
    public static BigDecimal percent(BigDecimal value, String label) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value.stripTrailingZeros();
        MarketplaceRuleViolation.check(v.signum() >= 0 && v.compareTo(HUNDRED) <= 0, label + " debe estar entre 0 y 100.");
        MarketplaceRuleViolation.check(Math.max(v.scale(), 0) <= PERCENT_SCALE, label + " admite máximo 4 decimales.");
        return v.setScale(PERCENT_SCALE, ROUNDING);
    }
}
