package com.essenza.draco.modules.reviews.application.services;

import com.essenza.draco.modules.reviews.application.input.product_review.*;
import com.essenza.draco.modules.reviews.application.output.repository.ProductReviewRepository;
import com.essenza.draco.modules.reviews.application.dto.product_review.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProductReviewService implements CreateProductReviewUseCase,
        UpdateProductReviewUseCase,
        DeleteProductReviewUseCase,
        FindProductReviewByIdUseCase,
        FindProductReviewsUseCase,
        FindProductReviewsPageUseCase,
        SearchProductReviewsUseCase,
        VoteProductReviewHelpfulUseCase,
        ModerateProductReviewUseCase {

    private final ProductReviewRepository repository;
    private final ReviewModerationService moderationService;

    public ProductReviewService(ProductReviewRepository repository,
                                ReviewModerationService moderationService) {
        this.repository = repository;
        this.moderationService = moderationService;
    }

    @Override
    public ProductReviewDto create(CreateProductReviewDto input) {
        ProductReviewDto created = repository.create(input);
        applyAutomaticModeration(created.getId(), created);
        return repository.findById(created.getId()).orElse(created);
    }

    @Override
    public ProductReviewDto update(Long id, UpdateProductReviewDto input) {
        ProductReviewDto updated = repository.update(id, input);
        applyAutomaticModeration(id, updated);
        return repository.findById(id).orElse(updated);
    }

    @Override
    public boolean deleteById(Long id) {
        return repository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductReviewDto> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductReviewDto> findAll() {
        return repository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductReviewDto> findPage(Pageable pageable) {
        return repository.findPage(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductReviewDto> search(ProductReviewFiltersDto filters) {
        if (filters == null || isEmptyFilters(filters)) {
            return findAll();
        }
        return repository.findByFilters(filters);
    }

    @Override
    public boolean voteHelpful(Long id, boolean helpful) {
        return repository.updateHelpfulVote(id, helpful);
    }

    @Override
    public ProductReviewDto moderate(Long id, ModerateProductReviewDto input) {
        boolean updated = repository.updateModerationStatus(id,
                input.getModerationStatus(),
                input.getModerationNotes(),
                input.getIsApproved());

        if (!updated) {
            throw new IllegalArgumentException("Product review not found: " + id);
        }
        if (input.getStoreResponse() != null) {
            String response = input.getStoreResponse().strip().replaceAll("[\\p{Cntrl}&&[^\\n]]", "");
            repository.updateStoreResponse(id, response.isEmpty() ? null : response, java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        }

        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product review not found: " + id));
    }

    private void applyAutomaticModeration(Long id, ProductReviewDto review) {
        if (review == null) {
            return;
        }
        ReviewModerationService.ModerationResult result = moderationService.moderateReview(review);
        repository.updateModerationStatus(id, result.getStatus(), result.getModerationNotes(), result.isAutoApproved());
    }

    private boolean isEmptyFilters(ProductReviewFiltersDto filters) {
        return filters.getProductId() == null &&
                filters.getCustomerId() == null &&
                filters.getRating() == null &&
                filters.getOnlyApproved() == null &&
                filters.getOnlyVerifiedPurchases() == null &&
                filters.getStartDate() == null &&
                filters.getEndDate() == null;
    }
}
