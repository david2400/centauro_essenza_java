package com.essenza.draco.modules.product_details.application.dto.type_product;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.essenza.draco.shared.common.domain.dto.AuditInfoDto;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TypeProductDto extends AuditInfoDto {
    private Long id;
    private String name;
}
