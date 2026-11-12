package com.essenza.draco.modules.shipping_logistics.dispatch.application.dto.dispatch_product;

import com.essenza.draco.shared.validation.ValidDeliveryDates;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ValidDeliveryDates
public class CreateDispatchProductDto {
    @NotBlank
    private String guideNumber;
    @NotBlank
    private String address;
    @NotNull
    @FutureOrPresent
    private LocalDate estimatedDeliveryDate;
    /** Se llena al entregar; al registrar el despacho todavía no existe. */
    @PastOrPresent
    private LocalDate realDeliveryDate;
    @NotBlank
    private String departmentOrigin;
    @NotBlank
    private String cityOrigin;
    @NotBlank
    private String departmentDestination;
    @NotBlank
    private String cityDestination;
    @NotNull
    @Positive
    private Long orderId;
}
