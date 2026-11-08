package com.essenza.draco.modules.product_details.application.input.feature;

import com.essenza.draco.modules.product_details.application.dto.feature.FeatureDto;

import java.util.List;

public interface FindFeaturesUseCase {
    List<FeatureDto> findAll();
}
