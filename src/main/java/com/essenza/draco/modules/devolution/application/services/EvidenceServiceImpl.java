package com.essenza.draco.modules.devolution.application.services;

import com.essenza.draco.modules.devolution.application.input.evidence.*;
import com.essenza.draco.modules.devolution.application.dto.evidence.CreateEvidenceDto;
import com.essenza.draco.modules.devolution.application.dto.evidence.EvidenceDto;
import com.essenza.draco.modules.devolution.application.dto.evidence.UpdateEvidenceDto;
import com.essenza.draco.modules.devolution.application.output.repository.EvidenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

//Evidencias
@Service
@Transactional
public class EvidenceServiceImpl implements CreateEvidenceUseCase,
        UpdateEvidenceUseCase,
        DeleteEvidenceUseCase,
        FindEvidenceByIdUseCase,
        FindEvidencesUseCase {

    private final EvidenceRepository evidenceRepository;

    public EvidenceServiceImpl(EvidenceRepository evidenceRepository) {
        this.evidenceRepository = evidenceRepository;
    }

    @Override
    public EvidenceDto create(CreateEvidenceDto input) {
        return evidenceRepository.create(input);
    }

    @Override
    public EvidenceDto update(Long id, UpdateEvidenceDto input) {
        return evidenceRepository.update(id, input);
    }

    @Override
    public boolean deleteById(Long id) {
        return evidenceRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EvidenceDto> findById(Long id) {
        return evidenceRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceDto> findAll() {
        return evidenceRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceDto> findByOrderDevolutionId(Long orderDevolutionId) {
        return evidenceRepository.findByOrderDevolutionId(orderDevolutionId);
    }
}
