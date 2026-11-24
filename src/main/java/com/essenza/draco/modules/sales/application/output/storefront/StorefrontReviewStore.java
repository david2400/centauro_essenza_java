package com.essenza.draco.modules.sales.application.output.storefront;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reseñas para la tienda pública (tabla product_reviews del módulo de reseñas). Solo se muestran las aprobadas en el
 * panel; las nuevas entran pendientes de moderación.
 */
public interface StorefrontReviewStore {

    record Summary(long productId, BigDecimal average, long count) {
    }

    record ReviewRow(long id, String customerName, int rating, String title, String comment, LocalDateTime date,
                     boolean verified, String storeResponse, LocalDateTime storeResponseAt) {

        public ReviewRow(long id, String customerName, int rating, String title, String comment, LocalDateTime date, boolean verified) {
            this(id, customerName, rating, title, comment, date, verified, null, null);
        }
    }

    /** Comprador de una compra (para firmar la reseña); customerId puede no existir (invitado). */
    record Buyer(Long customerId, String name, String email) {
    }

    Map<Long, Summary> summaries(Collection<Long> productIds);

    /** Cantidad de reseñas aprobadas por estrellas (1 a 5). */
    Map<Integer, Long> distribution(long productId);

    List<ReviewRow> approved(long productId, int offset, int limit);

    /** Productos (id → nombre) de una compra, sin duplicados. */
    Map<Long, String> purchasedProducts(long purchaseId);

    Optional<Buyer> buyer(long purchaseId);

    /** Productos que ese correo ya reseñó (aprobadas, pendientes o rechazadas). */
    Set<Long> reviewedBy(String email, Collection<Long> productIds);

    void insert(long productId, Buyer buyer, int rating, String title, String comment, Instant now);

    /** Estado de la reseña de ese correo por producto (PENDING, APPROVED o REJECTED). */
    default Map<Long, String> reviewStatuses(String email, Collection<Long> productIds) {
        Map<Long, String> result = new java.util.HashMap<>();
        reviewedBy(email, productIds).forEach(id -> result.put(id, "PENDING"));
        return result;
    }

    /** Slug de cada producto (para enlazar desde el pedido). */
    default Map<Long, String> slugs(Collection<Long> productIds) {
        return Map.of();
    }
}
