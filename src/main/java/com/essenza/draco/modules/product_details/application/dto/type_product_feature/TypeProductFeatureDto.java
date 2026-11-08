package com.essenza.draco.modules.product_details.application.dto.type_product_feature;

import com.essenza.draco.shared.common.domain.dto.AuditInfoDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TypeProductFeatureDto extends AuditInfoDto {
    private Long typeProductId;
    private Long featureId;
    private String typeProductName;
    private String featureName;
}
