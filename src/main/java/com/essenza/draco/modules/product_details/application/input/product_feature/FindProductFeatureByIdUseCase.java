package com.essenza.draco.modules.product_details.application.input.product_feature;

import com.essenza.draco.modules.product_details.application.dto.product_feature.ProductFeatureDto;

import java.util.Optional;

public interface FindProductFeatureByIdUseCase {
    Optional<ProductFeatureDto> findById(Long productId, Long featureId);
}
