package com.essenza.draco.modules.product_details.application.input.type_product_feature;

import com.essenza.draco.modules.product_details.application.dto.type_product_feature.TypeProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.type_product_feature.UpdateTypeProductFeatureDto;

public interface UpdateTypeProductFeatureUseCase {
    TypeProductFeatureDto update(Long typeProductId, UpdateTypeProductFeatureDto input);
}
