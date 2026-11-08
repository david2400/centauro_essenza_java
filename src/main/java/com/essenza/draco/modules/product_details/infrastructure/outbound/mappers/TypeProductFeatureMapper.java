package com.essenza.draco.modules.product_details.infrastructure.outbound.mappers;

import com.essenza.draco.modules.product_details.application.dto.type_product_feature.CreateTypeProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.type_product_feature.TypeProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.type_product_feature.UpdateTypeProductFeatureDto;
import com.essenza.draco.modules.product_details.infrastructure.outbound.persistence.mysql.shop.TypeProductFeatureEntity;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TypeProductFeatureMapper {
    @Mapping(target = "typeProductName", source = "typeProduct.name")
    @Mapping(target = "featureName", source = "feature.name")
    TypeProductFeatureDto toDto(TypeProductFeatureEntity entity);
    
    List<TypeProductFeatureDto> toDtoList(List<TypeProductFeatureEntity> entities);

    TypeProductFeatureEntity toEntity(CreateTypeProductFeatureDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(UpdateTypeProductFeatureDto dto, @MappingTarget TypeProductFeatureEntity entity);
}
