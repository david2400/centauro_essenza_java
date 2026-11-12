package com.essenza.draco.modules.sales.application.input.product_order;

import com.essenza.draco.modules.sales.application.dto.product_order.ProductOrderDto;
import java.util.List;

public interface FindProductOrdersUseCase {
    List<ProductOrderDto> findAll();

    /** Solo los del padre indicado (evita traer la tabla completa para filtrar en el cliente). */
    List<ProductOrderDto> findByOrderId(Long orderId);
}
