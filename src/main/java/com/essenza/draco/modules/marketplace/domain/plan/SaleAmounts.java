package com.essenza.draco.modules.marketplace.domain.plan;

import java.math.BigDecimal;

import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;

/**
 * Importes de una venta (subpedido de un vendedor) en la moneda del plan.
 *
 * @param itemsSubtotal productos antes de descuentos e impuestos
 * @param discount      descuentos sobre productos (no puede superar el subtotal)
 * @param shipping      envío cobrado al comprador
 * @param taxes         impuestos cobrados al comprador
 */
public record SaleAmounts(BigDecimal itemsSubtotal, BigDecimal discount, BigDecimal shipping, BigDecimal taxes) {

    public static SaleAmounts of(String currency, BigDecimal itemsSubtotal, BigDecimal discount, BigDecimal shipping,
                                 BigDecimal taxes) {
        BigDecimal items = Money.amount(itemsSubtotal, currency, "El subtotal de productos");
        BigDecimal disc = Money.amount(discount, currency, "El descuento");
        MarketplaceRuleViolation.check(disc.compareTo(items) <= 0, "El descuento no puede superar el subtotal de productos.");
        return new SaleAmounts(items, disc, Money.amount(shipping, currency, "El envío"),
                Money.amount(taxes, currency, "Los impuestos"));
    }

    /** Lo que paga el comprador por esta venta. */
    public BigDecimal buyerTotal() {
        return itemsSubtotal.subtract(discount).add(shipping).add(taxes);
    }
}
