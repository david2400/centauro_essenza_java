package com.essenza.draco.modules.product_details.infrastructure.outbound.repositories.unit_measurement;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.product_details.application.dto.unit.UnitUsage;
import com.essenza.draco.modules.product_details.application.output.repository.UnitRepository;
import com.essenza.draco.modules.product_details.domain.model.unit.UnitDimension;
import com.essenza.draco.modules.product_details.domain.model.unit.UnitOfMeasure;
import com.essenza.draco.modules.product_details.infrastructure.outbound.persistence.mysql.shop.UnitMeasurementEntity;

import jakarta.persistence.EntityManager;

@Repository
public class UnitRepositoryAdapter implements UnitRepository {

    /** Referencias a la unidad desde otros módulos (consulta de solo lectura). */
    private static final String USAGE = """
            SELECT 'A', unit_id, COUNT(*) FROM attributes WHERE deleted = 0 AND unit_id IS NOT NULL GROUP BY unit_id
            UNION ALL
            SELECT 'P', net_content_unit_id, COUNT(*) FROM products WHERE deleted = 0 AND net_content_unit_id IS NOT NULL GROUP BY net_content_unit_id
            UNION ALL
            SELECT 'V', net_content_unit_id, COUNT(*) FROM product_childs WHERE deleted = 0 AND net_content_unit_id IS NOT NULL GROUP BY net_content_unit_id
            UNION ALL
            SELECT 'F', id_unit_measurement, COUNT(*) FROM feature_unit_measurement GROUP BY id_unit_measurement
            """;

    private final JpaUnitMeasurementRepository jpa;
    private final EntityManager em;

    public UnitRepositoryAdapter(JpaUnitMeasurementRepository jpa, EntityManager em) {
        this.jpa = jpa;
        this.em = em;
    }

    @Override
    public List<UnitOfMeasure> findAll() {
        return jpa.findAll().stream().map(UnitRepositoryAdapter::toDomain).toList();
    }

    @Override
    public Optional<UnitOfMeasure> findById(Long id) {
        return id == null ? Optional.empty() : jpa.findById(id).map(UnitRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<UnitOfMeasure> findByCode(String code) {
        return jpa.findFirstByCode(code).map(UnitRepositoryAdapter::toDomain);
    }

    @Override
    public boolean codeTaken(String code, Long excludeId) {
        return excludeId == null ? jpa.existsByCode(code) : jpa.existsByCodeAndIdNot(code, excludeId);
    }

    @Override
    public boolean nameTaken(String name, Long excludeId) {
        return excludeId == null ? jpa.existsByNameIgnoreCase(name) : jpa.existsByNameIgnoreCaseAndIdNot(name, excludeId);
    }

    @Override
    public UnitOfMeasure save(UnitOfMeasure unit) {
        UnitMeasurementEntity entity = unit.getId() == null ? new UnitMeasurementEntity()
                : jpa.findById(unit.getId()).orElseThrow();
        entity.setCode(unit.getCode());
        entity.setSymbol(unit.getSymbol());
        entity.setName(unit.getName());
        entity.setDimension(unit.getDimension().name());
        entity.setFactor(unit.getFactor());
        entity.setBase(unit.isBase());
        entity.setDecimals(unit.getDecimals());
        entity.setActive(unit.isActive());
        return toDomain(jpa.saveAndFlush(entity));
    }

    @Override
    public void delete(Long id) {
        jpa.deleteById(id);
        jpa.flush();
    }

    @Override
    public UnitUsage usage(Long id) {
        long a = 0, p = 0, v = 0, f = 0;
        for (Object[] row : usageRows()) {
            if (row[1] == null || ((Number) row[1]).longValue() != id) continue;
            long count = ((Number) row[2]).longValue();
            switch (String.valueOf(row[0])) {
                case "A" -> a += count;
                case "P" -> p += count;
                case "V" -> v += count;
                default -> f += count;
            }
        }
        return new UnitUsage(a, p, v, f);
    }

    @Override
    public Map<Long, Long> usageCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : usageRows()) {
            if (row[1] != null) {
                counts.merge(((Number) row[1]).longValue(), ((Number) row[2]).longValue(), Long::sum);
            }
        }
        return counts;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> usageRows() {
        return em.createNativeQuery(USAGE).getResultList();
    }

    static UnitOfMeasure toDomain(UnitMeasurementEntity e) {
        return new UnitOfMeasure(e.getId(), e.getCode(), e.getSymbol(), e.getName(), UnitDimension.parse(e.getDimension()),
                e.getFactor() == null ? BigDecimal.ONE : e.getFactor(), Boolean.TRUE.equals(e.getBase()),
                e.getDecimals() == null ? 2 : e.getDecimals(), e.getActive() == null || e.getActive());
    }
}
