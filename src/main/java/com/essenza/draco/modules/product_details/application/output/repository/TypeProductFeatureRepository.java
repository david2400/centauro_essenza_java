package com.essenza.draco.modules.product_details.application.output.repository;

import com.essenza.draco.modules.product_details.application.dto.type_product_feature.CreateTypeProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.type_product_feature.TypeProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.type_product_feature.UpdateTypeProductFeatureDto;

import java.util.List;
import java.util.Optional;

public interface TypeProductFeatureRepository {
    TypeProductFeatureDto create(CreateTypeProductFeatureDto input);
    TypeProductFeatureDto update(Long typeProductId, UpdateTypeProductFeatureDto input);
    boolean deleteByTypeProductId(Long typeProductId);
    Optional<TypeProductFeatureDto> findByTypeProductId(Long typeProductId);
    List<TypeProductFeatureDto> findAll();
    boolean existsByTypeProductId(Long typeProductId);
}
