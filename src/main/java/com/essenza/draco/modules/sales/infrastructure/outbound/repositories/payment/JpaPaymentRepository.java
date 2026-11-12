package com.essenza.draco.modules.sales.infrastructure.outbound.repositories.payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.essenza.draco.modules.sales.infrastructure.outbound.persistence.mysql.shop.PaymentEntity;

public interface JpaPaymentRepository extends JpaRepository<PaymentEntity, Long>, JpaSpecificationExecutor<PaymentEntity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.id = :id")
    Optional<PaymentEntity> lockById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.paymentReference = :reference")
    Optional<PaymentEntity> lockByPaymentReference(@Param("reference") String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.provider = :provider and p.providerReference = :reference order by p.id desc")
    List<PaymentEntity> lockByProvider(@Param("provider") String provider, @Param("reference") String reference);

    List<PaymentEntity> findByOrderIdOrderByIdAsc(Long orderId);

    @Query("select p.id from PaymentEntity p where p.status = 'PENDING' and p.provider is not null"
            + " and p.createdAt between :from and :to order by p.id asc")
    List<Long> findPendingOnlineIds(@Param("from") Instant from, @Param("to") Instant to, Pageable page);

    @Query("select p.paymentTypeId, count(p) from PaymentEntity p group by p.paymentTypeId")
    List<Object[]> countByMethod();
}
