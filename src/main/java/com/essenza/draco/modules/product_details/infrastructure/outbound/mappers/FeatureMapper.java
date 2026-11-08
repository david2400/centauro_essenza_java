package com.essenza.draco.modules.product_details.infrastructure.outbound.mappers;

import com.essenza.draco.modules.product_details.application.dto.feature.CreateFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.feature.FeatureDto;
import com.essenza.draco.modules.product_details.application.dto.feature.UpdateFeatureDto;
import com.essenza.draco.modules.product_details.infrastructure.outbound.persistence.mysql.shop.FeatureEntity;
import org.mapstruct.*;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface FeatureMapper {

    @Mapping(target = "unitId", expression = "java(extractUnitId(entity))")
    FeatureDto toDto(FeatureEntity entity);

    List<FeatureDto> toDtoList(List<FeatureEntity> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "unitMeasurements", ignore = true)
    @Mapping(target = "usrCrea", ignore = true)
    @Mapping(target = "usrMod", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    FeatureEntity toEntity(CreateFeatureDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "unitMeasurements", ignore = true)
    @Mapping(target = "usrCrea", ignore = true)
    @Mapping(target = "usrMod", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntityFromDto(UpdateFeatureDto dto, @MappingTarget FeatureEntity entity);

    default Long extractUnitId(FeatureEntity entity) {
        Set<com.essenza.draco.modules.product_details.infrastructure.outbound.persistence.mysql.shop.UnitMeasurementEntity> unitMeasurements = entity.getUnitMeasurements();
        if (unitMeasurements == null || unitMeasurements.isEmpty()) {
            return null;
        }
        return unitMeasurements.iterator().next().getId();
    }
}
