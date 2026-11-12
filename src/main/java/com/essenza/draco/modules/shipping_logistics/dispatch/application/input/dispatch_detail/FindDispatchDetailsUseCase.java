package com.essenza.draco.modules.shipping_logistics.dispatch.application.input.dispatch_detail;

import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.dispatch_detail.DispatchDetailDto;

import java.util.List;

public interface FindDispatchDetailsUseCase {
    List<DispatchDetailDto> findAll();

    /** Solo los del padre indicado (evita traer la tabla completa para filtrar en el cliente). */
    List<DispatchDetailDto> findByDispatchProductId(Long dispatchProductId);
}
