package com.essenza.draco.modules.product_details.application.services;

import com.essenza.draco.modules.product_details.application.input.product_feature.*;
import com.essenza.draco.modules.product_details.application.dto.product_feature.CreateProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.product_feature.ProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.product_feature.UpdateProductFeatureDto;
import com.essenza.draco.modules.product_details.application.output.repository.ProductFeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Transactional
public class ProductFeatureServiceImpl implements CreateProductFeatureUseCase,
        UpdateProductFeatureUseCase,
        DeleteProductFeatureUseCase,
        FindProductFeatureByIdUseCase,
        FindProductFeaturesUseCase {

    private final ProductFeatureRepository productFeatureRepository;

//    public ProductFeatureServiceImpl(ProductFeatureRepository productFeatureRepository) {
//        this.productFeatureRepository = productFeatureRepository;
//    }


    @Override
    public ProductFeatureDto create(CreateProductFeatureDto input) {
        // UnitEntity por ahora solo tiene id; creamos un registro vacío

        return productFeatureRepository.create(input);
    }

    @Override
    public ProductFeatureDto update(Long productId, Long featureId, UpdateProductFeatureDto input) {
        // Sin campos por actualizar actualmente
        return productFeatureRepository.update(productId,featureId,input);
    }

    @Override
    public boolean deleteById(Long productId, Long featureId) {
//        if (!unitRepository.existsById(id)) return false;
//        unitRepository.deleteById(id);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductFeatureDto> findById(Long productId, Long featureId) {
        return productFeatureRepository.findByIds(productId,featureId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductFeatureDto> findAll() {
        return productFeatureRepository.findAll();
    }
}
