package com.essenza.draco.modules.product_details.application.input.product_feature;

import com.essenza.draco.modules.product_details.application.dto.product_feature.ProductFeatureDto;

import java.util.List;

public interface FindProductFeaturesUseCase {
    List<ProductFeatureDto> findAll();
}
