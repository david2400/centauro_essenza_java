package com.essenza.draco.modules.marketplace.domain.seller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.Action;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.DocType;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.DocumentKind;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.DocumentReview;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.Kind;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.Status;

/**
 * Flujo de estados del vendedor:
 *
 * <pre>
 * DRAFT ──SUBMIT──▶ IN_REVIEW ──APPROVE──▶ APPROVED ──SUSPEND──▶ SUSPENDED
 *   ▲                 │                      ▲                       │
 *   │          REQUEST_CHANGES               └──────REACTIVATE───────┘
 *   │                 ▼
 *   └── (edita) CHANGES_REQUESTED ──SUBMIT──▶ IN_REVIEW
 *
 * CLOSE desde cualquier estado salvo CLOSED. CLOSED es final y conserva todo el historial.
 * </pre>
 *
 * Motivo obligatorio en REQUEST_CHANGES, SUSPEND, REACTIVATE y CLOSE.
 */
public final class SellerWorkflow {

    public static final int MAX_REASON = 1000;

    private static final Map<Action, Set<Status>> FROM = Map.of(
            Action.SUBMIT, EnumSet.of(Status.DRAFT, Status.CHANGES_REQUESTED),
            Action.REQUEST_CHANGES, EnumSet.of(Status.IN_REVIEW),
            Action.APPROVE, EnumSet.of(Status.IN_REVIEW),
            Action.SUSPEND, EnumSet.of(Status.APPROVED),
            Action.REACTIVATE, EnumSet.of(Status.SUSPENDED),
            Action.CLOSE, EnumSet.of(Status.DRAFT, Status.IN_REVIEW, Status.CHANGES_REQUESTED, Status.APPROVED,
                    Status.SUSPENDED));

    private static final Map<Action, Status> TO = Map.of(
            Action.SUBMIT, Status.IN_REVIEW,
            Action.REQUEST_CHANGES, Status.CHANGES_REQUESTED,
            Action.APPROVE, Status.APPROVED,
            Action.SUSPEND, Status.SUSPENDED,
            Action.REACTIVATE, Status.APPROVED,
            Action.CLOSE, Status.CLOSED);

    private static final Set<Action> REASON_REQUIRED = EnumSet.of(Action.REQUEST_CHANGES, Action.SUSPEND,
            Action.REACTIVATE, Action.CLOSE);

    private SellerWorkflow() {
    }

    /** Acciones posibles desde un estado (para pintar solo botones válidos; el backend vuelve a validar). */
    public static List<Action> actionsFrom(Status status) {
        List<Action> actions = new ArrayList<>();
        for (Action action : Action.values()) {
            if (FROM.get(action).contains(status)) {
                actions.add(action);
            }
        }
        return actions;
    }

    public static boolean reasonRequired(Action action) {
        return REASON_REQUIRED.contains(action);
    }

    /**
     * Valida la transición y devuelve el nuevo estado.
     *
     * @param readiness lo que falta (documentos, plan, datos); solo se exige vacío en SUBMIT y APPROVE
     */
    public static Status transition(Status current, Action action, String reason, Readiness readiness) {
        MarketplaceRuleViolation.check(action != null, "La acción es obligatoria.");
        if (!FROM.get(action).contains(current)) {
            throw new MarketplaceRuleViolation("No se puede " + verb(action) + " un vendedor en estado " + current + ".");
        }
        String text = reason == null ? "" : reason.trim();
        if (REASON_REQUIRED.contains(action)) {
            MarketplaceRuleViolation.check(text.length() >= 5, "Escribe el motivo (mínimo 5 caracteres).");
        }
        MarketplaceRuleViolation.check(text.length() <= MAX_REASON, "El motivo admite máximo " + MAX_REASON + " caracteres.");
        if (action == Action.SUBMIT) {
            MarketplaceRuleViolation.check(readiness.forSubmit().isEmpty(),
                    "Falta información para enviar a revisión: " + String.join(", ", readiness.forSubmit()) + ".");
        }
        if (action == Action.APPROVE) {
            MarketplaceRuleViolation.check(readiness.forApproval().isEmpty(),
                    "Falta para aprobar: " + String.join(", ", readiness.forApproval()) + ".");
        }
        return TO.get(action);
    }

    /** Documentos que exige la revisión según tipo de vendedor y documento. */
    public static Set<DocumentKind> requiredDocuments(Kind kind, DocType docType) {
        if (kind == Kind.OWN) {
            return EnumSet.noneOf(DocumentKind.class);
        }
        return docType == DocType.NIT
                ? EnumSet.of(DocumentKind.RUT, DocumentKind.CAMARA_COMERCIO)
                : EnumSet.of(DocumentKind.RUT, DocumentKind.CEDULA_REPRESENTANTE);
    }

    /**
     * Evalúa lo que falta. Códigos estables para la UI: EMAIL, PHONE, PRIMARY_CONTACT,
     * DOC_&lt;KIND&gt; (falta cargarlo), DOC_&lt;KIND&gt;_REVIEW (cargado pero no aceptado),
     * DOC_REJECTED_PENDING (hay rechazados sin reemplazo) y PLAN (externo sin plan asignado).
     *
     * @param documents documentos vigentes (no reemplazados) con su revisión
     * @param hasPlan   tiene una asignación de plan vigente o programada
     */
    public static Readiness readiness(SellerProfile profile, Collection<DocumentState> documents, boolean hasPlan) {
        List<String> submit = new ArrayList<>(profile.missingForReview());
        List<String> approval = new ArrayList<>(submit);
        for (DocumentKind required : requiredDocuments(profile.kind(), profile.docType())) {
            boolean uploaded = documents.stream().anyMatch(d -> d.kind() == required && d.review() != DocumentReview.REJECTED);
            boolean accepted = documents.stream().anyMatch(d -> d.kind() == required && d.review() == DocumentReview.ACCEPTED);
            if (!uploaded) {
                submit.add("DOC_" + required);
                approval.add("DOC_" + required);
            } else if (!accepted) {
                approval.add("DOC_" + required + "_REVIEW");
            }
        }
        if (documents.stream().anyMatch(d -> d.review() == DocumentReview.PENDING) && !approval.contains("DOCS_PENDING")) {
            approval.add("DOCS_PENDING");
        }
        if (profile.kind() == Kind.EXTERNAL && !hasPlan) {
            approval.add("PLAN");
        }
        return new Readiness(List.copyOf(submit), List.copyOf(approval));
    }

    private static String verb(Action action) {
        return switch (action) {
            case SUBMIT -> "enviar a revisión";
            case REQUEST_CHANGES -> "pedir correcciones a";
            case APPROVE -> "aprobar";
            case SUSPEND -> "suspender";
            case REACTIVATE -> "reactivar";
            case CLOSE -> "cerrar";
        };
    }

    /** Estado de revisión de un documento vigente. */
    public record DocumentState(DocumentKind kind, DocumentReview review) {
    }

    /** Lo que falta para enviar a revisión y para aprobar. */
    public record Readiness(List<String> forSubmit, List<String> forApproval) {

        public static Readiness ready() {
            return new Readiness(List.of(), List.of());
        }
    }
}
