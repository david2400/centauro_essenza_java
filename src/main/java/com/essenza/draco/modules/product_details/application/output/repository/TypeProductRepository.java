package com.essenza.draco.modules.product_details.application.output.repository;

import com.essenza.draco.modules.product_details.application.dto.type_product.CreateTypeProductDto;
import com.essenza.draco.modules.product_details.application.dto.type_product.TypeProductDto;
import com.essenza.draco.modules.product_details.application.dto.type_product.UpdateTypeProductDto;

import java.util.List;
import java.util.Optional;

public interface TypeProductRepository {
    TypeProductDto create(CreateTypeProductDto input);

    TypeProductDto update(Long id, UpdateTypeProductDto input);

    boolean deleteById(Long id);

    Optional<TypeProductDto> findById(Long id);

    List<TypeProductDto> findAll();
}
