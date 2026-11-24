package com.essenza.draco.modules.reviews.application.dto.product_review;

import com.essenza.draco.shared.common.domain.dto.AuditInfoDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReviewDto extends AuditInfoDto {
    private Long id;
    private Long productId;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private Integer rating; // 1-5 stars
    private String title;
    private String comment;
    private Boolean isVerifiedPurchase;
    private Boolean isApproved;
    private Boolean isHelpful;
    private Integer helpfulCount;
    private Integer notHelpfulCount;
    private String moderationStatus; // PENDING, APPROVED, REJECTED
    private String moderationNotes;
    private LocalDateTime reviewDate;
    private String storeResponse;
    private LocalDateTime storeResponseAt;
}
