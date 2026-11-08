package com.essenza.draco.modules.marketplace.application.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.marketplace.application.MarketplacePermissions;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.DocumentDto;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.DocumentFile;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.ReviewDocumentRequest;
import com.essenza.draco.modules.marketplace.application.port.DocumentStorage;
import com.essenza.draco.modules.marketplace.application.port.SellerStore;
import com.essenza.draco.modules.marketplace.application.port.SellerStore.DocumentRow;
import com.essenza.draco.modules.marketplace.application.port.SellerStore.NewDocument;
import com.essenza.draco.modules.marketplace.application.port.SellerStore.SellerRow;
import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.DocumentKind;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.DocumentReview;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.Status;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Documentos de soporte del vendedor. Solo PDF, PNG y JPEG (se verifica el contenido, no la
 * extensión), máximo 10 MB. El archivo va a un almacenamiento privado; la descarga exige
 * permiso, es de la misma empresa y queda auditada.
 */
@Service
public class SellerDocumentService {

    public static final long MAX_BYTES = 10L * 1024 * 1024;

    private final SellerStore sellers;
    private final DocumentStorage storage;
    private final ActorProvider actors;
    private final Clock clock;

    public SellerDocumentService(SellerStore sellers, DocumentStorage storage, ActorProvider actors, Clock marketplaceClock) {
        this.sellers = sellers;
        this.storage = storage;
        this.actors = actors;
        this.clock = marketplaceClock;
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> list(long sellerId, boolean includeSuperseded) {
        RequestActor actor = actors.current().require(MarketplacePermissions.SELLERS_VIEW);
        seller(actor, sellerId, false);
        return sellers.documents(actor.companyId(), sellerId, includeSuperseded).stream().map(SellerDocumentService::toDto).toList();
    }

    @Transactional
    public DocumentDto upload(long sellerId, String kindValue, String originalName, byte[] content) {
        RequestActor actor = actors.current().require(MarketplacePermissions.SELLERS_MANAGE);
        DocumentKind kind = PlanEnums.parse(DocumentKind.class, kindValue, "El tipo de documento");
        SellerRow seller = seller(actor, sellerId, true);
        MarketplaceRuleViolation.check(seller.status() != Status.CLOSED, "Un vendedor cerrado no recibe documentos.");
        MarketplaceRuleViolation.check(seller.status() != Status.IN_REVIEW,
                "El vendedor está en revisión: los documentos se cambian después de la decisión o al pedir correcciones.");
        MarketplaceRuleViolation.check(content != null && content.length > 0, "El archivo está vacío.");
        MarketplaceRuleViolation.check(content.length <= MAX_BYTES, "El archivo supera 10 MB.");
        String contentType = detectType(content);
        MarketplaceRuleViolation.check(contentType != null, "Solo se aceptan archivos PDF, PNG o JPG.");
        String fileName = safeName(originalName, contentType);

        String key = storage.put(actor.companyId(), content);
        Instant now = clock.instant();
        long id = sellers.insertDocument(actor.companyId(), sellerId,
                new NewDocument(kind, fileName, contentType, content.length, sha256(content), key), actor.userId(), now);
        if (kind != DocumentKind.OTRO) {
            sellers.supersedeDocuments(actor.companyId(), sellerId, kind, id, now);
        }
        sellers.addEvent(actor.companyId(), sellerId, "DOCUMENT_UPLOADED", null, null, null, kind + ": " + fileName,
                actor.userId(), actor.displayName(), now);
        return sellers.findDocument(actor.companyId(), sellerId, id).map(SellerDocumentService::toDto).orElseThrow();
    }

    @Transactional
    public DocumentDto review(long sellerId, long documentId, ReviewDocumentRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.SELLERS_APPROVE);
        SellerRow seller = seller(actor, sellerId, true);
        MarketplaceRuleViolation.check(seller.status() != Status.CLOSED, "Un vendedor cerrado no se revisa.");
        DocumentRow doc = document(actor, sellerId, documentId);
        MarketplaceRuleViolation.check(doc.supersededAt() == null, "Este documento fue reemplazado; revisa el más reciente.");
        DocumentReview review = PlanEnums.parse(DocumentReview.class, request.status(), "La revisión");
        MarketplaceRuleViolation.check(review != DocumentReview.PENDING, "La revisión debe ser ACCEPTED o REJECTED.");
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        MarketplaceRuleViolation.check(review != DocumentReview.REJECTED || (note != null && note.length() >= 5),
                "Explica por qué se rechaza (mínimo 5 caracteres).");
        MarketplaceRuleViolation.check(note == null || note.length() <= 1000, "La nota admite máximo 1000 caracteres.");
        Instant now = clock.instant();
        sellers.reviewDocument(actor.companyId(), documentId, review, note, actor.userId(), now);
        sellers.addEvent(actor.companyId(), sellerId, "DOCUMENT_" + review.name(), null, null, note,
                doc.kind() + ": " + doc.fileName(), actor.userId(), actor.displayName(), now);
        return sellers.findDocument(actor.companyId(), sellerId, documentId).map(SellerDocumentService::toDto).orElseThrow();
    }

    /** Abre el archivo para descargar. Se audita quién lo vio. */
    @Transactional
    public DocumentFile download(long sellerId, long documentId) {
        RequestActor actor = actors.current().require(MarketplacePermissions.SELLERS_VIEW);
        seller(actor, sellerId, false);
        DocumentRow doc = document(actor, sellerId, documentId);
        sellers.addEvent(actor.companyId(), sellerId, "DOCUMENT_DOWNLOADED", null, null, null,
                doc.kind() + ": " + doc.fileName(), actor.userId(), actor.displayName(), clock.instant());
        return new DocumentFile(doc.fileName(), doc.contentType(), doc.sizeBytes(),
                storage.open(actor.companyId(), doc.storageKey()));
    }

    private SellerRow seller(RequestActor actor, long sellerId, boolean forUpdate) {
        return sellers.find(actor.companyId(), sellerId, forUpdate)
                .orElseThrow(() -> new NotFoundException("Vendedor " + sellerId + " no encontrado."));
    }

    private DocumentRow document(RequestActor actor, long sellerId, long documentId) {
        return sellers.findDocument(actor.companyId(), sellerId, documentId)
                .orElseThrow(() -> new NotFoundException("Documento " + documentId + " no encontrado."));
    }

    /** Tipo real por las primeras bytes ("magic numbers"). */
    static String detectType(byte[] b) {
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
            return "application/pdf";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        return null;
    }

    /** Nombre seguro para mostrar y descargar: sin rutas ni caracteres de control, con extensión real. */
    static String safeName(String original, String contentType) {
        String base = original == null ? "" : original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[\\p{Cntrl}\"<>|:*?]", "").trim();
        int dot = base.lastIndexOf('.');
        String stem = dot >= 0 ? base.substring(0, dot) : base;
        if (stem.isBlank()) {
            stem = "documento";
        }
        if (stem.length() > 150) {
            stem = stem.substring(0, 150);
        }
        String ext = switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            default -> ".jpg";
        };
        return stem + ext;
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static DocumentDto toDto(DocumentRow d) {
        return new DocumentDto(d.id(), d.kind().name(), d.fileName(), d.contentType(), d.sizeBytes(), d.review().name(),
                d.reviewNote(), d.reviewedAt(), d.reviewedBy(), d.supersededAt() != null, d.uploadedAt(), d.uploadedBy());
    }
}
