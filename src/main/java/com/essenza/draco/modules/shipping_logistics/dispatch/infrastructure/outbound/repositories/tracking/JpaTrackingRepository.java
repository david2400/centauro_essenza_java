package com.essenza.draco.modules.shipping_logistics.dispatch.infrastructure.outbound.repositories.tracking;

import com.essenza.draco.modules.shipping_logistics.dispatch.infrastructure.outbound.persistence.mysql.shop.TrackingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaTrackingRepository extends JpaRepository<TrackingEntity, Long> {

    List<TrackingEntity> findByDispatchProductIdOrderByIdAsc(Long dispatchProductId);
}
