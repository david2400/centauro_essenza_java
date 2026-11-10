package com.essenza.draco.modules.billing.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Datos del emisor y parámetros de facturación electrónica (una sola fila).
 * {@code countryId/stateId/cityId} apuntan al catálogo del servicio parametros; {@code city} y {@code department}
 * guardan los nombres para la representación gráfica (la factura no consulta parametros).
 */
public record BillingSettings(String nit, String legalName, String tradeName, String taxRegime, String responsibilities,
                              String address, String city, String department, String email, String phone,
                              BillingEnvironment environment, String provider, String softwareId, String softwarePin,
                              boolean autoInvoice, String invoiceFooter, Long countryId, Long stateId, Long cityId) {

    public static final Set<String> REGIMES = Set.of("RESPONSABLE_IVA", "NO_RESPONSABLE_IVA");

    public BillingSettings {
        nit = nit == null || Nit.digits(nit).isEmpty() ? null : Nit.digits(nit);
        if (nit != null && (nit.length() < 5 || nit.length() > 15)) {
            throw new BillingRuleViolation("El NIT debe tener entre 5 y 15 dígitos (sin dígito de verificación).");
        }
        taxRegime = taxRegime == null || taxRegime.isBlank() ? "RESPONSABLE_IVA" : taxRegime.trim();
        if (!REGIMES.contains(taxRegime)) {
            throw new BillingRuleViolation("Régimen no válido: " + taxRegime + " (RESPONSABLE_IVA o NO_RESPONSABLE_IVA).");
        }
        email = blankToNull(email);
        if (email != null && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new BillingRuleViolation("Correo del emisor no válido: " + email);
        }
        environment = environment == null ? BillingEnvironment.TEST : environment;
        provider = provider == null || provider.isBlank() ? "SIMULATOR" : provider.trim().toUpperCase(java.util.Locale.ROOT);
        legalName = blankToNull(legalName);
        tradeName = blankToNull(tradeName);
        responsibilities = blankToNull(responsibilities);
        address = blankToNull(address);
        city = blankToNull(city);
        department = blankToNull(department);
        phone = blankToNull(phone);
        softwareId = blankToNull(softwareId);
        softwarePin = blankToNull(softwarePin);
        invoiceFooter = blankToNull(invoiceFooter);
        if (stateId != null && countryId == null) {
            throw new BillingRuleViolation("Elige el país del departamento.");
        }
        if (cityId != null && stateId == null) {
            throw new BillingRuleViolation("Elige el departamento de la ciudad.");
        }
    }

    public String dv() {
        return nit == null ? null : Nit.checkDigit(nit);
    }

    /** Lo que falta para poder facturar (claves para el panel). */
    public List<String> missing() {
        List<String> missing = new ArrayList<>();
        if (nit == null) {
            missing.add("nit");
        }
        if (legalName == null) {
            missing.add("legal_name");
        }
        if (address == null) {
            missing.add("address");
        }
        if (city == null) {
            missing.add("city");
        }
        if (email == null) {
            missing.add("email");
        }
        if (environment == BillingEnvironment.PRODUCTION && (softwareId == null || softwarePin == null)) {
            missing.add("software");
        }
        return missing;
    }

    public boolean ready() {
        return missing().isEmpty();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
