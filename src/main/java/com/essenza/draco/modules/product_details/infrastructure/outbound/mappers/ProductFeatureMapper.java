package com.essenza.draco.modules.product_details.infrastructure.outbound.mappers;

import com.essenza.draco.modules.product_details.application.dto.product_feature.CreateProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.product_feature.ProductFeatureDto;
import com.essenza.draco.modules.product_details.application.dto.product_feature.UpdateProductFeatureDto;
import com.essenza.draco.modules.product_details.infrastructure.outbound.persistence.mysql.shop.ProductFeatureEntity;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductFeatureMapper {
    ProductFeatureDto toDto(ProductFeatureEntity entity);
    List<ProductFeatureDto> toDtoList(List<ProductFeatureEntity> entities);

//    @Mapping(target = "product", ignore = true)
//    @Mapping(target = "feature", ignore = true)
//    @Mapping(target = "usrCrea", ignore = true)
//    @Mapping(target = "usrMod", ignore = true)
//    @Mapping(target = "createdAt", ignore = true)
//    @Mapping(target = "updatedAt", ignore = true)
    ProductFeatureEntity toEntity(CreateProductFeatureDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
//    @Mapping(target = "product", ignore = true)
//    @Mapping(target = "feature", ignore = true)
//    @Mapping(target = "usrCrea", ignore = true)
//    @Mapping(target = "usrMod", ignore = true)
//    @Mapping(target = "createdAt", ignore = true)
//    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(UpdateProductFeatureDto dto, @MappingTarget ProductFeatureEntity entity);
}
