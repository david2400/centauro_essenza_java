package com.essenza.draco.modules.product_details.application.output.repository;

import com.essenza.draco.modules.product_details.application.dto.product_feature.CreateProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.product_feature.ProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.product_feature.UpdateProductFeatureDto;

import java.util.List;
import java.util.Optional;

public interface ProductFeatureRepository {
    ProductFeatureDto create(CreateProductFeatureDto input);

    ProductFeatureDto update(Long productId, Long featureId, UpdateProductFeatureDto input);

    boolean deleteByIds(Long productId, Long featureId);

    Optional<ProductFeatureDto> findByIds(Long productId, Long featureId);

    List<ProductFeatureDto> findAll();
}
