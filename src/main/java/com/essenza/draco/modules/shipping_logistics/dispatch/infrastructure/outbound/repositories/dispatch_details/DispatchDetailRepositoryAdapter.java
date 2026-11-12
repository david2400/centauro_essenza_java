package com.essenza.draco.modules.shipping_logistics.dispatch.infrastructure.outbound.repositories.dispatch_details;

import com.essenza.draco.modules.shipping_logistics.dispatch.application.output.repository.DispatchDetailRepository;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.dispatch_detail.CreateDispatchDetailDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.dispatch_detail.DispatchDetailDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.dispatch_detail.UpdateDispatchDetailDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.infrastructure.outbound.mappers.DispatchDetailMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DispatchDetailRepositoryAdapter implements DispatchDetailRepository {

    private final JpaDispatchDetailRepository jpa;
    private final DispatchDetailMapper mapper;

    public DispatchDetailRepositoryAdapter(JpaDispatchDetailRepository jpa, DispatchDetailMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public DispatchDetailDto create(CreateDispatchDetailDto input) {
        var entity = mapper.toEntity(input);
        var saved = jpa.save(entity);
        return mapper.toDto(saved);
    }

    @Override
    public DispatchDetailDto update(Long id, UpdateDispatchDetailDto input) {
        var entity = jpa.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("DispatchDetail not found: " + id));
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
    public Optional<DispatchDetailDto> findById(Long id) {
        return jpa.findById(id).map(mapper::toDto);
    }

    @Override
    public List<DispatchDetailDto> findAll() {
        return jpa.findAll().stream().map(mapper::toDto).toList();
    }

    @Override
    public List<DispatchDetailDto> findByDispatchProductId(Long dispatchProductId) {
        return jpa.findByDispatchProductIdOrderByIdAsc(dispatchProductId).stream().map(mapper::toDto).toList();
    }
}
