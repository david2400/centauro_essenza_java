package com.essenza.draco.modules.devolution.application.output.repository;

import com.essenza.draco.modules.devolution.application.dto.evidence.CreateEvidenceDto;
import com.essenza.draco.modules.devolution.application.dto.evidence.EvidenceDto;
import com.essenza.draco.modules.devolution.application.dto.evidence.UpdateEvidenceDto;

import java.util.List;
import java.util.Optional;

public interface EvidenceRepository {

    EvidenceDto create(CreateEvidenceDto input);

    EvidenceDto update(Long id, UpdateEvidenceDto input);

    boolean deleteById(Long id);

    Optional<EvidenceDto> findById(Long id);

    List<EvidenceDto> findAll();

    List<EvidenceDto> findByOrderDevolutionId(Long orderDevolutionId);
}
