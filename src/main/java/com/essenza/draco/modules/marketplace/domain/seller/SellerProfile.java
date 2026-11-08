package com.essenza.draco.modules.marketplace.domain.seller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.DocType;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.Kind;

/**
 * Ficha comercial del vendedor, ya normalizada y validada. No incluye estado ni datos
 * bancarios: el dinero de cada venta lo recibe el vendedor en su propia cuenta de la pasarela
 * (pendiente), nunca se capturan claves ni contraseñas de vendedores en este formulario.
 */
public record SellerProfile(
        String code,
        Kind kind,
        String legalName,
        String tradeName,
        DocType docType,
        String docNumber,
        String email,
        String phone,
        String website,
        String address,
        String city,
        String description,
        String accountManagerId,
        String accountManagerName,
        List<SellerContact> contacts) {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");
    public static final int MAX_CONTACTS = 20;

    public SellerProfile {
        MarketplaceRuleViolation.check(kind != null, "Indica si el vendedor es propio o externo.");
        code = normalizeCode(code);
        legalName = required(legalName, 200, "La razón social o nombre");
        tradeName = optional(tradeName, 200, "El nombre comercial");
        MarketplaceRuleViolation.check(docType != null, "El tipo de documento es obligatorio.");
        docNumber = normalizeDocNumber(docNumber);
        email = optional(email, 160, "El correo");
        if (email != null) {
            email = email.toLowerCase(Locale.ROOT);
            MarketplaceRuleViolation.check(EMAIL.matcher(email).matches(), "El correo del vendedor no es válido.");
        }
        phone = optional(phone, 40, "El teléfono");
        website = optional(website, 200, "El sitio web");
        address = optional(address, 250, "La dirección");
        city = optional(city, 120, "La ciudad");
        description = optional(description, 2000, "La descripción");
        accountManagerId = optional(accountManagerId, 64, "El responsable");
        accountManagerName = optional(accountManagerName, 160, "El nombre del responsable");
        List<SellerContact> list = contacts == null ? List.of() : List.copyOf(contacts);
        MarketplaceRuleViolation.check(list.size() <= MAX_CONTACTS, "Máximo " + MAX_CONTACTS + " contactos.");
        MarketplaceRuleViolation.check(list.stream().filter(SellerContact::primary).count() <= 1,
                "Solo puede haber un contacto principal.");
        contacts = list;
    }

    /** Vacío = se genera (VEN-0001). Si viene: 2 a 30 caracteres A-Z, 0-9, guion o guion bajo. */
    public static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String value = code.trim().toUpperCase(Locale.ROOT);
        MarketplaceRuleViolation.check(value.matches("[A-Z0-9][A-Z0-9_-]{1,29}"),
                "El código del vendedor debe tener de 2 a 30 caracteres: letras, números, guion o guion bajo.");
        return value;
    }

    /** Sin puntos ni espacios; NIT con o sin dígito de verificación (900123456-7). */
    public static String normalizeDocNumber(String value) {
        String v = value == null ? "" : value.trim().replace(".", "").replace(" ", "").toUpperCase(Locale.ROOT);
        MarketplaceRuleViolation.check(v.matches("[0-9A-Z][0-9A-Z-]{2,29}"),
                "El número de documento debe tener entre 3 y 30 caracteres (sin puntos ni espacios).");
        return v;
    }

    public SellerProfile withCode(String generated) {
        return new SellerProfile(generated, kind, legalName, tradeName, docType, docNumber, email, phone, website,
                address, city, description, accountManagerId, accountManagerName, contacts);
    }

    /** Lo que falta para enviar a revisión (códigos estables para la UI). */
    public List<String> missingForReview() {
        List<String> missing = new ArrayList<>();
        if (email == null) {
            missing.add("EMAIL");
        }
        if (phone == null && contacts.stream().allMatch(c -> c.phone() == null)) {
            missing.add("PHONE");
        }
        if (contacts.stream().noneMatch(SellerContact::primary)) {
            missing.add("PRIMARY_CONTACT");
        }
        return missing;
    }

    static String required(String value, int max, String label) {
        String v = value == null ? "" : value.trim();
        MarketplaceRuleViolation.check(!v.isEmpty(), label + " es obligatorio.");
        MarketplaceRuleViolation.check(v.length() <= max, label + " admite máximo " + max + " caracteres.");
        return v;
    }

    static String optional(String value, int max, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        MarketplaceRuleViolation.check(v.length() <= max, label + " admite máximo " + max + " caracteres.");
        return v;
    }

    /** Contacto del vendedor. */
    public record SellerContact(String name, String role, String email, String phone, boolean primary) {

        public SellerContact {
            name = required(name, 160, "El nombre del contacto");
            role = optional(role, 80, "El cargo del contacto");
            email = optional(email, 160, "El correo del contacto");
            if (email != null) {
                email = email.toLowerCase(Locale.ROOT);
                MarketplaceRuleViolation.check(EMAIL.matcher(email).matches(), "El correo del contacto " + name + " no es válido.");
            }
            phone = optional(phone, 40, "El teléfono del contacto");
            MarketplaceRuleViolation.check(email != null || phone != null,
                    "El contacto " + name + " necesita correo o teléfono.");
        }
    }
}
