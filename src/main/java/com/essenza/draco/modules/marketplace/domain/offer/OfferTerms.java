package com.essenza.draco.modules.marketplace.domain.offer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.domain.offer.OfferEnums.Condition;
import com.essenza.draco.modules.marketplace.domain.offer.OfferEnums.Fulfillment;
import com.essenza.draco.modules.marketplace.domain.plan.Money;

/**
 * Condiciones comerciales de una oferta, validadas.
 *
 * @param currency          moneda ISO del precio
 * @param price             precio al comprador, con el mismo criterio del precio del catálogo (impuesto incluido)
 * @param availableQuantity unidades que el vendedor declara disponibles (despacho del vendedor)
 * @param handlingDays      días hábiles de alistamiento antes del despacho
 * @param condition         nuevo, usado o reacondicionado
 * @param fulfillment       quién despacha: SELLER (su cantidad declarada) o PLATFORM (consignación en bodega propia)
 * @param sellerSku         referencia propia del vendedor (opcional)
 */
public record OfferTerms(String currency, BigDecimal price, int availableQuantity, int handlingDays, Condition condition,
                         Fulfillment fulfillment, String sellerSku) {

    public static final int MAX_QUANTITY = 1_000_000;
    public static final int MAX_HANDLING_DAYS = 60;
    /** Desviación frente al precio del catálogo a partir de la cual se alerta al moderador. */
    public static final BigDecimal DEVIATION_ALERT_PERCENT = new BigDecimal("50");

    public static OfferTerms of(String currencyCode, BigDecimal price, Integer availableQuantity, Integer handlingDays,
                                Condition condition, Fulfillment fulfillment, String sellerSku) {
        String currency = Money.currency(currencyCode);
        BigDecimal amount = Money.amount(price, currency, "El precio");
        MarketplaceRuleViolation.check(amount.signum() > 0, "El precio debe ser mayor que cero.");
        int qty = availableQuantity == null ? 0 : availableQuantity;
        MarketplaceRuleViolation.check(qty >= 0 && qty <= MAX_QUANTITY,
                "La cantidad disponible debe estar entre 0 y " + MAX_QUANTITY + ".");
        int days = handlingDays == null ? 1 : handlingDays;
        MarketplaceRuleViolation.check(days >= 0 && days <= MAX_HANDLING_DAYS,
                "Los días de alistamiento deben estar entre 0 y " + MAX_HANDLING_DAYS + ".");
        Fulfillment ful = fulfillment == null ? Fulfillment.SELLER : fulfillment;
        String ref = sellerSku == null || sellerSku.isBlank() ? null : sellerSku.trim();
        MarketplaceRuleViolation.check(ref == null || ref.matches("[A-Za-z0-9._/-]{1,60}"),
                "La referencia del vendedor admite hasta 60 letras, números, punto, guion o barra.");
        return new OfferTerms(currency, amount, qty, days, condition == null ? Condition.NEW : condition, ful, ref);
    }

    /**
     * Las mismas condiciones con otro disponible. En ofertas con despacho de la plataforma el
     * disponible no se escribe: lo da la mercancía en consignación.
     */
    public OfferTerms withAvailableQuantity(int quantity) {
        MarketplaceRuleViolation.check(quantity >= 0, "La cantidad disponible no puede ser negativa.");
        return new OfferTerms(currency, price, Math.min(quantity, MAX_QUANTITY), handlingDays, condition, fulfillment, sellerSku);
    }

    /** Campos que cambian entre dos versiones (nombres estables para la auditoría). */
    public List<String> changesTo(OfferTerms after) {
        List<String> changed = new ArrayList<>();
        if (!currency.equals(after.currency)) changed.add("currency");
        if (price.compareTo(after.price) != 0) changed.add("price");
        if (availableQuantity != after.availableQuantity) changed.add("available_quantity");
        if (handlingDays != after.handlingDays) changed.add("handling_days");
        if (condition != after.condition) changed.add("condition");
        if (fulfillment != after.fulfillment) changed.add("fulfillment");
        if (!Objects.equals(sellerSku, after.sellerSku)) changed.add("seller_sku");
        return changed;
    }

    /**
     * Diferencia porcentual frente al precio del catálogo (2 decimales). Null si el catálogo no
     * tiene precio o la moneda no es comparable (el catálogo está en la moneda de la tienda).
     */
    public BigDecimal deviationFrom(BigDecimal catalogPrice, String catalogCurrency) {
        if (catalogPrice == null || catalogPrice.signum() <= 0 || !currency.equals(catalogCurrency)) {
            return null;
        }
        return price.subtract(catalogPrice).multiply(new BigDecimal("100")).divide(catalogPrice, 2, RoundingMode.HALF_UP);
    }

    public static boolean deviationAlert(BigDecimal deviationPercent) {
        return deviationPercent != null && deviationPercent.abs().compareTo(DEVIATION_ALERT_PERCENT) > 0;
    }
}
