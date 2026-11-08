package com.essenza.draco.modules.product_details.application.input.product_feature;

import com.essenza.draco.modules.product_details.application.dto.product_feature.ProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.product_feature.UpdateProductFeatureDto;

public interface UpdateProductFeatureUseCase {
    ProductFeatureDto update(Long productId, Long featureId, UpdateProductFeatureDto input);
}
