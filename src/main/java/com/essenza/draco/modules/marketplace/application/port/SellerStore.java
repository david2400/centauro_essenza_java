package com.essenza.draco.modules.marketplace.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.SellerEventDto;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.SellerStatsDto;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.DocumentKind;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.DocumentReview;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.Status;
import com.essenza.draco.modules.marketplace.domain.seller.SellerProfile;

/**
 * Persistencia de vendedores, contactos, documentos (metadatos) y auditoría. Toda consulta va
 * filtrada por {@code companyId}: un vendedor de otra empresa "no existe".
 */
public interface SellerStore {

    long insert(long companyId, SellerProfile profile, Status status, String actorId, Instant now);

    void assignCode(long companyId, long sellerId, String code);

    /** @param forUpdate bloquea la fila hasta el fin de la transacción (serializa cambios del vendedor) */
    Optional<SellerRow> find(long companyId, long sellerId, boolean forUpdate);

    /** @return false si la versión no coincide (otro usuario lo cambió) */
    boolean updateProfile(long companyId, long sellerId, int expectedVersion, SellerProfile profile, String actorId,
                          Instant now);

    boolean updateStatus(long companyId, long sellerId, int expectedVersion, Status status, String reason,
                         boolean approving, String actorId, Instant now);

    Page<SellerRow> search(long companyId, SellerFilter filter, Pageable pageable);

    SellerStatsDto stats(long companyId);

    /** Hasta {@code limit} vendedores por nombre, código o documento (buscadores). */
    List<SellerRow> lookup(long companyId, String q, String kind, int limit);

    void addEvent(long companyId, long sellerId, String type, Status from, Status to, String reason, String detail,
                  String actorId, String actorName, Instant now);

    List<SellerEventDto> events(long companyId, long sellerId, int limit);

    long insertDocument(long companyId, long sellerId, NewDocument document, String actorId, Instant now);

    /** Marca como reemplazados los documentos vigentes del mismo tipo, salvo {@code keepId}. */
    void supersedeDocuments(long companyId, long sellerId, DocumentKind kind, long keepId, Instant now);

    Optional<DocumentRow> findDocument(long companyId, long sellerId, long documentId);

    /** @param includeSuperseded también los reemplazados (historial) */
    List<DocumentRow> documents(long companyId, long sellerId, boolean includeSuperseded);

    void reviewDocument(long companyId, long documentId, DocumentReview review, String note, String actorId, Instant now);

    record SellerFilter(String q, String status, String kind) {
    }

    record SellerRow(
            long id,
            SellerProfile profile,
            Status status,
            String statusReason,
            Instant statusChangedAt,
            Instant approvedAt,
            String approvedBy,
            int version,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {
    }

    record NewDocument(DocumentKind kind, String fileName, String contentType, long sizeBytes, String sha256,
                       String storageKey) {
    }

    record DocumentRow(
            long id,
            long sellerId,
            DocumentKind kind,
            String fileName,
            String contentType,
            long sizeBytes,
            String sha256,
            String storageKey,
            DocumentReview review,
            String reviewNote,
            Instant reviewedAt,
            String reviewedBy,
            Instant supersededAt,
            Instant uploadedAt,
            String uploadedBy) {
    }
}
