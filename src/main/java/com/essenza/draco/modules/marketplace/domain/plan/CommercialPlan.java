package com.essenza.draco.modules.marketplace.domain.plan;

import java.time.LocalDate;
import java.util.Locale;

import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.Status;

/**
 * Una versión de un plan comercial. El par (código, versión) es único por empresa.
 *
 * <ul>
 *   <li>Solo una versión DRAFT se edita. Al publicarla pasa a ACTIVE y queda inmutable.</li>
 *   <li>Publicar una versión deja la anterior ACTIVE en SUPERSEDED: ya no se asigna, pero
 *       los vendedores que la tienen la conservan (no hay migración automática).</li>
 *   <li>Para cambiar condiciones se crea una nueva versión (borrador copiado de la vigente).</li>
 *   <li>La vigencia ({@code validFrom}..{@code validTo}) limita en qué fechas puede empezar
 *       una asignación; no corta asignaciones ya hechas.</li>
 * </ul>
 */
public record CommercialPlan(
        Long id,
        String code,
        int version,
        String name,
        String description,
        Status status,
        PlanTerms terms,
        LocalDate validFrom,
        LocalDate validTo) {

    public static final int MAX_NAME = 120;
    public static final int MAX_DESCRIPTION = 1000;

    public CommercialPlan {
        code = normalizeCode(code);
        MarketplaceRuleViolation.check(version >= 1, "La versión del plan debe ser 1 o mayor.");
        name = name == null ? "" : name.trim();
        MarketplaceRuleViolation.check(!name.isEmpty() && name.length() <= MAX_NAME,
                "El nombre del plan es obligatorio (máximo " + MAX_NAME + " caracteres).");
        description = description == null || description.isBlank() ? null : description.trim();
        MarketplaceRuleViolation.check(description == null || description.length() <= MAX_DESCRIPTION,
                "La descripción admite máximo " + MAX_DESCRIPTION + " caracteres.");
        MarketplaceRuleViolation.check(status != null, "El estado del plan es obligatorio.");
        MarketplaceRuleViolation.check(terms != null, "Las condiciones del plan son obligatorias.");
        MarketplaceRuleViolation.check(validFrom != null, "La fecha desde la que se puede asignar es obligatoria.");
        MarketplaceRuleViolation.check(validTo == null || !validTo.isBefore(validFrom),
                "La vigencia termina antes de empezar.");
    }

    /** Código: 2 a 30 caracteres A-Z, 0-9, guion o guion bajo, en mayúsculas. */
    public static String normalizeCode(String code) {
        String value = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        MarketplaceRuleViolation.check(value.matches("[A-Z0-9][A-Z0-9_-]{1,29}"),
                "El código del plan debe tener de 2 a 30 caracteres: letras, números, guion o guion bajo.");
        return value;
    }

    public static CommercialPlan draft(String code, String name, String description, PlanTerms terms,
                                       LocalDate validFrom, LocalDate validTo) {
        return new CommercialPlan(null, code, 1, name, description, Status.DRAFT, terms, validFrom, validTo);
    }

    public boolean editable() {
        return status == Status.DRAFT;
    }

    /** Reemplaza nombre, descripción, condiciones y vigencia de un borrador. */
    public CommercialPlan edit(String newName, String newDescription, PlanTerms newTerms, LocalDate from, LocalDate to) {
        MarketplaceRuleViolation.check(editable(),
                "Solo se editan borradores. Para cambiar un plan publicado crea una nueva versión.");
        return new CommercialPlan(id, code, version, newName, newDescription, status, newTerms, from, to);
    }

    /** Publica el borrador. La vigencia no puede haber terminado. */
    public CommercialPlan publish(LocalDate today) {
        MarketplaceRuleViolation.check(status == Status.DRAFT, "Solo se publica un borrador.");
        MarketplaceRuleViolation.check(validTo == null || !validTo.isBefore(today),
                "La vigencia del plan ya terminó; ajústala antes de publicar.");
        return withStatus(Status.ACTIVE);
    }

    /** La versión que queda reemplazada al publicar una más nueva del mismo código. */
    public CommercialPlan supersede() {
        MarketplaceRuleViolation.check(status == Status.ACTIVE, "Solo se reemplaza una versión activa.");
        return withStatus(Status.SUPERSEDED);
    }

    public CommercialPlan retire() {
        MarketplaceRuleViolation.check(status == Status.ACTIVE || status == Status.SUPERSEDED,
                "Solo se retira un plan publicado.");
        return withStatus(Status.RETIRED);
    }

    /** Borrador de la siguiente versión, con las mismas condiciones para ajustar. */
    public CommercialPlan nextVersionDraft(int nextVersion, LocalDate today) {
        MarketplaceRuleViolation.check(status != Status.DRAFT, "Este plan ya es un borrador; edítalo directamente.");
        MarketplaceRuleViolation.check(nextVersion > version, "La nueva versión debe ser mayor que la actual.");
        LocalDate from = validFrom.isBefore(today) ? today : validFrom;
        LocalDate to = validTo != null && validTo.isBefore(from) ? null : validTo;
        return new CommercialPlan(null, code, nextVersion, name, description, Status.DRAFT, terms, from, to);
    }

    /** ¿Se puede asignar a un vendedor una vigencia que empieza en {@code startsOn}? */
    public void checkAssignableOn(LocalDate startsOn) {
        MarketplaceRuleViolation.check(status == Status.ACTIVE,
                "Solo se asignan planes publicados y vigentes (el plan " + code + " v" + version + " está " + status + ").");
        MarketplaceRuleViolation.check(!startsOn.isBefore(validFrom),
                "El plan " + code + " v" + version + " se puede asignar desde el " + validFrom + ".");
        MarketplaceRuleViolation.check(validTo == null || !startsOn.isAfter(validTo),
                "El plan " + code + " v" + version + " solo se puede asignar hasta el " + validTo + ".");
    }

    private CommercialPlan withStatus(Status next) {
        return new CommercialPlan(id, code, version, name, description, next, terms, validFrom, validTo);
    }
}
