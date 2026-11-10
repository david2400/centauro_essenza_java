package com.essenza.draco.modules.shipping_logistics.dispatch.application.services;

//import com.essenza.draco.modules.dispatch.application.input.dispatch_product.*;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.input.dispatch_product.*;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.dispatch_product.CreateDispatchProductDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.dispatch_product.DispatchProductDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.dispatch_product.UpdateDispatchProductDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.output.repository.DispatchProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class DispatchProductServiceImpl implements CreateDispatchProductUseCase,
        UpdateDispatchProductUseCase,
        DeleteDispatchProductUseCase,
        FindDispatchProductByIdUseCase,
        FindDispatchProductsUseCase {

    private final DispatchProductRepository repository;

    public DispatchProductServiceImpl(DispatchProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public DispatchProductDto create(CreateDispatchProductDto input) {
        return repository.create(input);
    }

    @Override
    public DispatchProductDto update(Long id, UpdateDispatchProductDto input) {
        return repository.update(id, input);
    }

    @Override
    public boolean deleteById(Long id) {
        if (repository.findById(id).isEmpty()) {
            throw new NotFoundException("No se encontró el despacho " + id + ".");
        }
        return repository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DispatchProductDto> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DispatchProductDto> findAll() {
        return repository.findAll();
    }

}
