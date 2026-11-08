package com.essenza.draco.modules.product_details.application.services;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.product_details.application.dto.unit.SaveUnitDto;
import com.essenza.draco.modules.product_details.application.dto.unit.UnitConversionDto;
import com.essenza.draco.modules.product_details.application.dto.unit.UnitDto;
import com.essenza.draco.modules.product_details.application.dto.unit.UnitUsage;
import com.essenza.draco.modules.product_details.application.input.unit.ManageUnitsUseCase;
import com.essenza.draco.modules.product_details.application.output.repository.UnitRepository;
import com.essenza.draco.modules.product_details.domain.model.unit.UnitDimension;
import com.essenza.draco.modules.product_details.domain.model.unit.UnitOfMeasure;
import com.essenza.draco.modules.product_details.domain.model.unit.UnitRuleViolation;
import com.essenza.draco.shared.common.lookup.LookupRequest;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;

/**
 * Reglas del catálogo de unidades:
 * <ul>
 *   <li>Código y nombre únicos (entre no borradas).</li>
 *   <li>Cada magnitud (salvo OTHER) tiene una unidad base con factor 1; la primera unidad
 *       de una magnitud pasa a ser la base. Elegir otra base reescala los factores de la magnitud.</li>
 *   <li>No se cambia la magnitud ni se borra una unidad en uso; la base no se borra mientras
 *       queden otras unidades en su magnitud.</li>
 * </ul>
 */
@Service
@Transactional
public class UnitServiceImpl implements ManageUnitsUseCase {

    private static final MathContext PRECISION = new MathContext(24, RoundingMode.HALF_UP);
    private static final Comparator<UnitOfMeasure> ORDER = Comparator
            .comparing((UnitOfMeasure u) -> u.getDimension().ordinal())
            .thenComparing(UnitOfMeasure::getFactor)
            .thenComparing(UnitOfMeasure::getName);

    private final UnitRepository units;

