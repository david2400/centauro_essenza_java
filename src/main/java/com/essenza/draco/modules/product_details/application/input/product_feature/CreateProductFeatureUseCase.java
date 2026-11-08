package com.essenza.draco.modules.product_details.application.input.product_feature;

import com.essenza.draco.modules.product_details.application.dto.product_feature.CreateProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.product_feature.ProductFeatureDto;

public interface CreateProductFeatureUseCase {
    ProductFeatureDto create(CreateProductFeatureDto input);
}
