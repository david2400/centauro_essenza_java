package com.essenza.draco.modules.product_details.application.dto.product_feature;

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
public class CreateProductFeatureDto {
    @NotNull
    @Positive
    private Long productId;
    @NotNull
    @Positive
    private Long featureId;
    @NotNull
    private Double value;
}
