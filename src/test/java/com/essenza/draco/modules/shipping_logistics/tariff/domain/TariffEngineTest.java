package com.essenza.draco.modules.shipping_logistics.tariff.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.Bracket;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.Destination;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.Place;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.RateCard;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.Zone;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.ZoneRate;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.TariffEngine.Input;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.TariffEngine.Piece;

class TariffEngineTest {

    // Colombia = 1; Antioquia = 10 (Medellín 100, Envigado 101); Cundinamarca = 20 (Bogotá 200); San Andrés = 30 (300)
    static final Destination MEDELLIN = new Destination(1L, 10L, 100L);
    static final Destination ENVIGADO = new Destination(1L, 10L, 101L);
    static final Destination BOGOTA = new Destination(1L, 20L, 200L);
    static final Destination SAN_ANDRES = new Destination(1L, 30L, 300L);
    static final LocalDate FRIDAY = LocalDate.of(2026, 11, 13);

    static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    static List<Bracket> brackets(String first, String second, String perKg) {
        return List.of(new Bracket(d("0"), d("5"), d(first), null), new Bracket(d("5"), null, d(second), d(perKg)));
    }

    static ZoneRate rate(String key, int min, int max, String free, List<Bracket> brackets) {
        return Tariff.checkRate(key, min, max, free == null ? null : d(free), true, brackets);
    }

    static RateCard card(long id, String name, List<ZoneRate> rates) {
        return new RateCard(id, id, name, name, 5000, d("1"), d("0"), d("1"), d("0"), true, 1, rates);
    }

    static Input input(Destination origin, Destination dest, List<Piece> pieces) {
        return new Input(origin, dest, pieces, null, d("50000"), FRIDAY);
    }

    @Test
    void routeDependsOnOriginAndDestination() {
        assertThat(TariffEngine.route(MEDELLIN, MEDELLIN)).isEqualTo(Tariff.LOCAL);
        assertThat(TariffEngine.route(MEDELLIN, ENVIGADO)).isEqualTo(Tariff.REGIONAL);
        assertThat(TariffEngine.route(MEDELLIN, BOGOTA)).isEqualTo(Tariff.NATIONAL);
        assertThat(TariffEngine.route(null, BOGOTA)).isEqualTo(Tariff.NATIONAL);
    }

    @Test
    void missingRouteFallsBackToWiderOneButSpecialZoneDoesNot() {
        RateCard onlyNational = card(1, "Nacional", List.of(rate("NATIONAL", 2, 4, null, brackets("12000", "20000", "1500"))));
        Zone special = new Zone(7, "ESPECIAL", "Trayecto especial", true, List.of(Place.of(1L, 30L, null)));

        var local = TariffEngine.quote(List.of(onlyNational), List.of(special), input(MEDELLIN, MEDELLIN, List.of()));
        assertThat(local.zoneKey()).isEqualTo(Tariff.LOCAL);
        assertThat(local.options()).singleElement().satisfies(o -> assertThat(o.zoneKey()).isEqualTo(Tariff.NATIONAL));

        var island = TariffEngine.quote(List.of(onlyNational), List.of(special), input(MEDELLIN, SAN_ANDRES, List.of()));
        assertThat(island.zoneKey()).isEqualTo("Z7");
        assertThat(island.covered()).isFalse();
    }

    @Test
    void chargeableWeightUsesVolumetricAndRoundsUp() {
        RateCard c = card(1, "A", List.of(rate("NATIONAL", 1, 3, null, brackets("10000", "20000", "1000"))));
        // 2 cajas de 0,5 kg de 40×30×20 cm: real 1 kg, volumétrico 2×24000/5000 = 9,6 kg → cobra 10 kg
        var r = TariffEngine.quote(List.of(c), List.of(), input(MEDELLIN, BOGOTA,
                List.of(new Piece(d("0.5"), d("40"), d("30"), d("20"), 2))));
        var o = r.options().get(0);
        assertThat(o.actualKg()).isEqualByComparingTo("1");
        assertThat(o.volumetricKg()).isEqualByComparingTo("9.6");
        assertThat(o.chargeableKg()).isEqualByComparingTo("10");
        // franja 5+: 20000 + 5 kg adicionales × 1000
        assertThat(o.freight()).isEqualByComparingTo("25000");
        assertThat(o.weightEstimated()).isFalse();
    }

    @Test
    void missingWeightUsesDefaultAndIsFlagged() {
        RateCard c = card(1, "A", List.of(rate("NATIONAL", 1, 3, null, brackets("10000", "20000", "1000"))));
        var o = TariffEngine.quote(List.of(c), List.of(), input(MEDELLIN, BOGOTA, List.of(new Piece(null, null, null, null, 3))))
                .options().get(0);
        assertThat(o.chargeableKg()).isEqualByComparingTo("3");
        assertThat(o.weightEstimated()).isTrue();
    }

