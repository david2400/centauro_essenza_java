package com.essenza.draco.modules.sales.domain.model.purchase;

/** Número visible de la compra: CMP-000123 a partir del id (único porque el id lo es). */
public final class PurchaseNumber {

    private PurchaseNumber() {
    }

    public static String of(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Id de compra no válido: " + id);
        }
        String digits = Long.toString(id);
        return "CMP-" + (digits.length() >= 6 ? digits : "0".repeat(6 - digits.length()) + digits);
    }
}
