package com.essenza.draco.modules.marketplace.domain.payout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reglas para enviar un lote de pagos a la dispersión automática (Wompi Pagos a terceros) y leer
 * su resultado. Montos en centavos enteros; referencia por pago de máximo 40 letras, números o guiones;
 * cuentas en AHORROS/CORRIENTE (las billeteras con celular viajan como cuenta de ahorros de la entidad)
 * o por llave Bre-B.
 */
public final class PayoutDispatchRules {

    /** Resultado final de un pago en el proveedor. */
    public enum Outcome { PAID, FAILED, PENDING }

    static final Set<String> PAID = Set.of("APPROVED");
    static final Set<String> FAILED = Set.of("FAILED", "REJECTED", "CANCELLED");
    static final Set<String> LEGAL_ID_TYPES = Set.of("CC", "CE", "NIT", "PP", "TI");

    private PayoutDispatchRules() {
    }

    public static long cents(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("El valor del pago debe ser mayor que cero.");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }

    /** Referencia del pago: {@code LIQ-000123-45} (lote y pago). */
    public static String reference(String batchNumber, long itemId) {
        String value = (batchNumber == null ? "LIQ" : batchNumber) + "-" + itemId;
        value = value.replaceAll("[^A-Za-z0-9-]", "-");
        return value.length() <= 40 ? value : value.substring(value.length() - 40);
    }

    /** Llave de idempotencia del envío del lote (letras, números y guion; máximo 64). */
    public static String idempotencyKey(String batchNumber, long batchId) {
        String value = ("PAYOUT-" + batchId + "-" + (batchNumber == null ? "" : batchNumber)).replaceAll("[^A-Za-z0-9-]", "-");
        return value.length() <= 64 ? value : value.substring(0, 64);
    }

    public static String accountType(String type) {
        return switch (type == null ? "" : type) {
            case "CHECKING" -> "CORRIENTE";
            case "SAVINGS", "DIGITAL_WALLET" -> "AHORROS";
            default -> throw new IllegalArgumentException("Tipo de cuenta sin equivalente en el proveedor: " + type);
        };
    }

    public static Outcome outcome(String providerStatus) {
        String s = providerStatus == null ? "" : providerStatus.trim().toUpperCase(Locale.ROOT);
        if (PAID.contains(s)) {
            return Outcome.PAID;
        }
        return FAILED.contains(s) ? Outcome.FAILED : Outcome.PENDING;
    }

    /** Qué le falta a un pago para enviarlo (vacío = listo). */
    public static List<String> problems(String accountType, String accountNumber, String providerBankId, String email, String holderName,
                                        String docType) {
        List<String> problems = new ArrayList<>();
        if (email == null || email.isBlank()) {
            problems.add("correo del vendedor");
        }
        if (holderName == null || holderName.isBlank()) {
            problems.add("titular");
        }
        if (accountNumber == null || accountNumber.isBlank()) {
            problems.add("número de cuenta o llave");
        }
        if (!"BRE_B_KEY".equals(accountType)) {
            if (providerBankId == null || providerBankId.isBlank()) {
                problems.add("id de la entidad en el proveedor (catálogo de bancos)");
            }
            if (docType == null || !LEGAL_ID_TYPES.contains(docType)) {
                problems.add("tipo de documento del titular admitido (CC, CE, NIT, PP o TI)");
            }
        }
        return problems;
    }
}
