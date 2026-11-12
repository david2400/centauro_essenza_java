package com.essenza.draco.modules.shipping_logistics.dispatch.application.output.repository;

import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.dispatch_detail.DispatchDetailDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.dispatch_detail.CreateDispatchDetailDto;
import com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.dispatch_detail.UpdateDispatchDetailDto;

import java.util.List;
import java.util.Optional;

public interface DispatchDetailRepository {

    DispatchDetailDto create(CreateDispatchDetailDto input);

    DispatchDetailDto update(Long id, UpdateDispatchDetailDto input);

    boolean deleteById(Long id);

    Optional<DispatchDetailDto> findById(Long id);

    List<DispatchDetailDto> findAll();

    List<DispatchDetailDto> findByDispatchProductId(Long dispatchProductId);
}
