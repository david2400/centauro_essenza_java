package com.essenza.draco.modules.product_details.application.dto.type_product_feature;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTypeProductFeatureDto {
    @NotNull
    @Positive
    private Long typeProductId;
    
    @NotNull
    @Positive
    private Long featureId;
}
