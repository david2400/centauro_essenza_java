package com.essenza.draco.modules.reviews.infrastructure.outbound.repositories.product_review;

import com.essenza.draco.modules.reviews.application.output.repository.ProductReviewRepository;
import com.essenza.draco.modules.reviews.application.dto.product_review.CreateProductReviewDto;
import com.essenza.draco.modules.reviews.application.dto.product_review.ProductReviewDto;
import com.essenza.draco.modules.reviews.application.dto.product_review.ProductReviewFiltersDto;
import com.essenza.draco.modules.reviews.application.dto.product_review.UpdateProductReviewDto;
import com.essenza.draco.modules.reviews.infrastructure.outbound.mappers.ProductReviewMapper;
import com.essenza.draco.modules.reviews.infrastructure.outbound.persistence.mysql.shop.ProductReviewEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProductReviewRepositoryAdapter implements ProductReviewRepository {

    private final JpaProductReviewRepository jpa;
    private final ProductReviewMapper mapper;

    public ProductReviewRepositoryAdapter(JpaProductReviewRepository jpa, ProductReviewMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public ProductReviewDto create(CreateProductReviewDto input) {
        ProductReviewEntity entity = mapper.toEntity(input);
        ProductReviewEntity saved = jpa.save(entity);
        return mapper.toDto(saved);
    }

    @Override
    public ProductReviewDto update(Long id, UpdateProductReviewDto input) {
        ProductReviewEntity entity = jpa.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product review not found: " + id));
        mapper.updateEntityFromDto(input, entity);
        ProductReviewEntity updated = jpa.save(entity);
        return mapper.toDto(updated);
    }

    @Override
    public boolean deleteById(Long id) {
        if (!jpa.existsById(id)) {
            return false;
        }
        jpa.deleteById(id);
        return true;
    }

    @Override
    public Optional<ProductReviewDto> findById(Long id) {
        return jpa.findById(id).map(mapper::toDto);
    }

    @Override
    public List<ProductReviewDto> findAll() {
        return mapper.toDtoList(jpa.findAll());
    }

    @Override
    public List<ProductReviewDto> findByFilters(ProductReviewFiltersDto filters) {
        Specification<ProductReviewEntity> spec = ProductReviewSpecification.withFilters(filters);
        return mapper.toDtoList(jpa.findAll(spec));
    }

    @Override
    public Page<ProductReviewDto> findPage(Pageable pageable) {
        return jpa.findAll(pageable).map(mapper::toDto);
    }

    @Override
    public boolean updateHelpfulVote(Long id, boolean helpful) {
        int deltaHelpful = helpful ? 1 : 0;
        int deltaNotHelpful = helpful ? 0 : 1;
        Boolean isHelpful = helpful ? Boolean.TRUE : Boolean.FALSE;
        return jpa.updateHelpfulVotes(id, deltaHelpful, deltaNotHelpful, isHelpful) > 0;
    }

    @Override
    public boolean updateModerationStatus(Long id, String status, String notes, Boolean isApproved) {
        return jpa.updateModeration(id, status, notes, isApproved) > 0;
    }

    @Override
    public boolean updateStoreResponse(Long id, String response, java.time.LocalDateTime at) {
        return jpa.updateStoreResponse(id, response, response == null ? null : at) > 0;
    }
}
