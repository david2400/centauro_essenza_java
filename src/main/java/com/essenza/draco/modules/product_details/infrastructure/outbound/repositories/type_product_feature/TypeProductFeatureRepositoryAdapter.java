package com.essenza.draco.modules.product_details.infrastructure.outbound.repositories.type_product_feature;

import com.essenza.draco.modules.product_details.application.output.repository.TypeProductFeatureRepository;
import com.essenza.draco.modules.product_details.application.dto.type_product_feature.CreateTypeProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.type_product_feature.TypeProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.type_product_feature.UpdateTypeProductFeatureDto;
import com.essenza.draco.modules.product_details.infrastructure.outbound.mappers.TypeProductFeatureMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class TypeProductFeatureRepositoryAdapter implements TypeProductFeatureRepository {

    private final JpaTypeProductFeatureRepository jpa;
    private final TypeProductFeatureMapper mapper;

    public TypeProductFeatureRepositoryAdapter(JpaTypeProductFeatureRepository jpa, TypeProductFeatureMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TypeProductFeatureDto create(CreateTypeProductFeatureDto input) {
        if (jpa.existsByTypeProductId(input.getTypeProductId())) {
            throw new IllegalArgumentException("Este tipo de producto ya tiene una característica asignada");
        }
        var entity = mapper.toEntity(input);
        var saved = jpa.save(entity);
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public TypeProductFeatureDto update(Long typeProductId, UpdateTypeProductFeatureDto input) {
        var entity = jpa.findById(typeProductId)
                .orElseThrow(() -> new IllegalArgumentException("TypeProductFeature not found: " + typeProductId));
        mapper.updateEntityFromDto(input, entity);
        var updated = jpa.save(entity);
        return mapper.toDto(updated);
    }

    @Override
    @Transactional
    public boolean deleteByTypeProductId(Long typeProductId) {
        if (!jpa.existsById(typeProductId)) return false;
        jpa.deleteById(typeProductId);
        return true;
    }

    @Override
    public Optional<TypeProductFeatureDto> findByTypeProductId(Long typeProductId) {
        return jpa.findById(typeProductId).map(mapper::toDto);
    }

    @Override
    public List<TypeProductFeatureDto> findAll() {
        return jpa.findAll().stream().map(mapper::toDto).toList();
    }

    @Override
    public boolean existsByTypeProductId(Long typeProductId) {
        return jpa.existsById(typeProductId);
    }
}
