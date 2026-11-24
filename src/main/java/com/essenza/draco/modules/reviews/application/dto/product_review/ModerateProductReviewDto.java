package com.essenza.draco.modules.reviews.application.dto.product_review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerateProductReviewDto {

    @NotBlank(message = "Moderation status is required")
    private String moderationStatus;

    @Size(max = 1000, message = "Moderation notes cannot exceed 1000 characters")
    private String moderationNotes;

    @NotNull(message = "Approval flag is required")
    private Boolean isApproved;

    /**
     * Respuesta pública de la tienda. Nulo: no se toca; vacío: se quita. Se muestra en la tienda solo si la reseña está
     * aprobada.
     */
    @Size(max = 1000, message = "Store response cannot exceed 1000 characters")
    private String storeResponse;
}
