package com.essenza.draco.modules.product_details.infrastructure.outbound.repositories.product_feature;

import com.essenza.draco.modules.product_details.application.output.repository.ProductFeatureRepository;
import com.essenza.draco.modules.product_details.application.dto.product_feature.CreateProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.product_feature.ProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.product_feature.UpdateProductFeatureDto;
import com.essenza.draco.modules.product_details.infrastructure.outbound.mappers.ProductFeatureMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class ProductFeatureRepositoryAdapter implements ProductFeatureRepository {

    private final JpaProductFeatureRepository jpa;
    private final ProductFeatureMapper mapper;

    public ProductFeatureRepositoryAdapter(JpaProductFeatureRepository jpa, ProductFeatureMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ProductFeatureDto create(CreateProductFeatureDto input) {
        var entity = mapper.toEntity(input);
        var saved = jpa.save(entity);
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public ProductFeatureDto update(Long productId, Long featureId, UpdateProductFeatureDto input) {
//        var pk = new ProductFeatureEntity.PK(productId, featureId);
//        var entity = jpa.findById(pk)
//                .orElseThrow(() -> new IllegalArgumentException("ProductFeature not found: (" + productId + "," + featureId + ")"));
//        mapper.updateEntityFromDto(input, entity);
//        var updated = jpa.save(entity);
//        return mapper.toDto(updated);
        return null;
    }

    @Override
    @Transactional
    public boolean deleteByIds(Long productId, Long featureId) {
//        var pk = new ProductFeatureEntity.PK(productId, featureId);
//        if (!jpa.existsById(pk)) return false;
//        jpa.deleteById(pk);
        return true;
    }

    @Override
    public Optional<ProductFeatureDto> findByIds(Long productId, Long featureId) {
//        var pk = new ProductFeatureEntity.PK(productId, featureId);
//        return jpa.findById(pk).map(mapper::toDto);
        return null;
    }

    @Override
    public List<ProductFeatureDto> findAll() {
        return jpa.findAll().stream().map(mapper::toDto).toList();
    }
}
