package com.essenza.draco.modules.product_details.application.input.type_product;

import com.essenza.draco.modules.product_details.application.dto.type_product.TypeProductDto;
import com.essenza.draco.modules.product_details.application.dto.type_product.UpdateTypeProductDto;

public interface UpdateTypeProductUseCase {
    TypeProductDto update(Long id, UpdateTypeProductDto input);
}
