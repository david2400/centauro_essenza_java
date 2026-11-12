package com.essenza.draco.modules.shipping_logistics.dispatch.application.input.tracking;

import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.tracking.TrackingDto;

import java.util.List;

public interface FindTrackingsUseCase {
    List<TrackingDto> findAll();

    /** Solo los del padre indicado (evita traer la tabla completa para filtrar en el cliente). */
    List<TrackingDto> findByDispatchProductId(Long dispatchProductId);
}
