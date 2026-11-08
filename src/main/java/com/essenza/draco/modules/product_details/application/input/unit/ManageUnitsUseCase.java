package com.essenza.draco.modules.product_details.application.input.unit;

import java.math.BigDecimal;
import java.util.List;

import com.essenza.draco.modules.product_details.application.dto.unit.SaveUnitDto;
import com.essenza.draco.modules.product_details.application.dto.unit.UnitConversionDto;
import com.essenza.draco.modules.product_details.application.dto.unit.UnitDto;
import com.essenza.draco.shared.common.lookup.LookupRequest;

/** Catálogo de unidades de medida con conversiones. */
public interface ManageUnitsUseCase {

    /** Unidades ordenadas por magnitud y factor; {@code dimension} filtra; inactivas solo si se piden. */
    List<UnitDto> list(String dimension, boolean includeInactive);

    UnitDto get(Long id);

    UnitDto create(SaveUnitDto input);

    UnitDto update(Long id, SaveUnitDto input);

    void delete(Long id);

    /** {@code from}/{@code to}: código o id de la unidad. */
    UnitConversionDto convert(BigDecimal value, String from, String to);

    /** Búsqueda ligera (activas) por código, símbolo o nombre; {@code dimensions} filtra. */
    List<UnitDto> lookup(LookupRequest request, List<String> dimensions);
}
