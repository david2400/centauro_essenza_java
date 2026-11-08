package com.essenza.draco.modules.sales.domain.model.customer;

import java.time.Instant;

import com.essenza.draco.modules.sales.domain.model.order.CustomerInfo;
import com.essenza.draco.modules.sales.domain.model.order.OrderRuleViolation;

/**
 * Cliente persistente de la tienda. Su identidad es el documento (tipo + número) dentro de la
 * empresa; las órdenes guardan su propia foto del cliente y se relacionan por ese documento.
 * Reutiliza las validaciones de {@link CustomerInfo} (las mismas de la orden).
 *
 * @param storeUserId      usuario de la tienda (access_control), si tiene cuenta
 * @param marketingConsent autorizó comunicaciones comerciales (habeas data); {@code consentAt} cuándo
 */
public record CustomerProfile(CustomerInfo info, Long storeUserId, boolean marketingConsent, Instant consentAt,
                              String notes) {

    public static final int MAX_NOTES = 2000;

    public static CustomerProfile of(String kind, String docType, String docNumber, String name, String email, String phone,
                                     String billingAddress, String billingCity, Long storeUserId, Boolean marketingConsent,
                                     Instant previousConsentAt, boolean previousConsent, String notes, Instant now) {
        CustomerInfo info = CustomerInfo.of(storeUserId, kind, docType, docNumber, name, email, phone, billingAddress,
                billingCity);
        if (info.docNumber() == null) {
            throw new OrderRuleViolation("El documento del cliente es obligatorio: es su identificación en la tienda.");
        }
        if (info.name() == null) {
            throw new OrderRuleViolation("Indica el nombre del cliente.");
        }
        String text = notes == null || notes.isBlank() ? null : notes.strip();
        if (text != null && text.length() > MAX_NOTES) {
            throw new OrderRuleViolation("Las notas admiten máximo " + MAX_NOTES + " caracteres.");
        }
        boolean consent = Boolean.TRUE.equals(marketingConsent);
        // La fecha del consentimiento se conserva mientras siga vigente; se fija al otorgarlo.
        Instant consentAt = !consent ? null : previousConsent && previousConsentAt != null ? previousConsentAt : now;
        return new CustomerProfile(info, storeUserId, consent, consentAt, text);
    }

    /** Llave de búsqueda del cliente en las órdenes. */
    public String docKey() {
        return info.docType() + ":" + info.docNumber();
    }
}
