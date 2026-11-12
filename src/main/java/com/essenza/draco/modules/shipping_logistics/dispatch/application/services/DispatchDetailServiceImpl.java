package com.essenza.draco.modules.shipping_logistics.dispatch.application.services;

//import com.essenza.draco.modules.dispatch.application.input.dispatch_detail.*;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.input.dispatch_detail.*;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.dispatch_detail.CreateDispatchDetailDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.dispatch_detail.DispatchDetailDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.dispatch_detail.UpdateDispatchDetailDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.output.repository.DispatchDetailRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class DispatchDetailServiceImpl implements CreateDispatchDetailUseCase,
        UpdateDispatchDetailUseCase,
        DeleteDispatchDetailUseCase,
        FindDispatchDetailByIdUseCase,
        FindDispatchDetailsUseCase {

    private final DispatchDetailRepository repository;

    public DispatchDetailServiceImpl(DispatchDetailRepository repository) {
        this.repository = repository;
    }

    @Override
    public DispatchDetailDto create(CreateDispatchDetailDto input) {
        return repository.create(input);
    }

    @Override
    public DispatchDetailDto update(Long id, UpdateDispatchDetailDto input) {
        return repository.update(id, input);
    }

    @Override
    public boolean deleteById(Long id) {
        return repository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DispatchDetailDto> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DispatchDetailDto> findAll() {
        return repository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DispatchDetailDto> findByDispatchProductId(Long dispatchProductId) {
        return repository.findByDispatchProductId(dispatchProductId);
    }
}
