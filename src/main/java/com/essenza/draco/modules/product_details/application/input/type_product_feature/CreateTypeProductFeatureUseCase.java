package com.essenza.draco.modules.product_details.application.input.type_product_feature;

import com.essenza.draco.modules.product_details.application.dto.type_product_feature.CreateTypeProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.type_product_feature.TypeProductFeatureDto;

public interface CreateTypeProductFeatureUseCase {
    TypeProductFeatureDto create(CreateTypeProductFeatureDto input);
}
