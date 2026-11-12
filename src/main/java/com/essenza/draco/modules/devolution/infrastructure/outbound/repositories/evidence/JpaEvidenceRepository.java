package com.essenza.draco.modules.devolution.infrastructure.outbound.repositories.evidence;

import com.essenza.draco.modules.devolution.infrastructure.outbound.persistence.mysql.shop.OrderDevolutionEvidenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaEvidenceRepository extends JpaRepository<OrderDevolutionEvidenceEntity, Long> {

    List<OrderDevolutionEvidenceEntity> findByOrderDevolutionIdOrderByIdAsc(Long orderDevolutionId);
}
