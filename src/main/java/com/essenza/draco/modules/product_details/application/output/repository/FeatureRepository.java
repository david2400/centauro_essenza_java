package com.essenza.draco.modules.product_details.application.output.repository;

import com.essenza.draco.modules.product_details.application.dto.feature.FeatureDto;
import com.essenza.draco.modules.product_details.application.dto.feature.CreateFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.feature.UpdateFeatureDto;

import java.util.List;
import java.util.Optional;

public interface FeatureRepository {

    FeatureDto create(CreateFeatureDto input);

    FeatureDto update(Long id, UpdateFeatureDto input);

    boolean deleteById(Long id);

    Optional<FeatureDto> findById(Long id);

    List<FeatureDto> findAll();
}
