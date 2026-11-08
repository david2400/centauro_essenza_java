package com.essenza.draco.modules.product_details.application.services;

import com.essenza.draco.modules.product_details.application.input.type_product_feature.*;
import com.essenza.draco.modules.product_details.application.dto.type_product_feature.CreateTypeProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.type_product_feature.TypeProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.type_product_feature.UpdateTypeProductFeatureDto;
import com.essenza.draco.modules.product_details.application.output.repository.TypeProductFeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Transactional
public class TypeProductFeatureServiceImpl implements
        CreateTypeProductFeatureUseCase,
        UpdateTypeProductFeatureUseCase,
        DeleteTypeProductFeatureUseCase,
        FindTypeProductFeaturesUseCase,
        FindTypeProductFeatureByIdUseCase {

    private final TypeProductFeatureRepository typeProductFeatureRepository;

//    public TypeProductFeatureServiceImpl(TypeProductFeatureRepository typeProductFeatureRepository) {
//        this.typeProductFeatureRepository = typeProductFeatureRepository;
//    }

    @Override
    public TypeProductFeatureDto create(CreateTypeProductFeatureDto input) {
        return typeProductFeatureRepository.create(input);
    }

    @Override
    public TypeProductFeatureDto update(Long typeProductId, UpdateTypeProductFeatureDto input) {
        return typeProductFeatureRepository.update(typeProductId, input);
    }

    @Override
    public boolean deleteByTypeProductId(Long typeProductId) {
        return typeProductFeatureRepository.deleteByTypeProductId(typeProductId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TypeProductFeatureDto> findByTypeProductId(Long typeProductId) {
        return typeProductFeatureRepository.findByTypeProductId(typeProductId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TypeProductFeatureDto> findAll() {
        return typeProductFeatureRepository.findAll();
    }
}