    public UnitServiceImpl(UnitRepository units) {
        this.units = units;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnitDto> list(String dimension, boolean includeInactive) {
        UnitDimension filter = dimension == null || dimension.isBlank() ? null : UnitDimension.parse(dimension);
        List<UnitOfMeasure> all = units.findAll();
        Map<Long, Long> usage = units.usageCounts();
        return all.stream()
                .filter(u -> filter == null || u.getDimension() == filter)
                .filter(u -> includeInactive || u.isActive())
                .sorted(ORDER)
                .map(u -> toDto(u, all, usage))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UnitDto get(Long id) {
        UnitOfMeasure unit = require(id);
        return toDto(unit, units.findAll(), units.usageCounts());
    }

    @Override
    public UnitDto create(SaveUnitDto input) {
        UnitDimension dimension = UnitDimension.parse(input.dimension());
        String code = UnitOfMeasure.normalizeCode(input.code() == null || input.code().isBlank()
                ? deriveCode(input.symbol() != null && !input.symbol().isBlank() ? input.symbol() : input.name())
                : input.code());
        checkUnique(code, input.name(), null);
        List<UnitOfMeasure> siblings = siblings(dimension, null);
        boolean first = dimension != UnitDimension.OTHER && siblings.stream().noneMatch(UnitOfMeasure::isBase);
        boolean wantsBase = dimension != UnitDimension.OTHER && (first || Boolean.TRUE.equals(input.base()));
        BigDecimal factor = dimension == UnitDimension.OTHER || first ? BigDecimal.ONE : requireFactor(input.factor());

        UnitOfMeasure draft = new UnitOfMeasure(null, code, input.symbol(), input.name(), dimension, factor,
                false, decimalsOf(input, dimension), input.active() == null || input.active() || wantsBase);
        UnitOfMeasure saved = units.save(first ? draft.rescaled(BigDecimal.ONE, true) : draft);
        if (wantsBase && !first) {
            promoteToBase(saved);
            saved = require(saved.getId());
        }
        return get(saved.getId());
    }

    @Override
    public UnitDto update(Long id, SaveUnitDto input) {
        UnitOfMeasure current = require(id);
        UnitDimension dimension = input.dimension() == null || input.dimension().isBlank()
                ? current.getDimension() : UnitDimension.parse(input.dimension());
        String code = input.code() == null || input.code().isBlank() ? current.getCode() : UnitOfMeasure.normalizeCode(input.code());
        checkUnique(code, input.name(), id);

        boolean dimensionChanged = dimension != current.getDimension();
        if (dimensionChanged) {
            UnitUsage usage = units.usage(id);
            if (usage.total() > 0) {
                throw new ConflictException("No se puede cambiar la magnitud: la unidad se usa en " + usage.describe() + ".");
            }
            if (current.isBase() && !siblings(current.getDimension(), id).isEmpty()) {
                throw new ConflictException("Es la unidad base de " + current.getDimension()
                        + ": elige otra base antes de moverla de magnitud.");
            }
        }
        if (!dimensionChanged && current.isBase() && Boolean.FALSE.equals(input.base())) {
            throw new UnitRuleViolation("Para quitar la base, marca otra unidad de la magnitud como base.");
        }

        List<UnitOfMeasure> siblings = siblings(dimension, id);
        boolean onlyOne = dimension != UnitDimension.OTHER && siblings.stream().noneMatch(UnitOfMeasure::isBase);
        boolean keepBase = !dimensionChanged && current.isBase();
        boolean wantsBase = dimension != UnitDimension.OTHER && (onlyOne || keepBase || Boolean.TRUE.equals(input.base()));
        BigDecimal factor;
        if (dimension == UnitDimension.OTHER || onlyOne || keepBase) {
            factor = BigDecimal.ONE;
        } else {
            factor = input.factor() != null ? input.factor() : dimensionChanged ? requireFactor(null) : current.getFactor();
        }
        boolean active = input.active() == null ? current.isActive() : input.active();
        if ((keepBase || onlyOne) && !active) {
            throw new UnitRuleViolation("La unidad base de una magnitud no se puede desactivar.");
        }

        UnitOfMeasure updated = new UnitOfMeasure(id, code, input.symbol() == null ? current.getSymbol() : input.symbol(),
                input.name(), dimension, factor, keepBase || onlyOne, decimalsOf(input, dimension, current.getDecimals()), active);
        units.save(updated);
        if (wantsBase && !(keepBase || onlyOne)) {
            promoteToBase(require(id));
        }
        return get(id);
    }

    @Override
    public void delete(Long id) {
        UnitOfMeasure unit = require(id);
        UnitUsage usage = units.usage(id);
        if (usage.total() > 0) {
            throw new ConflictException("No se puede eliminar: la unidad se usa en " + usage.describe()
                    + ". Puedes desactivarla para que no se ofrezca más.");
        }
        if (unit.isBase() && !siblings(unit.getDimension(), id).isEmpty()) {
            throw new ConflictException("Es la unidad base de su magnitud: elige otra base antes de eliminarla.");
        }
        units.delete(id);
    }

    @Override
    @Transactional(readOnly = true)
    public UnitConversionDto convert(BigDecimal value, String from, String to) {
        if (value == null) {
            throw new UnitRuleViolation("Indica el valor a convertir.");
        }
        UnitOfMeasure source = resolve(from);
        UnitOfMeasure target = resolve(to);
        BigDecimal result = source.convert(value, target);
        return new UnitConversionDto(value, source.getCode(), source.getSymbol(), target.getCode(), target.getSymbol(), result);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnitDto> lookup(LookupRequest request, List<String> dimensions) {
        Set<UnitDimension> filter = dimensions == null ? Set.of()
                : dimensions.stream().filter(d -> d != null && !d.isBlank()).map(UnitDimension::parse).collect(Collectors.toSet());
        String q = request.q().toLowerCase(Locale.ROOT);
        List<UnitOfMeasure> all = units.findAll();
        Map<Long, Long> usage = units.usageCounts();
        return all.stream()
                .filter(u -> request.byIds() ? request.ids().contains(u.getId())
                        : u.isActive() && (filter.isEmpty() || filter.contains(u.getDimension()))
                        && (q.isEmpty() || u.getCode().contains(q) || u.getSymbol().toLowerCase(Locale.ROOT).contains(q)
                        || u.getName().toLowerCase(Locale.ROOT).contains(q)))
                .sorted(ORDER)
                .limit(request.limit())
                .map(u -> toDto(u, all, usage))
                .toList();
    }

    // ─── Base y reescalado ──────────────────────────────────────────────────────

    /**
     * {@code unit} pasa a ser la base: todos los factores de su magnitud se dividen por
     * su factor actual, de modo que ella queda en 1 y las conversiones no cambian.
     */
    private void promoteToBase(UnitOfMeasure unit) {
        BigDecimal pivot = unit.getFactor();
        for (UnitOfMeasure other : siblings(unit.getDimension(), unit.getId())) {
            units.save(other.rescaled(other.getFactor().divide(pivot, PRECISION), false));
        }
        units.save(unit.rescaled(BigDecimal.ONE, true));
    }

    private List<UnitOfMeasure> siblings(UnitDimension dimension, Long excludeId) {
        return units.findAll().stream()
                .filter(u -> u.getDimension() == dimension && !u.getId().equals(excludeId))
                .toList();
    }

    // ─── Apoyo ──────────────────────────────────────────────────────────────────

    private void checkUnique(String code, String name, Long excludeId) {
        if (units.codeTaken(code, excludeId)) {
            throw new ConflictException("Ya existe una unidad con el código " + code + ".");
        }
        if (name != null && units.nameTaken(name.trim(), excludeId)) {
            throw new ConflictException("Ya existe una unidad llamada " + name.trim() + ".");
        }
    }

    private static BigDecimal requireFactor(BigDecimal factor) {
        if (factor == null || factor.signum() <= 0) {
            throw new UnitRuleViolation("Indica el factor: cuántas unidades base de la magnitud equivale esta unidad.");
        }
        return factor;
    }

    private static int decimalsOf(SaveUnitDto input, UnitDimension dimension) {
        return decimalsOf(input, dimension, dimension == UnitDimension.COUNT ? 0 : 2);
    }

    private static int decimalsOf(SaveUnitDto input, UnitDimension dimension, int fallback) {
        return input.decimals() != null ? input.decimals() : fallback;
    }

    private UnitOfMeasure require(Long id) {
        return units.findById(id).orElseThrow(() -> new NotFoundException("Unidad no encontrada: " + id));
    }

    private UnitOfMeasure resolve(String ref) {
        if (ref == null || ref.isBlank()) {
            throw new UnitRuleViolation("Indica las unidades de origen y destino.");
        }
        String value = ref.trim();
        Optional<UnitOfMeasure> found = value.matches("\\d{1,18}")
                ? units.findById(Long.valueOf(value))
                : units.findByCode(value.toLowerCase(Locale.ROOT));
        return found.orElseThrow(() -> new NotFoundException("Unidad no encontrada: " + value));
    }

    /** "Bulto x 25" → "bulto_x_25" (sin tildes, máx. 20). */
    static String deriveCode(String text) {
        String plain = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String code = plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (code.isEmpty()) {
            code = "unidad";
        }
        return code.length() > 20 ? code.substring(0, 20).replaceAll("_+$", "") : code;
    }

    private static UnitDto toDto(UnitOfMeasure unit, List<UnitOfMeasure> all, Map<Long, Long> usage) {
        String baseSymbol = unit.getDimension() == UnitDimension.OTHER ? null : all.stream()
                .filter(u -> u.getDimension() == unit.getDimension() && u.isBase())
                .map(UnitOfMeasure::getSymbol).findFirst().orElse(null);
        return new UnitDto(unit.getId(), unit.getCode(), unit.getSymbol(), unit.getName(), unit.getDimension().name(),
                unit.getFactor(), unit.isBase(), baseSymbol, unit.getDecimals(), unit.isActive(),
                usage.getOrDefault(unit.getId(), 0L));
    }
}
