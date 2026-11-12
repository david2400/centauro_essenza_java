package com.essenza.draco.modules.shipping_logistics.dispatch.application.services;

//import com.essenza.draco.modules.dispatch.application.input.tracking.*;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.input.tracking.*;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.tracking.CreateTrackingDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.tracking.TrackingDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.tracking.UpdateTrackingDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.output.repository.TrackingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class TrackingServiceImpl implements CreateTrackingUseCase,
        UpdateTrackingUseCase,
        DeleteTrackingUseCase,
        FindTrackingByIdUseCase,
        FindTrackingsUseCase {

    private final TrackingRepository repository;

    public TrackingServiceImpl(TrackingRepository repository) {
        this.repository = repository;
    }

    @Override
    public TrackingDto create(CreateTrackingDto input) {
        return repository.create(input);
    }

    @Override
    public TrackingDto update(Long id, UpdateTrackingDto input) {
        return repository.update(id, input);
    }

    @Override
    public boolean deleteById(Long id) {
        return repository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TrackingDto> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrackingDto> findAll() {
        return repository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrackingDto> findByDispatchProductId(Long dispatchProductId) {
        return repository.findByDispatchProductId(dispatchProductId);
    }
}
