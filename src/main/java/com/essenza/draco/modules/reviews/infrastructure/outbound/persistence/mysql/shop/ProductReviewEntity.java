package com.essenza.draco.modules.reviews.infrastructure.outbound.persistence.mysql.shop;

import com.essenza.draco.shared.common.domain.entity.AuditInfo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product_reviews")
public class ProductReviewEntity extends AuditInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_review")
    private Long id;

    @Column(name = "id_product", nullable = false)
    private Long productId;

    @Column(name = "id_customer", nullable = false)
    private Long customerId;

    @Column(name = "customer_name", nullable = false, length = 255)
    private String customerName;

    @Column(name = "customer_email", nullable = false, length = 255)
    private String customerEmail;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "comment", nullable = false, length = 2000)
    private String comment;

    @Column(name = "is_verified_purchase", nullable = false)
    private Boolean isVerifiedPurchase;

    @Column(name = "is_approved")
    private Boolean isApproved;

    @Column(name = "is_helpful")
    private Boolean isHelpful;

    @Column(name = "helpful_count")
    private Integer helpfulCount;

    @Column(name = "not_helpful_count")
    private Integer notHelpfulCount;

    @Column(name = "moderation_status", length = 50)
    private String moderationStatus;

    @Column(name = "moderation_notes", length = 1000)
    private String moderationNotes;

    @Column(name = "review_date")
    private LocalDateTime reviewDate;

    /** Respuesta pública de la tienda (se muestra bajo la reseña en la tienda). */
    @Column(name = "store_response", length = 1000)
    private String storeResponse;

    @Column(name = "store_response_at")
    private LocalDateTime storeResponseAt;
}
