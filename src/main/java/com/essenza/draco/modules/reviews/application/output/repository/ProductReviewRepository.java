package com.essenza.draco.modules.reviews.application.output.repository;

import com.essenza.draco.modules.reviews.application.dto.product_review.CreateProductReviewDto;
import com.essenza.draco.modules.reviews.application.dto.product_review.ProductReviewDto;
import com.essenza.draco.modules.reviews.application.dto.product_review.ProductReviewFiltersDto;
import com.essenza.draco.modules.reviews.application.dto.product_review.UpdateProductReviewDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductReviewRepository {

    ProductReviewDto create(CreateProductReviewDto input);

    ProductReviewDto update(Long id, UpdateProductReviewDto input);

    boolean deleteById(Long id);

    Optional<ProductReviewDto> findById(Long id);

    List<ProductReviewDto> findAll();

    List<ProductReviewDto> findByFilters(ProductReviewFiltersDto filters);

    Page<ProductReviewDto> findPage(Pageable pageable);

    boolean updateHelpfulVote(Long id, boolean helpful);

    boolean updateModerationStatus(Long id, String status, String notes, Boolean isApproved);

    /** Respuesta pública de la tienda; {@code response} nulo la quita. */
    boolean updateStoreResponse(Long id, String response, java.time.LocalDateTime at);
}
