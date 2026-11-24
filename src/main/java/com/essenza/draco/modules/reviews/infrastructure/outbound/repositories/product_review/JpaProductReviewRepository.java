package com.essenza.draco.modules.reviews.infrastructure.outbound.repositories.product_review;

import com.essenza.draco.modules.reviews.infrastructure.outbound.persistence.mysql.shop.ProductReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaProductReviewRepository extends JpaRepository<ProductReviewEntity, Long>,
        JpaSpecificationExecutor<ProductReviewEntity> {

    @Modifying
    @Query("UPDATE ProductReviewEntity r SET r.helpfulCount = r.helpfulCount + :deltaHelpful, " +
            "r.notHelpfulCount = r.notHelpfulCount + :deltaNotHelpful, r.isHelpful = :isHelpful " +
            "WHERE r.id = :id")
    int updateHelpfulVotes(@Param("id") Long id,
                           @Param("deltaHelpful") int deltaHelpful,
                           @Param("deltaNotHelpful") int deltaNotHelpful,
                           @Param("isHelpful") Boolean isHelpful);

    @Modifying
    @Query("UPDATE ProductReviewEntity r SET r.moderationStatus = :status, r.moderationNotes = :notes, " +
            "r.isApproved = :isApproved WHERE r.id = :id")
    int updateModeration(@Param("id") Long id,
                         @Param("status") String status,
                         @Param("notes") String notes,
                         @Param("isApproved") Boolean isApproved);

    @Modifying
    @Query("UPDATE ProductReviewEntity r SET r.storeResponse = :response, r.storeResponseAt = :at WHERE r.id = :id")
    int updateStoreResponse(@Param("id") Long id, @Param("response") String response, @Param("at") java.time.LocalDateTime at);
}
