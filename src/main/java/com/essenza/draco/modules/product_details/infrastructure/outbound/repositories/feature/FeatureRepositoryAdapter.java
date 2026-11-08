package com.essenza.draco.modules.product_details.infrastructure.outbound.repositories.feature;

import com.essenza.draco.modules.product_details.application.output.repository.FeatureRepository;
import com.essenza.draco.modules.product_details.application.dto.feature.CreateFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.feature.FeatureDto;
import com.essenza.draco.modules.product_details.application.dto.feature.UpdateFeatureDto;
import com.essenza.draco.modules.product_details.infrastructure.outbound.mappers.FeatureMapper;
import com.essenza.draco.modules.product_details.infrastructure.outbound.persistence.mysql.shop.FeatureEntity;
import com.essenza.draco.modules.product_details.infrastructure.outbound.persistence.mysql.shop.UnitMeasurementEntity;
import com.essenza.draco.modules.product_details.infrastructure.outbound.repositories.unit_measurement.JpaUnitMeasurementRepository;
import com.essenza.draco.shared.exceptions.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class FeatureRepositoryAdapter implements FeatureRepository {

    private final JpaFeatureRepository jpa;
    private final FeatureMapper mapper;
    private final JpaUnitMeasurementRepository unitMeasurementRepository;

    @Override
    public FeatureDto create(CreateFeatureDto input) {
        FeatureEntity entity = mapper.toEntity(input);
        entity.setUnitMeasurements(buildUnitMeasurementAssociation(input.getUnitId()));
        FeatureEntity saved = jpa.save(entity);
        return mapper.toDto(saved);
    }

    @Override
    public FeatureDto update(Long id, UpdateFeatureDto input) {
        FeatureEntity entity = jpa.findById(id)
                .orElseThrow(() -> new NotFoundException("Feature not found: " + id));
        mapper.updateEntityFromDto(input, entity);
        if (input.getUnitId() != null) {
            entity.setUnitMeasurements(buildUnitMeasurementAssociation(input.getUnitId()));
        }
        FeatureEntity updated = jpa.save(entity);
        return mapper.toDto(updated);
    }

    @Override
    public boolean deleteById(Long id) {
        if (!jpa.existsById(id)) return false;
        jpa.deleteById(id);
        return true;
    }

    @Override
    public Optional<FeatureDto> findById(Long id) {
        return jpa.findById(id).map(mapper::toDto);
    }

    @Override
    public List<FeatureDto> findAll() {
        return jpa.findAll().stream().map(mapper::toDto).toList();
    }

    private Set<UnitMeasurementEntity> buildUnitMeasurementAssociation(Long unitId) {
        UnitMeasurementEntity unit = unitMeasurementRepository.findById(unitId)
                .orElseThrow(() -> new NotFoundException("Unit measurement not found: " + unitId));
        return new HashSet<>(Collections.singleton(unit));
    }
}
