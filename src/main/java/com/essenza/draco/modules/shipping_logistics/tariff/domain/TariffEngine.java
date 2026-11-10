package com.essenza.draco.modules.shipping_logistics.tariff.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.Bracket;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.Destination;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.Place;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.RateCard;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.Zone;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.ZoneRate;

/**
 * Cotización pura (sin base de datos): zona del destino, peso cobrable, flete por franja, seguro, envío gratis y
 * fechas de entrega en días hábiles. Mismo resultado para la orden, el despacho y la tienda en línea.
 */
public final class TariffEngine {

    private TariffEngine() {
    }

    /** Unidad o grupo de unidades a enviar. Peso en kg y medidas en cm (las del catálogo); pueden faltar. */
    public record Piece(BigDecimal weightKg, BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm, int quantity) {
    }

    public record Input(Destination origin, Destination destination, List<Piece> pieces, BigDecimal declaredValue,
                        BigDecimal orderSubtotal, LocalDate shipDate) {
        public Input {
            pieces = pieces == null ? List.of() : List.copyOf(pieces);
        }
    }

    /** Opción de envío de un tarifario. {@code total} es lo que se cobra al cliente (0 si aplica envío gratis). */
    public record Option(RateCard card, String zoneKey, String zoneName, BigDecimal actualKg, BigDecimal volumetricKg,
                         BigDecimal chargeableKg, boolean weightEstimated, BigDecimal freight, BigDecimal insurance,
                         BigDecimal total, boolean free, int minDays, int maxDays, LocalDate deliveryFrom, LocalDate deliveryTo) {
    }

    /** Resultado: opciones ordenadas por valor y plazo, y la zona que resolvió el destino (para diagnóstico). */
    public record Result(String zoneKey, String zoneName, List<Option> options) {
        public boolean covered() {
            return !options.isEmpty();
        }
    }

    public static Result quote(List<RateCard> cards, List<Zone> zones, Input in) {
        Destination dest = in.destination();
        if (dest == null || dest.countryId() == null || dest.stateId() == null || dest.cityId() == null) {
            throw new TariffRuleViolation("Para cotizar el envío elige país, departamento y ciudad de destino.");
        }
        Optional<Zone> special = zoneFor(zones, dest);
        String route = route(in.origin(), dest);
        String zoneKey = special.map(Zone::key).orElse(route);
        String zoneName = special.map(Zone::name).orElse(null);

        List<Option> options = new ArrayList<>();
        for (RateCard card : cards) {
            if (!card.active()) {
                continue;
            }
            Optional<ZoneRate> rate = special.isPresent() ? rateFor(card, zoneKey) : routeRate(card, route);
            rate.flatMap(r -> option(card, r, zoneKey, zoneName, in)).ifPresent(options::add);
        }
        options.sort(Comparator.comparing(Option::total).thenComparingInt(Option::maxDays).thenComparing(o -> o.card().carrierName(),
                Comparator.nullsLast(Comparator.naturalOrder())));
        return new Result(zoneKey, zoneName, List.copyOf(options));
    }

    /** Trayecto automático: misma ciudad, mismo departamento o nacional. Sin origen conocido se cobra nacional. */
    public static String route(Destination origin, Destination dest) {
        if (origin == null || origin.countryId() == null || !origin.countryId().equals(dest.countryId())) {
            return Tariff.NATIONAL;
        }
        if (origin.cityId() != null && origin.cityId().equals(dest.cityId())) {
            return Tariff.LOCAL;
        }
        if (origin.stateId() != null && origin.stateId().equals(dest.stateId())) {
            return Tariff.REGIONAL;
        }
        return Tariff.NATIONAL;
    }

    /** Zona propia más específica que contiene el destino (ciudad, luego departamento, luego país). */
    public static Optional<Zone> zoneFor(List<Zone> zones, Destination dest) {
        Zone best = null;
        int bestSpecificity = -1;
        for (Zone zone : zones) {
            if (!zone.active()) {
                continue;
            }
            for (Place place : zone.places()) {
                if (place.contains(dest) && place.specificity() > bestSpecificity) {
                    best = zone;
                    bestSpecificity = place.specificity();
                }
            }
        }
        return Optional.ofNullable(best);
    }

    static Optional<ZoneRate> rateFor(RateCard card, String key) {
        return card.rates().stream().filter(r -> r.active() && r.zoneKey().equals(key)).findFirst();
    }

