package com.essenza.draco.modules.product_details.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.product_details.application.dto.unit.SaveUnitDto;
import com.essenza.draco.modules.product_details.application.dto.unit.UnitDto;
import com.essenza.draco.modules.product_details.application.dto.unit.UnitUsage;
import com.essenza.draco.modules.product_details.application.output.repository.UnitRepository;
import com.essenza.draco.modules.product_details.application.services.UnitServiceImpl;
import com.essenza.draco.modules.product_details.domain.model.unit.UnitDimension;
import com.essenza.draco.modules.product_details.domain.model.unit.UnitOfMeasure;
import com.essenza.draco.modules.product_details.domain.model.unit.UnitRuleViolation;
import com.essenza.draco.shared.common.lookup.LookupRequest;
import com.essenza.draco.shared.exceptions.ConflictException;

class UnitsTest {

    /** Repositorio en memoria. */
    static class FakeUnits implements UnitRepository {
        final Map<Long, UnitOfMeasure> rows = new HashMap<>();
        final Map<Long, UnitUsage> usage = new HashMap<>();
        final AtomicLong seq = new AtomicLong();

        public List<UnitOfMeasure> findAll() { return new ArrayList<>(rows.values()); }
        public Optional<UnitOfMeasure> findById(Long id) { return Optional.ofNullable(rows.get(id)); }
        public Optional<UnitOfMeasure> findByCode(String code) {
            return rows.values().stream().filter(u -> u.getCode().equals(code)).findFirst();
        }
        public boolean codeTaken(String code, Long excludeId) {
            return rows.values().stream().anyMatch(u -> u.getCode().equals(code) && !u.getId().equals(excludeId));
        }
        public boolean nameTaken(String name, Long excludeId) {
            return rows.values().stream().anyMatch(u -> u.getName().equalsIgnoreCase(name) && !u.getId().equals(excludeId));
        }
        public UnitOfMeasure save(UnitOfMeasure unit) {
            Long id = unit.getId() == null ? seq.incrementAndGet() : unit.getId();
            UnitOfMeasure stored = new UnitOfMeasure(id, unit.getCode(), unit.getSymbol(), unit.getName(), unit.getDimension(),
                    unit.getFactor(), unit.isBase(), unit.getDecimals(), unit.isActive());
            rows.put(id, stored);
            return stored;
        }
        public void delete(Long id) { rows.remove(id); }
        public UnitUsage usage(Long id) { return usage.getOrDefault(id, new UnitUsage(0, 0, 0, 0)); }
        public Map<Long, Long> usageCounts() {
            Map<Long, Long> m = new HashMap<>();
            usage.forEach((k, v) -> m.put(k, v.total()));
            return m;
        }
    }

    private FakeUnits repo;
    private UnitServiceImpl service;

    private static SaveUnitDto unit(String code, String name, String dim, String factor, Boolean base) {
        return new SaveUnitDto(code, null, name, dim, factor == null ? null : new BigDecimal(factor), base, null, null);
    }

    @BeforeEach
    void setUp() {
        repo = new FakeUnits();
        service = new UnitServiceImpl(repo);
    }

    @Test
    void firstUnitOfDimensionBecomesBase() {
        UnitDto kg = service.create(unit("kg", "Kilogramo", "MASS", "5", false));
        assertThat(kg.base()).isTrue();
        assertThat(kg.factor()).isEqualByComparingTo("1");
        UnitDto g = service.create(unit("g", "Gramo", "MASS", "0.001", null));
        assertThat(g.base()).isFalse();
        assertThat(g.baseSymbol()).isEqualTo("kg");
    }

    @Test
    void convertsWithinDimensionOnly() {
        service.create(unit("l", "Litro", "VOLUME", null, null));
        service.create(unit("ml", "Mililitro", "VOLUME", "0.001", null));
        service.create(unit("kg", "Kilogramo", "MASS", null, null));
        assertThat(service.convert(new BigDecimal("2.5"), "l", "ml").result()).isEqualByComparingTo("2500");
        assertThat(service.convert(new BigDecimal("750"), "ml", "l").result()).isEqualByComparingTo("0.75");
        assertThatThrownBy(() -> service.convert(BigDecimal.ONE, "l", "kg")).isInstanceOf(UnitRuleViolation.class);
    }

