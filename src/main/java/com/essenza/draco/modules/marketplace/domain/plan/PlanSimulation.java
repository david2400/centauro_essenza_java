package com.essenza.draco.modules.marketplace.domain.plan;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;

/**
 * Simulación de un mes con un plan: SOLO informativa. No crea cargos, facturas ni cobros.
 * Usa exactamente {@link PlanTerms#chargeFor}, la misma regla que aplicará el cálculo real.
 *
 * @param simulation       siempre true: el resultado no es un cargo
 * @param currency         moneda del plan
 * @param salesCount       ventas del mes simuladas
 * @param perSale          comisión de una venta típica
 * @param grossSales       lo que pagan los compradores en el mes
 * @param commissionTotal  comisión de todas las ventas (redondeo por venta y luego suma)
 * @param monthlyFee       mensualidad del ciclo
 * @param platformTotal    comisión + mensualidad (ingreso de la plataforma)
 * @param sellerNet        ventas − comisión − mensualidad (antes de costos propios del vendedor)
 * @param effectiveRatePercent platformTotal / grossSales × 100 (4 decimales); 0 si no hay ventas
 */
public record PlanSimulation(
        boolean simulation,
        String currency,
        int salesCount,
        SaleCharge perSale,
        BigDecimal grossSales,
        BigDecimal commissionTotal,
        BigDecimal monthlyFee,
        BigDecimal platformTotal,
        BigDecimal sellerNet,
        BigDecimal effectiveRatePercent) {

    public static final int MAX_SALES = 1_000_000;

    public static PlanSimulation of(PlanTerms terms, SaleAmounts typicalSale, int salesCount) {
        MarketplaceRuleViolation.check(salesCount >= 0 && salesCount <= MAX_SALES,
                "Las ventas del mes deben estar entre 0 y " + MAX_SALES + ".");
        String currency = terms.currency();
        SaleCharge perSale = terms.chargeFor(typicalSale);
        BigDecimal count = BigDecimal.valueOf(salesCount);
        BigDecimal gross = Money.round(typicalSale.buyerTotal().multiply(count), currency);
        BigDecimal commission = Money.round(perSale.total().multiply(count), currency);
        BigDecimal fee = terms.modality().chargesMonthlyFee() ? terms.monthlyFee() : Money.zero(currency);
        BigDecimal platform = commission.add(fee);
        BigDecimal rate = gross.signum() == 0 ? BigDecimal.ZERO.setScale(Money.PERCENT_SCALE)
                : platform.multiply(new BigDecimal("100")).divide(gross, Money.PERCENT_SCALE, RoundingMode.HALF_UP);
        return new PlanSimulation(true, currency, salesCount, perSale, gross, commission, fee, platform,
                gross.subtract(platform), rate);
    }
}
