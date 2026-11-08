package com.essenza.draco.modules.product_details.infrastructure.outbound.mappers;

import com.essenza.draco.modules.product_details.application.dto.type_product.CreateTypeProductDto;
import com.essenza.draco.modules.product_details.application.dto.type_product.TypeProductDto;
import com.essenza.draco.modules.product_details.application.dto.type_product.UpdateTypeProductDto;
import com.essenza.draco.modules.product_details.infrastructure.outbound.persistence.mysql.shop.TypeProductEntity;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TypeProductMapper {
    TypeProductDto toDto(TypeProductEntity entity);
    List<TypeProductDto> toDtoList(List<TypeProductEntity> entities);

//    @Mapping(target = "id", ignore = true)
//    @Mapping(target = "usrCrea", ignore = true)
//    @Mapping(target = "usrMod", ignore = true)
//    @Mapping(target = "createdAt", ignore = true)
//    @Mapping(target = "updatedAt", ignore = true)
    TypeProductEntity toEntity(CreateTypeProductDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
//    @Mapping(target = "usrCrea", ignore = true)
//    @Mapping(target = "usrMod", ignore = true)
//    @Mapping(target = "createdAt", ignore = true)
//    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(UpdateTypeProductDto dto, @MappingTarget TypeProductEntity entity);
}