    @Test
    void promotingBaseRescalesSiblingsKeepingConversions() {
        service.create(unit("kg", "Kilogramo", "MASS", null, null));
        service.create(unit("lb", "Libra", "MASS", "0.45359237", null));
        UnitDto g = service.create(unit("g", "Gramo", "MASS", "0.001", true));
        assertThat(g.base()).isTrue();
        assertThat(g.factor()).isEqualByComparingTo("1");
        UnitOfMeasure kg = repo.findByCode("kg").orElseThrow();
        assertThat(kg.isBase()).isFalse();
        assertThat(kg.getFactor()).isEqualByComparingTo("1000");
        assertThat(service.convert(BigDecimal.ONE, "lb", "g").result()).isEqualByComparingTo("453.59237");
        assertThat(repo.findAll().stream().filter(u -> u.getDimension() == UnitDimension.MASS && u.isBase())).hasSize(1);
    }

    @Test
    void cannotUnsetOrDeactivateTheBase() {
        UnitDto m = service.create(unit("m", "Metro", "LENGTH", null, null));
        service.create(unit("cm", "Centímetro", "LENGTH", "0.01", null));
        assertThatThrownBy(() -> service.update(m.id(), unit("m", "Metro", "LENGTH", null, false)))
                .isInstanceOf(UnitRuleViolation.class);
        assertThatThrownBy(() -> service.update(m.id(), new SaveUnitDto("m", null, "Metro", "LENGTH", null, null, null, false)))
                .isInstanceOf(UnitRuleViolation.class);
    }

    @Test
    void deleteRules() {
        UnitDto m = service.create(unit("m", "Metro", "LENGTH", null, null));
        UnitDto cm = service.create(unit("cm", "Centímetro", "LENGTH", "0.01", null));
        assertThatThrownBy(() -> service.delete(m.id())).isInstanceOf(ConflictException.class);
        repo.usage.put(cm.id(), new UnitUsage(1, 2, 0, 0));
        assertThatThrownBy(() -> service.delete(cm.id())).isInstanceOf(ConflictException.class)
                .hasMessageContaining("1 atributo(s), 2 producto(s)");
        repo.usage.clear();
        service.delete(cm.id());
        service.delete(m.id());
        assertThat(repo.rows).isEmpty();
    }

    @Test
    void dimensionChangeBlockedWhenInUse() {
        service.create(unit("und", "Unidad", "COUNT", null, null));
        UnitDto box = service.create(unit("caja", "Caja", "OTHER", null, null));
        repo.usage.put(box.id(), new UnitUsage(0, 1, 0, 0));
        assertThatThrownBy(() -> service.update(box.id(), unit("caja", "Caja", "COUNT", "12", null)))
                .isInstanceOf(ConflictException.class);
        repo.usage.clear();
        UnitDto moved = service.update(box.id(), unit("caja", "Caja x 12", "COUNT", "12", null));
        assertThat(moved.dimension()).isEqualTo("COUNT");
        assertThat(service.convert(new BigDecimal("3"), "caja", "und").result()).isEqualByComparingTo("36");
    }

    @Test
    void uniquenessAndDerivedCode() {
        UnitDto bulto = service.create(new SaveUnitDto(null, null, "Bulto × 25 kg", null, null, null, null, null));
        assertThat(bulto.code()).isEqualTo("bulto_25_kg");
        assertThat(bulto.dimension()).isEqualTo("OTHER");
        assertThatThrownBy(() -> service.create(unit("bulto_25_kg", "Otro", null, null, null))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.create(unit("otro", "bulto × 25 KG", null, null, null))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.create(unit("Mal Código", "X", null, null, null))).isInstanceOf(UnitRuleViolation.class);
    }

    @Test
    void lookupFiltersByDimensionAndText() {
        service.create(unit("l", "Litro", "VOLUME", null, null));
        service.create(unit("ml", "Mililitro", "VOLUME", "0.001", null));
        service.create(unit("kg", "Kilogramo", "MASS", null, null));
        assertThat(service.lookup(LookupRequest.of(null, null, null), List.of("VOLUME"))).extracting(UnitDto::code)
                .containsExactly("ml", "l");
        assertThat(service.lookup(LookupRequest.of("kilo", null, null), List.of())).extracting(UnitDto::code).containsExactly("kg");
    }
}
