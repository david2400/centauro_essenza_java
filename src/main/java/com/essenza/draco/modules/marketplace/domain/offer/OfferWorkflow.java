package com.essenza.draco.modules.marketplace.domain.offer;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.domain.offer.OfferEnums.Action;
import com.essenza.draco.modules.marketplace.domain.offer.OfferEnums.Status;

/**
 * Moderación de ofertas:
 *
 * <pre>
 * DRAFT ──SUBMIT──▶ PENDING_REVIEW ──APPROVE──▶ ACTIVE ──PAUSE──▶ PAUSED
 *   ▲                   │                          ▲                │
 *   │                REJECT                        └────RESUME──────┘
 *   │                   ▼
 *   └──(edita)──── REJECTED ──SUBMIT──▶ PENDING_REVIEW
 *
 * CLOSE desde cualquier estado salvo CLOSED (final; conserva historial).
 * </pre>
 *
 * Edición: en DRAFT y REJECTED todo; en ACTIVE y PAUSED solo precio, cantidad y alistamiento
 * (operación diaria, auditada); en PENDING_REVIEW y CLOSED nada.
 */
public final class OfferWorkflow {

    private static final Map<Action, Set<Status>> FROM = Map.of(
            Action.SUBMIT, EnumSet.of(Status.DRAFT, Status.REJECTED),
            Action.APPROVE, EnumSet.of(Status.PENDING_REVIEW),
            Action.REJECT, EnumSet.of(Status.PENDING_REVIEW),
            Action.PAUSE, EnumSet.of(Status.ACTIVE),
            Action.RESUME, EnumSet.of(Status.PAUSED),
            Action.CLOSE, EnumSet.of(Status.DRAFT, Status.PENDING_REVIEW, Status.ACTIVE, Status.REJECTED, Status.PAUSED));

    private static final Map<Action, Status> TO = Map.of(
            Action.SUBMIT, Status.PENDING_REVIEW,
            Action.APPROVE, Status.ACTIVE,
            Action.REJECT, Status.REJECTED,
            Action.PAUSE, Status.PAUSED,
            Action.RESUME, Status.ACTIVE,
            Action.CLOSE, Status.CLOSED);

    private static final Set<Action> REASON_REQUIRED = EnumSet.of(Action.REJECT, Action.CLOSE);
    private static final Set<Action> NEEDS_SELLABLE = EnumSet.of(Action.SUBMIT, Action.APPROVE, Action.RESUME);
    private static final Set<String> LIVE_EDITABLE = Set.of("price", "available_quantity", "handling_days");

    private OfferWorkflow() {
    }

    /**
     * Situación al momento de la acción.
     *
     * @param sellerApproved vendedor en estado APPROVED
     * @param skuSellable    SKU activo y producto publicado en el catálogo
     * @param actorIsCreator quien actúa creó la oferta
     * @param fourEyes       exigir que modere alguien distinto de quien la creó
     */
    public record Context(boolean sellerApproved, boolean skuSellable, boolean actorIsCreator, boolean fourEyes) {
    }

    public static List<Action> actionsFrom(Status status) {
        List<Action> actions = new ArrayList<>();
        for (Action action : Action.values()) {
            if (FROM.get(action).contains(status)) {
                actions.add(action);
            }
        }
        return actions;
    }

    public static boolean moderation(Action action) {
        return action == Action.APPROVE || action == Action.REJECT;
    }

    public static Status transition(Status current, Action action, String reason, Context context) {
        MarketplaceRuleViolation.check(action != null, "La acción es obligatoria.");
        MarketplaceRuleViolation.check(FROM.get(action).contains(current),
                "No se puede aplicar " + action + " a una oferta en estado " + current + ".");
        String text = reason == null ? "" : reason.trim();
        if (REASON_REQUIRED.contains(action)) {
            MarketplaceRuleViolation.check(text.length() >= 5, "Escribe el motivo (mínimo 5 caracteres).");
        }
        MarketplaceRuleViolation.check(text.length() <= 1000, "El motivo admite máximo 1000 caracteres.");
        if (NEEDS_SELLABLE.contains(action)) {
            MarketplaceRuleViolation.check(context.sellerApproved(), "El vendedor debe estar aprobado.");
            MarketplaceRuleViolation.check(context.skuSellable(),
                    "El SKU no se puede vender (inactivo o producto sin publicar en el catálogo).");
        }
        if (moderation(action) && context.fourEyes()) {
            MarketplaceRuleViolation.check(!context.actorIsCreator(),
                    "Quien creó la oferta no puede moderarla; debe hacerlo otra persona.");
        }
        return TO.get(action);
    }

    /** Valida qué se puede cambiar en el estado actual; devuelve los campos cambiados. */
    public static List<String> checkEdit(Status status, OfferTerms before, OfferTerms after) {
        List<String> changed = before.changesTo(after);
        switch (status) {
            case DRAFT, REJECTED -> {
                return changed;
            }
            case ACTIVE, PAUSED -> {
                List<String> blocked = changed.stream().filter(field -> !LIVE_EDITABLE.contains(field)).toList();
                MarketplaceRuleViolation.check(blocked.isEmpty(), "En una oferta publicada solo se cambian precio, cantidad"
                        + " y alistamiento; para cambiar " + String.join(", ", blocked) + " ciérrala y crea otra.");
                return changed;
            }
            default -> throw new MarketplaceRuleViolation(status == Status.PENDING_REVIEW
                    ? "La oferta está en moderación: espera la decisión."
                    : "Una oferta cerrada no se edita.");
        }
    }

    /** Visible para compradores: publicada, vendedor aprobado y SKU vendible. */
    public static boolean visible(Status status, boolean sellerApproved, boolean skuSellable) {
        return status == Status.ACTIVE && sellerApproved && skuSellable;
    }
}
