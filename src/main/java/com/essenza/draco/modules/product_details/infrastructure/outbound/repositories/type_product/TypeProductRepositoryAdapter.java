package com.essenza.draco.modules.product_details.infrastructure.outbound.repositories.type_product;

import com.essenza.draco.modules.product_details.application.output.repository.TypeProductRepository;
import com.essenza.draco.modules.product_details.application.dto.type_product.CreateTypeProductDto;
import com.essenza.draco.modules.product_details.application.dto.type_product.TypeProductDto;
import com.essenza.draco.modules.product_details.application.dto.type_product.UpdateTypeProductDto;
import com.essenza.draco.modules.product_details.infrastructure.outbound.mappers.TypeProductMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class TypeProductRepositoryAdapter implements TypeProductRepository {

    private final JpaTypeProductRepository jpa;
    private final TypeProductMapper mapper;

    public TypeProductRepositoryAdapter(JpaTypeProductRepository jpa, TypeProductMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TypeProductDto create(CreateTypeProductDto input) {
        var entity = mapper.toEntity(input);
        var saved = jpa.save(entity);
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public TypeProductDto update(Long id, UpdateTypeProductDto input) {
        var entity = jpa.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TypeProduct not found: " + id));
        mapper.updateEntityFromDto(input, entity);
        var updated = jpa.save(entity);
        return mapper.toDto(updated);
    }

    @Override
    @Transactional
    public boolean deleteById(Long id) {
        if (!jpa.existsById(id)) return false;
        jpa.deleteById(id);
        return true;
    }

    @Override
    public Optional<TypeProductDto> findById(Long id) {
        return jpa.findById(id).map(mapper::toDto);
    }

    @Override
    public List<TypeProductDto> findAll() {
        return jpa.findAll().stream().map(mapper::toDto).toList();
    }
}
