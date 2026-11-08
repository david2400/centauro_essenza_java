package com.essenza.draco.modules.product_details.application.dto.product_feature;

import com.essenza.draco.shared.common.domain.dto.AuditInfoDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFeatureDto extends AuditInfoDto {
    private Long productId;
    private Long featureId;
    private Double value;
}
