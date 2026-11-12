package com.essenza.draco.modules.shipping_logistics.dispatch.infrastructure.outbound.repositories.tracking;

import com.essenza.draco.modules.shipping_logistics.dispatch.application.output.repository.TrackingRepository;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.tracking.CreateTrackingDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.tracking.TrackingDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.tracking.UpdateTrackingDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.infrastructure.outbound.mappers.TrackingMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class TrackingRepositoryAdapter implements TrackingRepository {

    private final JpaTrackingRepository jpa;
    private final TrackingMapper mapper;

    public TrackingRepositoryAdapter(JpaTrackingRepository jpa, TrackingMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public TrackingDto create(CreateTrackingDto input) {
        var entity = mapper.toEntity(input);
        var saved = jpa.save(entity);
        return mapper.toDto(saved);
    }

    @Override
    public TrackingDto update(Long id, UpdateTrackingDto input) {
        var entity = jpa.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tracking not found: " + id));
        mapper.updateEntityFromDto(input, entity);
        var updated = jpa.save(entity);
        return mapper.toDto(updated);
    }

    @Override
    public boolean deleteById(Long id) {
        if (!jpa.existsById(id)) return false;
        jpa.deleteById(id);
        return true;
    }

    @Override
    public Optional<TrackingDto> findById(Long id) {
        return jpa.findById(id).map(mapper::toDto);
    }

    @Override
    public List<TrackingDto> findAll() {
        return jpa.findAll().stream().map(mapper::toDto).toList();
    }

    @Override
    public List<TrackingDto> findByDispatchProductId(Long dispatchProductId) {
        return jpa.findByDispatchProductIdOrderByIdAsc(dispatchProductId).stream().map(mapper::toDto).toList();
    }
}
