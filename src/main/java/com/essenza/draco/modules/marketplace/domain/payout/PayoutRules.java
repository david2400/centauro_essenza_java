package com.essenza.draco.modules.marketplace.domain.payout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;

import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;

/**
 * Pagos a vendedores por lote: lo que la tienda le debe a cada vendedor (saldo negativo de su
 * estado de cuenta) menos lo retenido de órdenes aún no entregadas o entregadas hace menos de los
 * días de retención (para cubrir devoluciones y reclamos).
 */
public final class PayoutRules {

    public enum BatchStatus { OPEN, CLOSED, CANCELLED }

    public enum ItemStatus { PENDING, PAID, REJECTED }

    /**
     * Destino del pago. SAVINGS/CHECKING: cuenta en banco, banco digital o cooperativa. DIGITAL_WALLET:
     * billetera atada al celular (Nequi, Daviplata, Movii, Dale!…). BRE_B_KEY: llave Bre-B (sistema de
     * pagos inmediatos del Banco de la República), que llega a cualquier entidad participante.
     */
    public static final Set<String> ACCOUNT_TYPES = Set.of("SAVINGS", "CHECKING", "DIGITAL_WALLET", "BRE_B_KEY");
    /** Llaves Bre-B: documento, celular, correo, alfanumérica (@…) o código de comercio. */
    public static final Set<String> KEY_TYPES = Set.of("DOCUMENT", "PHONE", "EMAIL", "ALPHANUMERIC", "MERCHANT_CODE");
    /** Tipos de entidad del catálogo. */
    public static final Set<String> BANK_KINDS = Set.of("BANK", "DIGITAL_BANK", "WALLET", "COOPERATIVE", "FINANCIAL");

    /** Tipos de cuenta que admite cada tipo de entidad (la llave Bre-B sirve en todas). */
    public static Set<String> accountTypesFor(String kind) {
        return switch (kind == null ? "" : kind) {
            case "WALLET" -> Set.of("DIGITAL_WALLET", "BRE_B_KEY");
            case "DIGITAL_BANK" -> Set.of("SAVINGS", "BRE_B_KEY");
            default -> Set.of("SAVINGS", "CHECKING", "BRE_B_KEY");
        };
    }

    public static final Set<String> DOC_TYPES = Set.of("CC", "CE", "NIT", "PP", "TI");

    private PayoutRules() {
    }

    /**
     * @param balance saldo del estado de cuenta (positivo = el vendedor debe; negativo = la tienda le debe)
     * @param held    abonado por órdenes aún retenidas
     * @return lo que se le puede pagar ahora (nunca negativo)
     */
    public static BigDecimal payable(BigDecimal balance, BigDecimal held) {
        BigDecimal owed = balance == null || balance.signum() >= 0 ? BigDecimal.ZERO : balance.negate();
        BigDecimal retained = held == null || held.signum() < 0 ? BigDecimal.ZERO : held;
        return owed.subtract(retained).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal owed(BigDecimal balance) {
        return balance == null || balance.signum() >= 0 ? BigDecimal.ZERO.setScale(2) : balance.negate().setScale(2, RoundingMode.HALF_UP);
    }

    /** Monto del pago: vacío = todo lo pagable; si se indica, entre 0,01 y lo pagable, con 2 decimales. */
    public static BigDecimal itemAmount(BigDecimal requested, BigDecimal payable, String seller) {
        if (payable == null || payable.signum() <= 0) {
            throw new MarketplaceRuleViolation("A " + seller + " no se le puede pagar nada todavía (sin saldo a favor o retenido).");
        }
        if (requested == null) {
            return payable;
        }
        if (requested.scale() > 2 && requested.stripTrailingZeros().scale() > 2) {
            throw new MarketplaceRuleViolation("El pago a " + seller + " admite máximo 2 decimales.");
        }
        BigDecimal amount = requested.setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new MarketplaceRuleViolation("El pago a " + seller + " debe ser mayor que cero.");
        }
        if (amount.compareTo(payable) > 0) {
            throw new MarketplaceRuleViolation("A " + seller + " se le pueden pagar como máximo " + payable.toPlainString() + ".");
        }
        return amount;
    }

    /** Entidad del catálogo elegida para la cuenta. */
    public record BankRef(long id, String name, String kind, String achCode, boolean active) {
    }

    /**
     * Cuenta o llave normalizada. {@code bankId}/{@code bankCode} vacíos solo en llaves Bre-B sin
     * entidad (la llave ya identifica el destino); {@code keyType} solo en llaves Bre-B.
     */
    public record BankAccount(Long bankId, String bankCode, String bankName, String accountType, String accountNumber, String keyType,
                              String holderName, String holderDocType, String holderDocNumber) {
    }

