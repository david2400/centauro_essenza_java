package com.essenza.draco.modules.marketplace.domain.plan;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.AccrualEvent;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.CommissionBase;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.FeeBilling;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.Modality;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.RefundPolicy;

/**
 * Condiciones económicas de un plan (o de una asignación con excepciones). Inmutable y
 * validado al construirse con {@link #of}: si existe, es coherente.
 *
 * <p>Reglas por modalidad:
 * <ul>
 *   <li>COMMISSION: sin mensualidad; porcentaje y/o cargo fijo mayor que cero.</li>
 *   <li>SUBSCRIPTION: mensualidad mayor que cero; sin porcentaje ni cargo fijo.</li>
 *   <li>MIXED: mensualidad mayor que cero y porcentaje y/o cargo fijo mayor que cero.</li>
 * </ul>
 */
public record PlanTerms(
        Modality modality,
        String currency,
        BigDecimal monthlyFee,
        BigDecimal commissionPercent,
        BigDecimal fixedFeePerSale,
        CommissionBase commissionBase,
        boolean includeShipping,
        boolean includeTaxes,
        AccrualEvent accrualEvent,
        int returnWindowDays,
        RefundPolicy refundPolicy,
        FeeBilling feeBilling,
        int graceDays,
        BigDecimal lateInterestMonthlyPercent,
        BigDecimal storageFeePerUnitDay) {

    public static final int MAX_RETURN_WINDOW_DAYS = 365;
    public static final int MAX_GRACE_DAYS = 90;
    private static final BigDecimal MAX_LATE_INTEREST = new BigDecimal("10");

    @SuppressWarnings("java:S107")
    public static PlanTerms of(Modality modality, String currencyCode, BigDecimal monthlyFee, BigDecimal commissionPercent,
                               BigDecimal fixedFeePerSale, CommissionBase commissionBase, boolean includeShipping,
                               boolean includeTaxes, AccrualEvent accrualEvent, Integer returnWindowDays,
                               RefundPolicy refundPolicy, FeeBilling feeBilling, Integer graceDays,
                               BigDecimal lateInterestMonthlyPercent) {
        return of(modality, currencyCode, monthlyFee, commissionPercent, fixedFeePerSale, commissionBase, includeShipping,
                includeTaxes, accrualEvent, returnWindowDays, refundPolicy, feeBilling, graceDays, lateInterestMonthlyPercent, null);
    }

    /**
     * @param storageFeePerUnitDay tarifa de almacenamiento de la mercancía en consignación por unidad y
     *                             día (0 = no se cobra); aplica a cualquier modalidad
     */
    @SuppressWarnings("java:S107")
    public static PlanTerms of(Modality modality, String currencyCode, BigDecimal monthlyFee, BigDecimal commissionPercent,
                               BigDecimal fixedFeePerSale, CommissionBase commissionBase, boolean includeShipping,
                               boolean includeTaxes, AccrualEvent accrualEvent, Integer returnWindowDays,
                               RefundPolicy refundPolicy, FeeBilling feeBilling, Integer graceDays,
                               BigDecimal lateInterestMonthlyPercent, BigDecimal storageFeePerUnitDay) {
        MarketplaceRuleViolation.check(modality != null, "La modalidad es obligatoria.");
        String currency = Money.currency(currencyCode);
        BigDecimal fee = Money.amount(monthlyFee, currency, "La mensualidad");
        BigDecimal pct = Money.percent(commissionPercent, "La comisión");
        BigDecimal fixed = Money.amount(fixedFeePerSale, currency, "El cargo fijo por venta");
        boolean hasCommission = pct.signum() > 0 || fixed.signum() > 0;

        switch (modality) {
            case COMMISSION -> {
                MarketplaceRuleViolation.check(fee.signum() == 0, "Un plan por comisión no lleva mensualidad; usa la modalidad mixta.");
                MarketplaceRuleViolation.check(hasCommission, "Un plan por comisión necesita porcentaje o cargo fijo por venta.");
            }
            case SUBSCRIPTION -> {
                MarketplaceRuleViolation.check(fee.signum() > 0, "Un plan por mensualidad necesita una tarifa mensual mayor que cero.");
                MarketplaceRuleViolation.check(!hasCommission, "Un plan por mensualidad no cobra comisión; usa la modalidad mixta.");
            }
            case MIXED -> {
                MarketplaceRuleViolation.check(fee.signum() > 0, "Un plan mixto necesita una tarifa mensual mayor que cero.");
                MarketplaceRuleViolation.check(hasCommission, "Un plan mixto necesita porcentaje o cargo fijo por venta.");
            }
        }

        CommissionBase base = commissionBase == null ? CommissionBase.ITEMS_NET_OF_DISCOUNTS : commissionBase;
        AccrualEvent accrual = accrualEvent == null ? AccrualEvent.ORDER_DELIVERED : accrualEvent;
        int window = returnWindowDays == null ? 0 : returnWindowDays;
        if (accrual == AccrualEvent.RETURN_WINDOW_CLOSED) {
            MarketplaceRuleViolation.check(window >= 1 && window <= MAX_RETURN_WINDOW_DAYS,
                    "La ventana de devolución debe estar entre 1 y " + MAX_RETURN_WINDOW_DAYS + " días.");
        } else {
            MarketplaceRuleViolation.check(window >= 0 && window <= MAX_RETURN_WINDOW_DAYS,
                    "La ventana de devolución debe estar entre 0 y " + MAX_RETURN_WINDOW_DAYS + " días.");
        }
        int grace = graceDays == null ? 0 : graceDays;
        MarketplaceRuleViolation.check(grace >= 0 && grace <= MAX_GRACE_DAYS,
                "El período de gracia debe estar entre 0 y " + MAX_GRACE_DAYS + " días.");
        BigDecimal interest = Money.percent(lateInterestMonthlyPercent, "El interés de mora");
        MarketplaceRuleViolation.check(interest.compareTo(MAX_LATE_INTEREST) <= 0, "El interés de mora mensual no puede superar el 10 %.");
        BigDecimal storage = Money.amount(storageFeePerUnitDay, currency, "La tarifa de almacenamiento");
        MarketplaceRuleViolation.check(storage.signum() >= 0, "La tarifa de almacenamiento no puede ser negativa.");

        if (!modality.chargesCommission()) {
            // Sin comisión estos campos no aplican: se guardan neutros para no confundir.
            return new PlanTerms(modality, currency, fee, pct, fixed, base, false, false, accrual, window,
                    RefundPolicy.NO_REVERSAL, feeBilling == null ? FeeBilling.IN_ADVANCE : feeBilling, grace, interest, storage);
        }
        return new PlanTerms(modality, currency, fee, pct, fixed, base, includeShipping, includeTaxes, accrual, window,
                refundPolicy == null ? RefundPolicy.REVERSE_PROPORTIONAL : refundPolicy,
                feeBilling == null ? FeeBilling.IN_ADVANCE : feeBilling, grace, interest, storage);
    }

    /**
     * Condiciones con una excepción comercial (porcentaje y/o mensualidad distintos). Se vuelve a
     * validar todo: la excepción no puede producir una combinación inválida para la modalidad.
     */
    public PlanTerms withOverrides(BigDecimal overrideCommissionPercent, BigDecimal overrideMonthlyFee) {
        if (overrideCommissionPercent == null && overrideMonthlyFee == null) {
            return this;
        }
        return of(modality, currency, overrideMonthlyFee != null ? overrideMonthlyFee : monthlyFee,
                overrideCommissionPercent != null ? overrideCommissionPercent : commissionPercent, fixedFeePerSale,
                commissionBase, includeShipping, includeTaxes, accrualEvent, returnWindowDays, refundPolicy, feeBilling,
                graceDays, lateInterestMonthlyPercent);
    }

    /** Base de comisión de una venta según el plan. */
    public BigDecimal commissionBaseOf(SaleAmounts sale) {
        BigDecimal base = sale.itemsSubtotal();
        if (commissionBase == CommissionBase.ITEMS_NET_OF_DISCOUNTS) {
            base = base.subtract(sale.discount());
        }
        if (includeShipping) {
            base = base.add(sale.shipping());
        }
        if (includeTaxes) {
            base = base.add(sale.taxes());
        }
        return Money.round(base.max(BigDecimal.ZERO), currency);
    }

    /** Comisión de una venta. Una venta con base cero no paga cargo fijo. */
    public SaleCharge chargeFor(SaleAmounts sale) {
        BigDecimal base = commissionBaseOf(sale);
        if (!modality.chargesCommission() || base.signum() == 0) {
            return SaleCharge.none(currency, base);
        }
        BigDecimal percentPart = Money.percentOf(base, commissionPercent, currency);
        BigDecimal fixedPart = Money.round(fixedFeePerSale, currency);
        return new SaleCharge(currency, base, percentPart, fixedPart, percentPart.add(fixedPart));
    }

    /**
     * Reverso de comisión por una cancelación o devolución.
     *
     * @param original         comisión original de la venta
     * @param refundedBase     parte de la base que se devolvió (misma regla de base que la venta)
     * @param alreadyReversed  lo ya reversado por devoluciones anteriores de la misma venta
     * @return importe a reversar ahora; nunca deja el total reversado por encima de la comisión
     */
    public BigDecimal reversalFor(SaleCharge original, BigDecimal refundedBase, BigDecimal alreadyReversed) {
        BigDecimal zero = Money.zero(currency);
        if (original == null || original.total().signum() == 0 || refundPolicy == RefundPolicy.NO_REVERSAL) {
            return zero;
        }
        BigDecimal refunded = Money.amount(refundedBase, currency, "La base devuelta");
        BigDecimal previous = Money.amount(alreadyReversed, currency, "Lo ya reversado");
        boolean full = refunded.compareTo(original.base()) >= 0;
        BigDecimal ratio = full ? BigDecimal.ONE
                : refunded.divide(original.base(), 10, RoundingMode.HALF_UP);

        BigDecimal reversible = refundPolicy == RefundPolicy.KEEP_FIXED_FEE ? original.percentPart() : original.total();
        BigDecimal target = full ? reversible : Money.round(reversible.multiply(ratio), currency);
        BigDecimal remaining = reversible.subtract(previous).max(BigDecimal.ZERO);
        return Money.round(target.min(remaining), currency);
    }
}
