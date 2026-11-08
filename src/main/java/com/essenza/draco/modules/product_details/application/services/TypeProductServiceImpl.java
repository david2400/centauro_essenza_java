package com.essenza.draco.modules.product_details.application.services;

import com.essenza.draco.modules.product_details.application.input.type_product.*;
import com.essenza.draco.modules.product_details.application.dto.type_product.CreateTypeProductDto;
import com.essenza.draco.modules.product_details.application.dto.type_product.TypeProductDto;
import com.essenza.draco.modules.product_details.application.dto.type_product.UpdateTypeProductDto;
import com.essenza.draco.modules.product_details.application.output.repository.TypeProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Transactional
public class TypeProductServiceImpl implements CreateTypeProductUseCase, UpdateTypeProductUseCase, DeleteTypeProductUseCase, FindTypeProductsUseCase, FindTypeProductByIdUseCase {

    private final TypeProductRepository typeProductRepository;

//    public TypeProductServiceImpl(TypeProductRepository typeProductRepository) {
//        this.typeProductRepository = typeProductRepository;
//    }


    @Override
    public TypeProductDto create(CreateTypeProductDto input) {
        return typeProductRepository.create(input);
    }

    @Override
    public TypeProductDto update(Long id, UpdateTypeProductDto input) {
        // Sin campos por actualizar actualmente
        return typeProductRepository.update(id,input);
    }

    @Override
    public boolean deleteById(Long id) {
//        if (!unitRepository.existsById(id)) return false;
//        unitRepository.deleteById(id);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TypeProductDto> findById(Long id) {
        return typeProductRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TypeProductDto> findAll() {
        return typeProductRepository.findAll();
    }
}