    @Test
    void insuranceMinChargeFreeShippingAndBusinessDays() {
        RateCard c = new RateCard(1, 1, "A", "A", 5000, d("1"), d("15000"), d("1"), d("2000"), true, 1,
                List.of(rate("NATIONAL", 2, 3, "100000", brackets("10000", "20000", "1000"))));
        var paid = TariffEngine.quote(List.of(c), List.of(),
                new Input(MEDELLIN, BOGOTA, List.of(), d("500000"), d("60000"), FRIDAY)).options().get(0);
        assertThat(paid.freight()).isEqualByComparingTo("15000"); // mínimo
        assertThat(paid.insurance()).isEqualByComparingTo("5000"); // 1 % de 500.000
        assertThat(paid.total()).isEqualByComparingTo("20000");
        assertThat(paid.deliveryFrom()).isEqualTo(LocalDate.of(2026, 11, 17)); // viernes + 2 hábiles = martes
        assertThat(paid.deliveryTo()).isEqualTo(LocalDate.of(2026, 11, 18));

        var free = TariffEngine.quote(List.of(c), List.of(),
                new Input(MEDELLIN, BOGOTA, List.of(), null, d("100000"), FRIDAY)).options().get(0);
        assertThat(free.free()).isTrue();
        assertThat(free.total()).isEqualByComparingTo("0");
        assertThat(free.freight()).isEqualByComparingTo("15000");
    }

    @Test
    void optionsAreSortedAndHeavierThanLastBracketIsNotCovered() {
        RateCard cheap = card(1, "Barata", List.of(rate("NATIONAL", 4, 6, null,
                List.of(new Bracket(d("0"), d("10"), d("9000"), null))))); // solo hasta 10 kg
        RateCard fast = card(2, "Rápida", List.of(rate("NATIONAL", 1, 2, null, brackets("15000", "25000", "2000"))));
        var light = TariffEngine.quote(List.of(fast, cheap), List.of(), input(MEDELLIN, BOGOTA, List.of()));
        assertThat(light.options()).extracting(o -> o.card().name()).containsExactly("Barata", "Rápida");
        var heavy = TariffEngine.quote(List.of(fast, cheap), List.of(), input(MEDELLIN, BOGOTA,
                List.of(new Piece(d("12"), null, null, null, 1))));
        assertThat(heavy.options()).extracting(o -> o.card().name()).containsExactly("Rápida");
    }

    @Test
    void mostSpecificZoneWins() {
        Zone antioquia = new Zone(1, "ANT", "Antioquia", true, List.of(Place.of(1L, 10L, null)));
        Zone envigado = new Zone(2, "ENV", "Envigado", true, List.of(Place.of(1L, 10L, 101L)));
        assertThat(TariffEngine.zoneFor(List.of(antioquia, envigado), ENVIGADO)).get().extracting(Zone::code).isEqualTo("ENV");
        assertThat(TariffEngine.zoneFor(List.of(antioquia, envigado), MEDELLIN)).get().extracting(Zone::code).isEqualTo("ANT");
        assertThat(TariffEngine.zoneFor(List.of(antioquia, envigado), BOGOTA)).isEmpty();
    }

    @Test
    void bracketsMustBeContiguousFromZero() {
        assertThatThrownBy(() -> rate("NATIONAL", 1, 2, null, List.of(new Bracket(d("1"), null, d("10"), null))))
                .hasMessageContaining("0 kg");
        assertThatThrownBy(() -> rate("NATIONAL", 1, 2, null,
                List.of(new Bracket(d("0"), d("5"), d("10"), null), new Bracket(d("6"), null, d("20"), null))))
                .hasMessageContaining("continuas");
        assertThatThrownBy(() -> rate("NATIONAL", 1, 2, null,
                List.of(new Bracket(d("0"), null, d("10"), null), new Bracket(d("5"), null, d("20"), null))))
                .hasMessageContaining("última");
        assertThatThrownBy(() -> rate("NATIONAL", 3, 2, null, brackets("1", "2", "1"))).hasMessageContaining("Días");
        assertThatThrownBy(() -> Tariff.checkZoneKey("PLANETA")).hasMessageContaining("Zona no válida");
        assertThatThrownBy(() -> Tariff.code("local")).hasMessageContaining("distinto de LOCAL");
    }

    @Test
    void destinationNeedsCity() {
        assertThatThrownBy(() -> TariffEngine.quote(List.of(), List.of(), input(MEDELLIN, new Destination(1L, 10L, null), List.of())))
                .hasMessageContaining("ciudad");
    }
}
