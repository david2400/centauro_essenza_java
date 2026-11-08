package com.essenza.draco.modules.product_details.application.output.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.essenza.draco.modules.product_details.application.dto.unit.UnitUsage;
import com.essenza.draco.modules.product_details.domain.model.unit.UnitOfMeasure;

/** Persistencia de unidades ({@code units_measurement}, sin borradas). */
public interface UnitRepository {

    List<UnitOfMeasure> findAll();

    Optional<UnitOfMeasure> findById(Long id);

    Optional<UnitOfMeasure> findByCode(String code);

    boolean codeTaken(String code, Long excludeId);

    boolean nameTaken(String name, Long excludeId);

    UnitOfMeasure save(UnitOfMeasure unit);

    void delete(Long id);

    UnitUsage usage(Long id);

    /** Uso total por unidad (solo las que tienen alguno). */
    Map<Long, Long> usageCounts();
}