    public static BankAccount checkBankAccount(BankRef bank, String accountType, String accountNumber, String keyType, String holderName,
                                               String holderDocType, String holderDocNumber) {
        String type = upper(accountType);
        MarketplaceRuleViolation.check(type != null && ACCOUNT_TYPES.contains(type),
                "Tipo de cuenta no válido: ahorros, corriente, billetera digital o llave Bre-B.");
        boolean breB = "BRE_B_KEY".equals(type);
        if (!breB) {
            MarketplaceRuleViolation.check(bank != null, "Elige el banco, banco digital o billetera.");
        }
        if (bank != null) {
            MarketplaceRuleViolation.check(bank.active(), bank.name() + " no está activo en el catálogo de bancos.");
            MarketplaceRuleViolation.check(accountTypesFor(bank.kind()).contains(type),
                    bank.name() + " no admite ese tipo de cuenta.");
        }
        String number;
        String key = null;
        if (breB) {
            key = upper(keyType);
            MarketplaceRuleViolation.check(key != null && KEY_TYPES.contains(key), "Indica el tipo de llave Bre-B.");
            number = checkKey(key, accountNumber);
        } else if ("DIGITAL_WALLET".equals(type)) {
            number = digits(accountNumber);
            MarketplaceRuleViolation.check(number != null && number.matches("3\\d{9}"),
                    "La billetera se identifica con el celular: 10 dígitos que empiezan por 3.");
        } else {
            number = digits(accountNumber);
            MarketplaceRuleViolation.check(number != null && number.matches("\\d{6,20}"), "El número de cuenta debe tener entre 6 y 20 dígitos.");
        }
        String holder = trim(holderName);
        MarketplaceRuleViolation.check(holder != null && holder.length() <= 160, "Indica el titular de la cuenta.");
        String docType = upper(holderDocType);
        MarketplaceRuleViolation.check(docType != null && DOC_TYPES.contains(docType), "Tipo de documento del titular no válido.");
        String doc = holderDocNumber == null ? null : holderDocNumber.replaceAll("[\\s.]", "");
        MarketplaceRuleViolation.check(doc != null && doc.matches("[0-9A-Za-z-]{4,30}"), "Indica el documento del titular.");
        return new BankAccount(bank == null ? null : bank.id(), bank == null ? null : bank.achCode(), bank == null ? "Bre-B" : bank.name(),
                type, number, key, holder, docType, doc);
    }

    /** Formato de cada llave Bre-B (el Banco de la República no fija longitudes: se ponen topes razonables). */
    static String checkKey(String keyType, String raw) {
        String value = trim(raw);
        MarketplaceRuleViolation.check(value != null, "Indica la llave Bre-B.");
        switch (keyType) {
            case "PHONE" -> {
                String phone = value.replaceAll("[\\s()+.-]", "");
                if (phone != null && phone.length() == 12 && phone.startsWith("57")) {
                    phone = phone.substring(2);
                }
                MarketplaceRuleViolation.check(phone != null && phone.matches("3\\d{9}"), "La llave celular son 10 dígitos que empiezan por 3.");
                return phone;
            }
            case "EMAIL" -> {
                String email = value.toLowerCase(Locale.ROOT);
                MarketplaceRuleViolation.check(email.length() <= 120 && email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),
                        "La llave correo no es válida.");
                return email;
            }
            case "ALPHANUMERIC" -> {
                MarketplaceRuleViolation.check(value.matches("@[A-Za-z0-9]{1,40}"),
                        "La llave alfanumérica empieza por @ seguida de letras o números.");
                return value;
            }
            case "DOCUMENT" -> {
                String doc = value.replaceAll("[\\s.-]", "");
                MarketplaceRuleViolation.check(doc.matches("[0-9A-Za-z]{4,20}"), "La llave documento solo lleva letras y números.");
                return doc.toUpperCase(Locale.ROOT);
            }
            default -> {
                MarketplaceRuleViolation.check(value.matches("[0-9A-Za-z-]{1,40}"), "El código de comercio solo lleva letras, números o guiones.");
                return value;
            }
        }
    }

    /** Datos de una entidad del catálogo (nombre, tipo y código ACH/banco opcional). */
    public static void checkBank(String name, String kind, String achCode) {
        String n = trim(name);
        MarketplaceRuleViolation.check(n != null && n.length() <= 80, "Indica el nombre de la entidad (máximo 80 caracteres).");
        MarketplaceRuleViolation.check(kind != null && BANK_KINDS.contains(kind), "Tipo de entidad no válido.");
        MarketplaceRuleViolation.check(achCode == null || achCode.isBlank() || achCode.trim().matches("[0-9A-Za-z]{1,10}"),
                "El código de banco tiene máximo 10 letras o números.");
    }

    /** Número enmascarado (solo los 4 últimos dígitos) para quien no gestiona pagos. */
    public static String mask(String number) {
        if (number == null || number.length() <= 4) {
            return number;
        }
        int at = number.indexOf('@');
        if (at > 0) { // correo: a••••@dominio
            return number.charAt(0) + "••••" + number.substring(at);
        }
        return "••••" + number.substring(number.length() - 4);
    }

    public static BatchStatus statusOf(int pending, int paid) {
        if (pending > 0) {
            return BatchStatus.OPEN;
        }
        return paid > 0 ? BatchStatus.CLOSED : BatchStatus.CANCELLED;
    }

    public static String number(long id) {
        return String.format("LIQ-%06d", id);
    }

    /** Celda de CSV sin fórmulas (evita inyección al abrirlo en una hoja de cálculo). */
    public static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return v.contains(",") || v.contains("\"") || v.contains("\n") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    private static String upper(String value) {
        return trim(value) == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String digits(String value) {
        return value == null ? null : value.replaceAll("[\\s-]", "");
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
