package com.essenza.draco.modules.product_details.application.dto.feature;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.essenza.draco.shared.common.domain.dto.AuditInfoDto;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureDto extends AuditInfoDto {
    private Long id;
    private String name;
    private Long unitId;
}
