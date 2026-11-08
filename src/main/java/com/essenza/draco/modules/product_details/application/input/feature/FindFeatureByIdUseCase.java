package com.essenza.draco.modules.product_details.application.input.feature;

import com.essenza.draco.modules.product_details.application.dto.feature.FeatureDto;

import java.util.Optional;

public interface FindFeatureByIdUseCase {
    Optional<FeatureDto> findById(Long id);
}
