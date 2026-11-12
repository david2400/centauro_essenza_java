package com.essenza.draco.modules.sales.application.output.repository;

import com.essenza.draco.modules.sales.application.dto.product_order.CreateProductOrderDto;
import com.essenza.draco.modules.sales.application.dto.product_order.ProductOrderDto;
import com.essenza.draco.modules.sales.application.dto.product_order.UpdateProductOrderDto;

import java.util.List;
import java.util.Optional;

public interface ProductOrderRepository {
    ProductOrderDto create(CreateProductOrderDto input);

    ProductOrderDto update(Long id, UpdateProductOrderDto input);

    boolean deleteById(Long id);

    Optional<ProductOrderDto> findById(Long id);

    List<ProductOrderDto> findAll();

    List<ProductOrderDto> findByOrderId(Long orderId);
}
