package com.essenza.draco.modules.product_details.application.dto.feature;

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
public class UpdateFeatureDto extends CreateFeatureDto {
    @NotNull
    @Positive
    private Long id;
}