    /** Si el tarifario no define el trayecto exacto, usa el siguiente más amplio (local → regional → nacional). */
    static Optional<ZoneRate> routeRate(RateCard card, String route) {
        List<String> order = switch (route) {
            case Tariff.LOCAL -> List.of(Tariff.LOCAL, Tariff.REGIONAL, Tariff.NATIONAL);
            case Tariff.REGIONAL -> List.of(Tariff.REGIONAL, Tariff.NATIONAL);
            default -> List.of(Tariff.NATIONAL);
        };
        for (String key : order) {
            Optional<ZoneRate> rate = rateFor(card, key);
            if (rate.isPresent()) {
                return rate;
            }
        }
        return Optional.empty();
    }

    static Optional<Option> option(RateCard card, ZoneRate rate, String zoneKey, String zoneName, Input in) {
        Weight w = weight(card, in.pieces());
        Optional<Bracket> bracket = rate.brackets().stream().filter(b -> b.covers(w.chargeable)).findFirst();
        if (bracket.isEmpty()) {
            return Optional.empty(); // más pesado que la última franja: esta transportadora no lo lleva
        }
        BigDecimal freight = bracket.get().priceFor(w.chargeable).max(card.minCharge()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal insurance = insurance(card, in.declaredValue());
        BigDecimal cost = freight.add(insurance);
        boolean free = rate.freeFrom() != null && in.orderSubtotal() != null && in.orderSubtotal().compareTo(rate.freeFrom()) >= 0;
        LocalDate ship = in.shipDate();
        return Optional.of(new Option(card, Tariff.isRoute(rate.zoneKey()) ? rate.zoneKey() : zoneKey,
                Tariff.isRoute(rate.zoneKey()) ? null : zoneName, w.actual, w.volumetric, w.chargeable, w.estimated, freight,
                insurance, free ? BigDecimal.ZERO.setScale(2) : cost, free, rate.minDays(), rate.maxDays(),
                ship == null ? null : addBusinessDays(ship, rate.minDays()), ship == null ? null : addBusinessDays(ship, rate.maxDays())));
    }

    record Weight(BigDecimal actual, BigDecimal volumetric, BigDecimal chargeable, boolean estimated) {
    }

    /**
     * Peso cobrable = el mayor entre el real y el volumétrico (largo × ancho × alto ÷ factor), redondeado hacia
     * arriba al kg (como cobran las transportadoras) y mínimo 1 kg. Sin peso registrado se usa el del tarifario y
     * la cotización queda marcada como estimada.
     */
    static Weight weight(RateCard card, List<Piece> pieces) {
        BigDecimal actual = BigDecimal.ZERO;
        BigDecimal volumetric = BigDecimal.ZERO;
        boolean estimated = pieces.isEmpty();
        BigDecimal divisor = BigDecimal.valueOf(card.volumetricDivisor());
        for (Piece p : pieces) {
            BigDecimal qty = BigDecimal.valueOf(Math.max(p.quantity(), 0));
            BigDecimal unit = p.weightKg() != null && p.weightKg().signum() > 0 ? p.weightKg() : null;
            if (unit == null) {
                estimated = true;
                unit = card.defaultWeightKg();
            }
            actual = actual.add(unit.multiply(qty));
            if (positive(p.lengthCm()) && positive(p.widthCm()) && positive(p.heightCm())) {
                volumetric = volumetric.add(p.lengthCm().multiply(p.widthCm()).multiply(p.heightCm()).multiply(qty)
                        .divide(divisor, 3, RoundingMode.HALF_UP));
            }
        }
        if (pieces.isEmpty()) {
            actual = card.defaultWeightKg();
        }
        BigDecimal chargeable = actual.max(volumetric).setScale(0, RoundingMode.CEILING).max(BigDecimal.ONE);
        return new Weight(actual.setScale(3, RoundingMode.HALF_UP), volumetric.setScale(3, RoundingMode.HALF_UP), chargeable, estimated);
    }

    static BigDecimal insurance(RateCard card, BigDecimal declared) {
        if (declared == null || declared.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal byPct = declared.multiply(card.insurancePct()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (card.insurancePct().signum() == 0 && card.insuranceMin().signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return byPct.max(card.insuranceMin()).setScale(2, RoundingMode.HALF_UP);
    }

    /** Suma días hábiles (lunes a viernes). Los festivos no se descuentan: la promesa usa el máximo del rango. */
    public static LocalDate addBusinessDays(LocalDate from, int days) {
        LocalDate d = from;
        int left = days;
        while (left > 0) {
            d = d.plusDays(1);
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                left--;
            }
        }
        return d;
    }

    private static boolean positive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }
}
