package com.essenza.draco.modules.sales.domain.model.storefront;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.essenza.draco.modules.sales.domain.model.order.OrderRuleViolation;

/**
 * Tarifas de envío de la tienda en línea por departamento y ciudad. Gana la más específica: ciudad y departamento,
 * solo ciudad, solo departamento y por último la general (ambos vacíos). Con {@code freeFrom} el envío es gratis si
 * el subtotal lo alcanza. Las comparaciones ignoran mayúsculas, tildes y espacios repetidos.
 */
public final class ShippingRates {

    private ShippingRates() {
    }

    public record Rate(long id, String state, String city, BigDecimal amount, BigDecimal freeFrom, Integer minDays, Integer maxDays,
                       boolean active) {

        public String stateKey() {
            return key(state);
        }

        public String cityKey() {
            return key(city);
        }
    }

    public record Quote(BigDecimal amount, BigDecimal freeFrom, Integer minDays, Integer maxDays, boolean free) {
    }

    /** Clave de comparación: minúsculas, sin tildes ni signos, espacios simples ("" = cualquiera). */
    public static String key(String value) {
        if (value == null) {
            return "";
        }
        String plain = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return plain.replaceAll("[^a-z0-9ñ ]+", " ").replaceAll("\\s+", " ").trim();
    }

    static int specificity(Rate r) {
        return (r.cityKey().isEmpty() ? 0 : 2) + (r.stateKey().isEmpty() ? 0 : 1);
    }

    public static Optional<Rate> match(List<Rate> rates, String state, String city) {
        String s = key(state);
        String c = key(city);
        return rates.stream().filter(Rate::active)
                .filter(r -> (r.cityKey().isEmpty() || r.cityKey().equals(c)) && (r.stateKey().isEmpty() || r.stateKey().equals(s)))
                .max(Comparator.comparingInt(ShippingRates::specificity));
    }

    public static Quote quote(Rate rate, BigDecimal subtotal) {
        boolean free = rate.freeFrom() != null && subtotal != null && subtotal.compareTo(rate.freeFrom()) >= 0;
        return new Quote(free ? BigDecimal.ZERO : rate.amount(), rate.freeFrom(), rate.minDays(), rate.maxDays(), free);
    }

    /** Valida y normaliza una tarifa del panel. */
    public static Rate check(String state, String city, BigDecimal amount, BigDecimal freeFrom, Integer minDays, Integer maxDays,
                             boolean active) {
        String st = text(state, 80);
        String ci = text(city, 120);
        if (amount == null || amount.signum() < 0 || amount.scale() > 2 || amount.compareTo(new BigDecimal("100000000")) >= 0) {
            throw new OrderRuleViolation("El valor del envío debe ser 0 o mayor, con máximo 2 decimales.");
        }
        if (freeFrom != null && (freeFrom.signum() <= 0 || freeFrom.scale() > 2)) {
            throw new OrderRuleViolation("El monto para envío gratis debe ser mayor que 0.");
        }
        if (minDays != null && (minDays < 0 || minDays > 90) || maxDays != null && (maxDays < 0 || maxDays > 90)) {
            throw new OrderRuleViolation("Los días de entrega van de 0 a 90.");
        }
        if (minDays != null && maxDays != null && maxDays < minDays) {
            throw new OrderRuleViolation("Los días máximos no pueden ser menos que los mínimos.");
        }
        return new Rate(0, st, ci, amount, freeFrom, minDays, maxDays, active);
    }

    private static String text(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.strip().replaceAll("\\s+", " ");
        if (v.length() > max) {
            throw new OrderRuleViolation("Nombre demasiado largo (máximo " + max + " caracteres).");
        }
        return v;
    }
}
