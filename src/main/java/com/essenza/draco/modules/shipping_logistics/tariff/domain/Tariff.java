package com.essenza.draco.modules.shipping_logistics.tariff.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Modelo del tarifario de envíos: tarifario por transportadora, tarifas por zona y franjas de peso.
 *
 * <p>Una zona es un trayecto automático según origen y destino ({@link #LOCAL} misma ciudad, {@link #REGIONAL}
 * mismo departamento, {@link #NATIONAL} el resto) o una zona propia (p. ej. "Trayecto especial") definida por
 * lugares del catálogo de parametros. Los ids de país, departamento y ciudad son los del catálogo.
 */
public final class Tariff {

    public static final String LOCAL = "LOCAL";
    public static final String REGIONAL = "REGIONAL";
    public static final String NATIONAL = "NATIONAL";
    public static final List<String> ROUTES = List.of(LOCAL, REGIONAL, NATIONAL);
    /** Prefijo de la clave de una zona propia: {@code Z12}. */
    public static final String ZONE_PREFIX = "Z";

    static final BigDecimal MAX_MONEY = new BigDecimal("100000000");
    static final BigDecimal MAX_KG = new BigDecimal("100000");

    private Tariff() {
    }

    public static String zoneKey(long zoneId) {
        return ZONE_PREFIX + zoneId;
    }

    public static boolean isRoute(String key) {
        return ROUTES.contains(key);
    }

    /** Clave normalizada y validada: LOCAL, REGIONAL, NATIONAL o Z{id}. */
    public static String checkZoneKey(String key) {
        String k = key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
        if (isRoute(k) || k.matches(ZONE_PREFIX + "[1-9][0-9]{0,18}")) {
            return k;
        }
        throw new TariffRuleViolation("Zona no válida: " + key + " (LOCAL, REGIONAL, NATIONAL o una zona propia).");
    }

    /** Lugar del catálogo: país obligatorio; departamento y ciudad opcionales (vacío = todo el nivel). */
    public record Place(long countryId, Long stateId, Long cityId) {

        public static Place of(Long countryId, Long stateId, Long cityId) {
            if (countryId == null || countryId <= 0) {
                throw new TariffRuleViolation("Elige el país del lugar.");
            }
            if (cityId != null && stateId == null) {
                throw new TariffRuleViolation("Elige el departamento de la ciudad.");
            }
            if ((stateId != null && stateId <= 0) || (cityId != null && cityId <= 0)) {
                throw new TariffRuleViolation("Departamento o ciudad no válidos.");
            }
            return new Place(countryId, stateId, cityId);
        }

        /** Clave única por empresa: un lugar pertenece a una sola zona. */
        public String key() {
            return countryId + ":" + (stateId == null ? "*" : stateId) + ":" + (cityId == null ? "*" : cityId);
        }

        public int specificity() {
            return cityId != null ? 2 : stateId != null ? 1 : 0;
        }

        /** El destino cae dentro de este lugar. */
        public boolean contains(Destination d) {
            return d.countryId() != null && countryId == d.countryId()
                    && (stateId == null || stateId.equals(d.stateId()))
                    && (cityId == null || cityId.equals(d.cityId()));
        }
    }

    /** Origen o destino de un envío, con los ids que haya (pueden faltar niveles). */
    public record Destination(Long countryId, Long stateId, Long cityId) {
        public boolean complete() {
            return countryId != null && stateId != null && cityId != null;
        }
    }

    public record Zone(long id, String code, String name, boolean active, List<Place> places) {
        public Zone {
            places = places == null ? List.of() : List.copyOf(places);
        }

        public String key() {
            return zoneKey(id);
        }
    }

    /**
     * Franja de peso cobrable {@code (minKg, maxKg]} (la primera incluye 0). Valor fijo de la franja más, si se
     * define, un valor por cada kg (o fracción) por encima de {@code minKg}. {@code maxKg} vacío = sin límite.
     */
    public record Bracket(BigDecimal minKg, BigDecimal maxKg, BigDecimal price, BigDecimal perKgOver) {

        public boolean covers(BigDecimal kg) {
            boolean aboveMin = minKg.signum() == 0 ? kg.signum() >= 0 : kg.compareTo(minKg) > 0;
            return aboveMin && (maxKg == null || kg.compareTo(maxKg) <= 0);
        }

        public BigDecimal priceFor(BigDecimal kg) {
            if (perKgOver == null || perKgOver.signum() == 0 || kg.compareTo(minKg) <= 0) {
                return price;
            }
            BigDecimal extraKg = kg.subtract(minKg).setScale(0, RoundingMode.CEILING);
            return price.add(perKgOver.multiply(extraKg));
        }
    }

    /** Tarifa de un tarifario para una zona: días de entrega, envío gratis y franjas. */
    public record ZoneRate(long id, String zoneKey, int minDays, int maxDays, BigDecimal freeFrom, boolean active,
                           List<Bracket> brackets) {
        public ZoneRate {
            brackets = brackets == null ? List.of() : List.copyOf(brackets);
        }
    }

    /**
     * Tarifario de una transportadora.
     *
     * @param volumetricDivisor cm³ por kg para el peso volumétrico (5000 habitual; 2500 = 400 kg/m³)
     * @param defaultWeightKg   peso por unidad cuando el producto no tiene peso registrado
     * @param minCharge         flete mínimo por envío
     * @param insurancePct      % del valor declarado como seguro (manejo)
     * @param insuranceMin      seguro mínimo cuando hay valor declarado
     */
    public record RateCard(long id, long carrierId, String carrierName, String name, int volumetricDivisor,
                           BigDecimal defaultWeightKg, BigDecimal minCharge, BigDecimal insurancePct,
                           BigDecimal insuranceMin, boolean active, long version, List<ZoneRate> rates) {
        public RateCard {
            rates = rates == null ? List.of() : List.copyOf(rates);
        }
    }

    // ----------------------------------------------------------------------------------------------- validación

    /** Datos generales del tarifario, validados. */
    public static RateCard checkCard(Long carrierId, String name, Integer volumetricDivisor, BigDecimal defaultWeightKg,
                                     BigDecimal minCharge, BigDecimal insurancePct, BigDecimal insuranceMin, boolean active) {
        if (carrierId == null || carrierId <= 0) {
            throw new TariffRuleViolation("Elige la transportadora.");
        }
        String n = text(name, 120);
        if (n == null) {
            throw new TariffRuleViolation("El tarifario necesita un nombre.");
        }
        int divisor = volumetricDivisor == null ? 5000 : volumetricDivisor;
        if (divisor < 1000 || divisor > 10000) {
            throw new TariffRuleViolation("El factor volumétrico debe estar entre 1000 y 10000 cm³/kg.");
        }
        BigDecimal def = defaultWeightKg == null ? BigDecimal.ONE : defaultWeightKg;
        if (def.signum() <= 0 || def.compareTo(new BigDecimal("1000")) > 0) {
            throw new TariffRuleViolation("El peso por defecto debe ser mayor que 0 y hasta 1000 kg.");
        }
        BigDecimal pct = insurancePct == null ? BigDecimal.ZERO : insurancePct;
        if (pct.signum() < 0 || pct.compareTo(new BigDecimal("20")) > 0) {
            throw new TariffRuleViolation("El porcentaje de seguro debe estar entre 0 y 20.");
        }
        return new RateCard(0, carrierId, null, n, divisor, def.setScale(3, RoundingMode.HALF_UP),
                money(minCharge, "flete mínimo"), pct.setScale(2, RoundingMode.HALF_UP), money(insuranceMin, "seguro mínimo"),
                active, 0, List.of());
    }

    /**
     * Tarifa de una zona, validada: franjas ordenadas, contiguas desde 0 kg (sin huecos ni traslapes) y solo la
     * última sin límite. Así cada peso cae en exactamente una franja.
     */
    public static ZoneRate checkRate(String zoneKey, Integer minDays, Integer maxDays, BigDecimal freeFrom, boolean active,
                                     List<Bracket> brackets) {
        String key = checkZoneKey(zoneKey);
        int min = minDays == null ? 1 : minDays;
        int max = maxDays == null ? min : maxDays;
        if (min < 0 || max > 60 || min > max) {
            throw new TariffRuleViolation("Días de entrega no válidos: entre 0 y 60, y el mínimo no puede superar el máximo.");
        }
        BigDecimal free = freeFrom == null ? null : money(freeFrom, "envío gratis desde");
        if (brackets == null || brackets.isEmpty()) {
            throw new TariffRuleViolation("Agrega al menos una franja de peso.");
        }
        if (brackets.size() > 30) {
            throw new TariffRuleViolation("Máximo 30 franjas de peso por zona.");
        }
        List<Bracket> sorted = brackets.stream()
                .map(b -> {
                    Objects.requireNonNull(b, "franja");
                    if (b.minKg() == null || b.price() == null) {
                        throw new TariffRuleViolation("Cada franja necesita peso inicial y valor.");
                    }
                    return new Bracket(kg(b.minKg()), b.maxKg() == null ? null : kg(b.maxKg()), money(b.price(), "valor de la franja"),
                            b.perKgOver() == null ? null : money(b.perKgOver(), "valor por kg adicional"));
                })
                .sorted((a, b) -> a.minKg().compareTo(b.minKg()))
                .toList();
        if (sorted.get(0).minKg().signum() != 0) {
            throw new TariffRuleViolation("La primera franja debe empezar en 0 kg.");
        }
        for (int i = 0; i < sorted.size(); i++) {
            Bracket b = sorted.get(i);
            boolean last = i == sorted.size() - 1;
            if (b.maxKg() == null && !last) {
                throw new TariffRuleViolation("Solo la última franja puede quedar sin peso máximo.");
            }
            if (b.maxKg() != null && b.maxKg().compareTo(b.minKg()) <= 0) {
                throw new TariffRuleViolation("En cada franja el peso máximo debe ser mayor que el inicial.");
            }
            if (!last && b.maxKg().compareTo(sorted.get(i + 1).minKg()) != 0) {
                throw new TariffRuleViolation("Las franjas deben ser continuas: la franja que empieza en "
                        + sorted.get(i + 1).minKg().stripTrailingZeros().toPlainString()
                        + " kg debe empezar donde termina la anterior (" + b.maxKg().stripTrailingZeros().toPlainString() + " kg).");
            }
        }
        return new ZoneRate(0, key, min, max, free, active, sorted);
    }

    static BigDecimal money(BigDecimal value, String field) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        if (v.signum() < 0 || v.compareTo(MAX_MONEY) >= 0) {
            throw new TariffRuleViolation("Valor no válido en " + field + ".");
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal kg(BigDecimal value) {
        if (value.signum() < 0 || value.compareTo(MAX_KG) > 0) {
            throw new TariffRuleViolation("Peso no válido: " + value.toPlainString() + " kg.");
        }
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    public static String text(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim().replaceAll("\\s+", " ");
        if (v.length() > max) {
            throw new TariffRuleViolation("Texto demasiado largo (máximo " + max + " caracteres).");
        }
        return v;
    }

    public static String code(String value) {
        String v = text(value, 30);
        if (v == null) {
            throw new TariffRuleViolation("La zona necesita un código.");
        }
        v = v.toUpperCase(Locale.ROOT).replace(' ', '_');
        if (!v.matches("[A-Z0-9_-]{2,30}") || isRoute(v)) {
            throw new TariffRuleViolation("Código de zona no válido: letras, números, guion o guion bajo (2 a 30), "
                    + "distinto de LOCAL, REGIONAL y NATIONAL.");
        }
        return v;
    }
}
