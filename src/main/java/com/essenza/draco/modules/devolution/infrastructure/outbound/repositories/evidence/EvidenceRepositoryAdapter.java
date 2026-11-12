package com.essenza.draco.modules.devolution.infrastructure.outbound.repositories.evidence;

import com.essenza.draco.modules.devolution.application.output.repository.EvidenceRepository;
import com.essenza.draco.modules.devolution.application.dto.evidence.CreateEvidenceDto;
import com.essenza.draco.modules.devolution.application.dto.evidence.EvidenceDto;
import com.essenza.draco.modules.devolution.application.dto.evidence.UpdateEvidenceDto;
import com.essenza.draco.modules.devolution.infrastructure.outbound.mappers.EvidenceMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class EvidenceRepositoryAdapter implements EvidenceRepository {

    private final JpaEvidenceRepository jpa;
    private final EvidenceMapper mapper;

    public EvidenceRepositoryAdapter(JpaEvidenceRepository jpa, EvidenceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public EvidenceDto create(CreateEvidenceDto input) {
        var entity = mapper.toEntity(input);
        var saved = jpa.save(entity);
        return mapper.toDto(saved);
    }

    @Override
    public EvidenceDto update(Long id, UpdateEvidenceDto input) {
        var entity = jpa.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Evidence not found: " + id));
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
    public Optional<EvidenceDto> findById(Long id) {
        return jpa.findById(id).map(mapper::toDto);
    }

    @Override
    public List<EvidenceDto> findAll() {
        return jpa.findAll().stream().map(mapper::toDto).toList();
    }

    @Override
    public List<EvidenceDto> findByOrderDevolutionId(Long orderDevolutionId) {
        return jpa.findByOrderDevolutionIdOrderByIdAsc(orderDevolutionId).stream().map(mapper::toDto).toList();
    }
}
