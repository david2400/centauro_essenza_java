package com.essenza.draco.modules.sales.infrastructure.outbound.repositories.product_order;
//import com.tuempresa.tuapp.api.mappers.sales.PaymentTypeMapper;
//import com.tuempresa.tuapp.domain.models.sales.PaymentType;
//import com.tuempresa.tuapp.domain.repositories.sales.PaymentTypeRepository;
//import com.tuempresa.tuapp.infrastructure.persistence.entities.sales.PaymentTypeEntity;

import com.essenza.draco.modules.sales.application.output.repository.ProductOrderRepository;
import com.essenza.draco.modules.sales.application.dto.product_order.CreateProductOrderDto;
import com.essenza.draco.modules.sales.application.dto.product_order.ProductOrderDto;
import com.essenza.draco.modules.sales.application.dto.product_order.UpdateProductOrderDto;
import com.essenza.draco.modules.sales.infrastructure.outbound.mappers.ProductOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProductOrderRepositoryAdapter implements ProductOrderRepository {

    private final JpaProductOrderRepository jpa;
    private final ProductOrderMapper mapper;

    public ProductOrderRepositoryAdapter(JpaProductOrderRepository jpa, ProductOrderMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }


    @Override
    public ProductOrderDto create(CreateProductOrderDto input) {
        var entity = mapper.toEntity(input);
        var saved = jpa.save(entity);
        return mapper.toDto(saved);
    }

    @Override
    public ProductOrderDto update(Long id, UpdateProductOrderDto input) {
        var entity = mapper.toEntity(input);
        var saved = jpa.save(entity);
        return mapper.toDto(saved);
    }

    @Override
    public boolean deleteById(Long id) {
        if (!jpa.existsById(id)) return false;
        jpa.deleteById(id);
        return true;
    }

    @Override
    public Optional<ProductOrderDto> findById(Long id) {
        return jpa.findById(id).map(mapper::toDto);
    }

    @Override
    public List<ProductOrderDto> findAll() {
        return jpa.findAll().stream().map(mapper::toDto).toList();
    }

    @Override
    public List<ProductOrderDto> findByOrderId(Long orderId) {
        return jpa.findByOrderIdOrderByIdAsc(orderId).stream().map(mapper::toDto).toList();
    }
}
