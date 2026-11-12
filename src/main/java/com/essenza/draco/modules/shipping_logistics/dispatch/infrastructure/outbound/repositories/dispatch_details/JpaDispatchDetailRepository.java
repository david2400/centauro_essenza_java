package com.essenza.draco.modules.shipping_logistics.dispatch.infrastructure.outbound.repositories.dispatch_details;

import com.essenza.draco.modules.shipping_logistics.dispatch.infrastructure.outbound.persistence.mysql.shop.DispatchDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaDispatchDetailRepository extends JpaRepository<DispatchDetailEntity, Long> {

    List<DispatchDetailEntity> findByDispatchProductIdOrderByIdAsc(Long dispatchProductId);
}
