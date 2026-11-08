package com.essenza.draco.modules.marketplace.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.essenza.draco.modules.marketplace.application.dto.OfferDtos.OfferEventDto;
import com.essenza.draco.modules.marketplace.domain.offer.OfferEnums.Status;
import com.essenza.draco.modules.marketplace.domain.offer.OfferTerms;

/** Ofertas y su auditoría, siempre por empresa. */
public interface OfferStore {

    long insert(long companyId, NewOffer offer, String actorId, Instant now);

    /** Con datos del vendedor (nombre, estado). {@code forUpdate} bloquea la oferta. */
    Optional<OfferRow> find(long companyId, long offerId, boolean forUpdate);

    /** @return false si {@code lockVersion} no coincide */
    boolean updateTerms(long companyId, long offerId, int lockVersion, OfferTerms terms, CatalogCopy catalog,
                        String actorId, Instant now);

    /** @return false si {@code lockVersion} no coincide */
    boolean updateStatus(long companyId, long offerId, int lockVersion, Status status, String reason, boolean publishing,
                         CatalogCopy catalog, String actorId, Instant now);

    Page<OfferRow> search(long companyId, OfferFilter filter, Pageable pageable);

    Map<String, Long> countByStatus(long companyId, Long sellerId);

    void addEvent(long companyId, long offerId, String type, Status from, Status to, String reason, String detail,
                  String actorId, String actorName, Instant now);

    List<OfferEventDto> events(long companyId, long offerId, int limit);

    record OfferFilter(String q, String status, Long sellerId, Long skuId) {
    }

    /** Copia del catálogo guardada para buscar y listar. */
    record CatalogCopy(Long productId, String skuCode, String productName) {
    }

    record NewOffer(long sellerId, long skuId, CatalogCopy catalog, OfferTerms terms) {
    }

    record OfferRow(
            long id,
            long sellerId,
            String sellerName,
            String sellerStatus,
            long skuId,
            CatalogCopy catalog,
            OfferTerms terms,
            Status status,
            String statusReason,
            Instant statusChangedAt,
            Instant publishedAt,
            int lockVersion,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {
    }
}
