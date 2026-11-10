package com.essenza.draco.modules.sales.domain.model.storefront;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.essenza.draco.modules.sales.domain.model.order.OrderRuleViolation;

/**
 * Reglas de la tienda pública: datos del comprador invitado, líneas del carrito y el enlace privado con el
 * que sigue su pedido. El enlace es un secreto de 256 bits; en la base solo se guarda su SHA-256.
 */
public final class StorefrontRules {

    public static final int MAX_LINES = 30;
    public static final int MAX_UNITS_PER_LINE = 20;
    public static final Set<String> DOC_TYPES = Set.of("CC", "CE", "NIT", "PP", "TI", "PEP");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]{1,64}@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final Pattern DOC = Pattern.compile("^[A-Za-z0-9-]{3,20}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+ ()-]{7,20}$");
    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private StorefrontRules() {
    }

    /** Línea pedida: SKU, oferta (vacía = venta de la tienda) y unidades. */
    public record CartLine(Long skuId, Long offerId, int quantity) {
    }

    public record Buyer(String name, String docType, String docNumber, String email, String phone) {
    }

    public record Address(String recipientName, String phone, String address, String addressDetail, String city, String state,
                          String notes) {
    }

    /** Une líneas repetidas (mismo SKU y oferta) y valida cantidades y tamaño del carrito. */
    public static List<CartLine> cart(List<CartLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new OrderRuleViolation("El carrito está vacío.");
        }
        Map<String, CartLine> merged = new LinkedHashMap<>();
        for (CartLine line : lines) {
            if (line == null || line.skuId() == null || line.skuId() <= 0) {
                throw new OrderRuleViolation("Hay un producto sin referencia en el carrito.");
            }
            if (line.offerId() != null && line.offerId() <= 0) {
                throw new OrderRuleViolation("Hay una oferta no válida en el carrito.");
            }
            if (line.quantity() <= 0) {
                throw new OrderRuleViolation("Las cantidades deben ser mayores que cero.");
            }
            String key = line.skuId() + ":" + Objects.toString(line.offerId(), "");
            CartLine prev = merged.get(key);
            int qty = (prev == null ? 0 : prev.quantity()) + line.quantity();
            if (qty > MAX_UNITS_PER_LINE) {
                throw new OrderRuleViolation("Máximo " + MAX_UNITS_PER_LINE + " unidades por producto en una compra en línea.");
            }
            merged.put(key, new CartLine(line.skuId(), line.offerId(), qty));
        }
        if (merged.size() > MAX_LINES) {
            throw new OrderRuleViolation("Máximo " + MAX_LINES + " productos distintos por compra.");
        }
        return new ArrayList<>(merged.values());
    }

    public static Buyer buyer(Buyer b) {
        if (b == null) {
            throw new OrderRuleViolation("Faltan tus datos.");
        }
        String name = text(b.name(), 200);
        String docType = b.docType() == null ? null : b.docType().trim().toUpperCase(Locale.ROOT);
        String docNumber = text(b.docNumber(), 20);
        String email = b.email() == null ? null : b.email().trim().toLowerCase(Locale.ROOT);
        String phone = text(b.phone(), 20);
        List<String> missing = new ArrayList<>();
        if (name == null || name.length() < 3) {
            missing.add("nombre");
        }
        if (docType == null || !DOC_TYPES.contains(docType)) {
            missing.add("tipo de documento");
        }
        if (docNumber == null || !DOC.matcher(docNumber).matches()) {
            missing.add("número de documento");
        }
        if (email == null || email.length() > 160 || !EMAIL.matcher(email).matches()) {
            missing.add("correo");
        }
        if (phone == null || !PHONE.matcher(phone).matches()) {
            missing.add("celular");
        }
        if (!missing.isEmpty()) {
            throw new OrderRuleViolation("Revisa: " + String.join(", ", missing) + ".");
        }
        return new Buyer(name, docType, docNumber, email, phone);
    }

    public static Address address(Address a, String fallbackName, String fallbackPhone) {
        if (a == null) {
            throw new OrderRuleViolation("Falta la dirección de entrega.");
        }
        String address = text(a.address(), 200);
        String city = text(a.city(), 120);
        List<String> missing = new ArrayList<>();
        if (address == null || address.length() < 5) {
            missing.add("dirección");
        }
        if (city == null) {
            missing.add("ciudad");
        }
        if (!missing.isEmpty()) {
            throw new OrderRuleViolation("Revisa: " + String.join(", ", missing) + ".");
        }
        String name = text(a.recipientName(), 160);
        String phone = text(a.phone(), 40);
        return new Address(name == null ? fallbackName : name, phone == null ? fallbackPhone : phone, address,
                text(a.addressDetail(), 200), city, text(a.state(), 80), text(a.notes(), 500));
    }

    /** Secreto del enlace de seguimiento (43 caracteres base64url). */
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static boolean wellFormed(String token) {
        return token != null && TOKEN.matcher(token).matches();
    }

    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Lo que se muestra de existencias: el número exacto no se publica. */
    public static int shownAvailable(long available) {
        return (int) Math.max(0, Math.min(available, 10));
    }

    /** Patrón LIKE seguro para la búsqueda (sin comodines del usuario). */
    public static String likePattern(String q) {
        String text = text(q, 80);
        if (text == null) {
            return null;
        }
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    static String text(String value, int max) {
        if (value == null) {
            return null;
        }
        String v = value.strip().replaceAll("\\s+", " ");
        if (v.isEmpty()) {
            return null;
        }
        return v.length() > max ? v.substring(0, max) : v;
    }
}
