package com.essenza.draco.modules.marketplace.domain.plan;

import java.math.BigDecimal;

/**
 * Comisión de la plataforma por una venta, ya redondeada a la moneda.
 *
 * @param currency     moneda del plan
 * @param base         base sobre la que se aplicó el porcentaje
 * @param percentPart  base × porcentaje
 * @param fixedPart    cargo fijo por venta
 * @param total        percentPart + fixedPart
 */
public record SaleCharge(String currency, BigDecimal base, BigDecimal percentPart, BigDecimal fixedPart, BigDecimal total) {

    static SaleCharge none(String currency, BigDecimal base) {
        BigDecimal zero = Money.zero(currency);
        return new SaleCharge(currency, base, zero, zero, zero);
    }
}
