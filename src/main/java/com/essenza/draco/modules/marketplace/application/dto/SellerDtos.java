package com.essenza.draco.modules.marketplace.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Contratos del API de vendedores (JSON en snake_case por la configuración global). */
public final class SellerDtos {

    private SellerDtos() {
    }

    public record ContactDto(String name, String role, String email, String phone, boolean primary) {
    }

    /**
     * Alta o edición de la ficha. {@code version} es obligatoria al editar (concurrencia
     * optimista: si otro usuario cambió el vendedor, 409). El estado no se cambia por aquí.
     */
    public record SaveSellerRequest(
            String code,
            String kind,
            String legalName,
            String tradeName,
            String docType,
            String docNumber,
            String email,
            String phone,
            String website,
            String address,
            String city,
            String description,
            String accountManagerId,
            String accountManagerName,
            List<ContactDto> contacts,
            Integer version) {
    }

    /** Cambio de estado: action = SUBMIT | REQUEST_CHANGES | APPROVE | SUSPEND | REACTIVATE | CLOSE. */
    public record SellerTransitionRequest(String action, String reason, Integer version) {
    }

    public record SellerSummaryDto(
            long id,
            String code,
            String kind,
            String legalName,
            String tradeName,
            String docType,
            String docNumber,
            String email,
            String phone,
            String city,
            String status,
            String accountManagerName,
            String currentPlanCode,
            String currentPlanName,
            Integer currentPlanVersion,
            LocalDate scheduledPlanStartsOn,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ReadinessDto(List<String> forSubmit, List<String> forApproval) {
    }

    public record SellerDetailDto(
            long id,
            String code,
            String kind,
            String legalName,
            String tradeName,
            String docType,
            String docNumber,
            String email,
            String phone,
            String website,
            String address,
            String city,
            String description,
            String accountManagerId,
            String accountManagerName,
            List<ContactDto> contacts,
            String status,
            String statusReason,
            Instant statusChangedAt,
            Instant approvedAt,
            String approvedBy,
            int version,
            ReadinessDto readiness,
            /* Acciones que el estado permite Y que el usuario actual tiene permiso de hacer. */
            List<String> allowedActions,
            List<String> requiredDocuments,
            List<DocumentDto> documents,
            List<PlanDtos.AssignmentDto> assignments,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {
    }

    public record SellerEventDto(
            long id,
            String type,
            String fromStatus,
            String toStatus,
            String reason,
            String detail,
            String actorId,
            String actorName,
            Instant createdAt) {
    }

    public record SellerStatsDto(
            long total,
            long draft,
            long inReview,
            long changesRequested,
            long approved,
            long suspended,
            long closed,
            long external,
            long own) {
    }

    public record DocumentDto(
            long id,
            String kind,
            String fileName,
            String contentType,
            long sizeBytes,
            String reviewStatus,
            String reviewNote,
            Instant reviewedAt,
            String reviewedBy,
            boolean superseded,
            Instant uploadedAt,
            String uploadedBy) {
    }

    /** status = ACCEPTED | REJECTED; nota obligatoria al rechazar. */
    public record ReviewDocumentRequest(String status, String note) {
    }

    /** Archivo para descargar por el endpoint autorizado. */
    public record DocumentFile(String fileName, String contentType, long sizeBytes, java.io.InputStream content) {
    }
}
