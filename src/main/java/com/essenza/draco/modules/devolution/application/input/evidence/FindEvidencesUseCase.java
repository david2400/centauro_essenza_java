package com.essenza.draco.modules.devolution.application.input.evidence;

import com.essenza.draco.modules.devolution.application.dto.evidence.EvidenceDto;

import java.util.List;

public interface FindEvidencesUseCase {
    List<EvidenceDto> findAll();

    /** Solo los del padre indicado (evita traer la tabla completa para filtrar en el cliente). */
    List<EvidenceDto> findByOrderDevolutionId(Long orderDevolutionId);
}
