package com.essenza.draco.modules.shipping_logistics.dispatch.application.output.repository;

import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.tracking.TrackingDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.tracking.CreateTrackingDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.tracking.UpdateTrackingDto;

import java.util.List;
import java.util.Optional;

public interface TrackingRepository {

    TrackingDto create(CreateTrackingDto input);

    TrackingDto update(Long id, UpdateTrackingDto input);

    boolean deleteById(Long id);

    Optional<TrackingDto> findById(Long id);

    List<TrackingDto> findAll();

    List<TrackingDto> findByDispatchProductId(Long dispatchProductId);
}
