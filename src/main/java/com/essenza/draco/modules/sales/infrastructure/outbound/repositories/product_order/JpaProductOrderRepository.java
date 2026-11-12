package com.essenza.draco.modules.sales.infrastructure.outbound.repositories.product_order;

import com.essenza.draco.modules.sales.infrastructure.outbound.persistence.mysql.shop.ProductOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaProductOrderRepository extends JpaRepository<ProductOrderEntity, Long> {

    List<ProductOrderEntity> findByOrderIdIn(List<Long> orderIds);

    List<ProductOrderEntity> findByOrderIdOrderByIdAsc(Long orderId);
}
