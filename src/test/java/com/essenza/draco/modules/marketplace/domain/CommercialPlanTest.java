package com.essenza.draco.modules.marketplace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.marketplace.domain.plan.CommercialPlan;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.AccrualEvent;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.CommissionBase;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.FeeBilling;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.Modality;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.RefundPolicy;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.Status;
import com.essenza.draco.modules.marketplace.domain.plan.PlanSimulation;
import com.essenza.draco.modules.marketplace.domain.plan.PlanTerms;
import com.essenza.draco.modules.marketplace.domain.plan.SaleAmounts;
import com.essenza.draco.modules.marketplace.domain.plan.SaleCharge;

class CommercialPlanTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static PlanTerms terms(Modality modality, String fee, String pct, String fixed) {
        return PlanTerms.of(modality, "COP", fee == null ? null : d(fee), pct == null ? null : d(pct),
                fixed == null ? null : d(fixed), CommissionBase.ITEMS_NET_OF_DISCOUNTS, false, false,
                AccrualEvent.ORDER_DELIVERED, null, RefundPolicy.REVERSE_PROPORTIONAL, FeeBilling.IN_ADVANCE, 5, d("1.5"));
    }

    private static SaleAmounts sale(String items, String discount, String shipping, String taxes) {
        return SaleAmounts.of("COP", d(items), d(discount), d(shipping), d(taxes));
    }

    @Nested
    class Modalities {

        @Test
        void commissionPlanRejectsMonthlyFeeAndNeedsSomeCommission() {
            assertThatThrownBy(() -> terms(Modality.COMMISSION, "50000", "10", null))
                    .isInstanceOf(MarketplaceRuleViolation.class).hasMessageContaining("mixta");
            assertThatThrownBy(() -> terms(Modality.COMMISSION, null, "0", "0"))
                    .hasMessageContaining("porcentaje o cargo fijo");
            assertThat(terms(Modality.COMMISSION, null, null, "1500").fixedFeePerSale()).isEqualByComparingTo("1500");
        }

        @Test
        void subscriptionPlanNeedsFeeAndNoCommission() {
            assertThatThrownBy(() -> terms(Modality.SUBSCRIPTION, "0", null, null)).hasMessageContaining("mayor que cero");
            assertThatThrownBy(() -> terms(Modality.SUBSCRIPTION, "90000", "5", null)).hasMessageContaining("no cobra comisión");
            PlanTerms t = terms(Modality.SUBSCRIPTION, "90000", null, null);
            assertThat(t.refundPolicy()).isEqualTo(RefundPolicy.NO_REVERSAL);
            assertThat(t.chargeFor(sale("100000", "0", "0", "0")).total()).isEqualByComparingTo("0");
        }

        @Test
        void mixedPlanNeedsBoth() {
            assertThatThrownBy(() -> terms(Modality.MIXED, null, "5", null)).hasMessageContaining("tarifa mensual");
            assertThatThrownBy(() -> terms(Modality.MIXED, "50000", null, null)).hasMessageContaining("porcentaje o cargo fijo");
            assertThat(terms(Modality.MIXED, "50000", "5", null).modality()).isEqualTo(Modality.MIXED);
        }

        @Test
        void validatesRangesAndCurrency() {
            assertThatThrownBy(() -> terms(Modality.COMMISSION, null, "100.5", null)).hasMessageContaining("entre 0 y 100");
            assertThatThrownBy(() -> terms(Modality.COMMISSION, null, "1.23456", null)).hasMessageContaining("4 decimales");
            assertThatThrownBy(() -> terms(Modality.COMMISSION, null, "-1", null)).hasMessageContaining("entre 0 y 100");
            assertThatThrownBy(() -> terms(Modality.COMMISSION, null, null, "100.555")).hasMessageContaining("2 decimales");
            assertThatThrownBy(() -> PlanTerms.of(Modality.COMMISSION, "XYZ", null, d("5"), null, null, false, false, null,
                    null, null, null, null, null)).hasMessageContaining("ISO 4217");
            assertThatThrownBy(() -> PlanTerms.of(Modality.COMMISSION, "cop", null, d("5"), null, null, false, false,
                    AccrualEvent.RETURN_WINDOW_CLOSED, 0, null, null, null, null)).hasMessageContaining("ventana de devolución");
            assertThatThrownBy(() -> PlanTerms.of(Modality.COMMISSION, "COP", null, d("5"), null, null, false, false, null,
                    null, null, null, 120, null)).hasMessageContaining("gracia");
            assertThatThrownBy(() -> PlanTerms.of(Modality.COMMISSION, "COP", null, d("5"), null, null, false, false, null,
                    null, null, null, null, d("12"))).hasMessageContaining("10 %");
            assertThat(PlanTerms.of(Modality.COMMISSION, "usd", null, d("5"), null, null, false, false, null, null, null,
                    null, null, null).currency()).isEqualTo("USD");
        }
    }

    @Nested
    class Charges {

        @Test
        void netBaseExcludesDiscountShippingAndTaxesByDefault() {
            PlanTerms t = terms(Modality.COMMISSION, null, "12.5", "800");
            SaleCharge charge = t.chargeFor(sale("200000", "20000", "12000", "34200"));
            assertThat(charge.base()).isEqualByComparingTo("180000");
            assertThat(charge.percentPart()).isEqualByComparingTo("22500.00");
            assertThat(charge.fixedPart()).isEqualByComparingTo("800");
            assertThat(charge.total()).isEqualByComparingTo("23300.00");
        }

        @Test
        void grossBaseWithShippingAndTaxes() {
            PlanTerms t = PlanTerms.of(Modality.COMMISSION, "COP", null, d("10"), null, CommissionBase.ITEMS_GROSS,
                    true, true, null, null, null, null, null, null);
            assertThat(t.chargeFor(sale("200000", "20000", "12000", "34200")).base()).isEqualByComparingTo("246200");
        }

        @Test
        void roundsHalfUpPerSale() {
            PlanTerms t = terms(Modality.COMMISSION, null, "2.5", null);
            // 333.33 × 2.5 % = 8.33325 → 8.33 ; 333.35 × 2.5 % = 8.33375 → 8.33 ; 333.4 → 8.335 → 8.34
            assertThat(t.chargeFor(sale("333.33", "0", "0", "0")).total()).isEqualByComparingTo("8.33");
            assertThat(t.chargeFor(sale("333.40", "0", "0", "0")).total()).isEqualByComparingTo("8.34");
        }

        @Test
        void zeroBaseDoesNotPayFixedFee() {
            PlanTerms t = terms(Modality.COMMISSION, null, "10", "1500");
            assertThat(t.chargeFor(sale("50000", "50000", "9000", "0")).total()).isEqualByComparingTo("0");
        }

        @Test
        void discountCannotExceedItems() {
            assertThatThrownBy(() -> sale("1000", "1001", "0", "0")).hasMessageContaining("no puede superar");
        }

        @Test
        void overridesAreRevalidated() {
            PlanTerms base = terms(Modality.MIXED, "50000", "8", null);
            PlanTerms special = base.withOverrides(d("6.5"), null);
            assertThat(special.commissionPercent()).isEqualByComparingTo("6.5");
            assertThat(special.monthlyFee()).isEqualByComparingTo("50000");
            assertThatThrownBy(() -> base.withOverrides(null, d("0"))).hasMessageContaining("tarifa mensual");
            assertThat(base.withOverrides(null, null)).isSameAs(base);
        }
    }

    @Nested
    class Reversals {

        private final SaleAmounts original = sale("100000", "0", "0", "0");

        @Test
        void proportionalReversalIncludesFixedFeeAndNeverExceedsTheCharge() {
            PlanTerms t = terms(Modality.COMMISSION, null, "10", "1000");
            SaleCharge charge = t.chargeFor(original); // 10000 + 1000
            assertThat(t.reversalFor(charge, d("40000"), d("0"))).isEqualByComparingTo("4400.00");
            assertThat(t.reversalFor(charge, d("100000"), d("4400"))).isEqualByComparingTo("6600.00");
            assertThat(t.reversalFor(charge, d("100000"), d("11000"))).isEqualByComparingTo("0");
        }

        @Test
        void keepFixedFeeOnlyReversesPercent() {
            PlanTerms t = PlanTerms.of(Modality.COMMISSION, "COP", null, d("10"), d("1000"), null, false, false, null, null,
                    RefundPolicy.KEEP_FIXED_FEE, null, null, null);
            SaleCharge charge = t.chargeFor(original);
            assertThat(t.reversalFor(charge, d("100000"), d("0"))).isEqualByComparingTo("10000.00");
        }

        @Test
        void noReversalPolicy() {
            PlanTerms t = PlanTerms.of(Modality.COMMISSION, "COP", null, d("10"), null, null, false, false, null, null,
                    RefundPolicy.NO_REVERSAL, null, null, null);
            assertThat(t.reversalFor(t.chargeFor(original), d("100000"), d("0"))).isEqualByComparingTo("0");
        }
    }

    @Nested
    class Simulation {

        @Test
        void mixedMonthIsLabelledAsSimulationAndRoundsPerSale() {
            PlanTerms t = terms(Modality.MIXED, "49900", "7.5", "300");
            PlanSimulation sim = PlanSimulation.of(t, sale("33333.33", "0", "8000", "0"), 120);
            assertThat(sim.simulation()).isTrue();
            assertThat(sim.perSale().total()).isEqualByComparingTo("2800.00"); // 2500.00 + 300
            assertThat(sim.commissionTotal()).isEqualByComparingTo("336000.00");
            assertThat(sim.grossSales()).isEqualByComparingTo("4959999.60");
            assertThat(sim.platformTotal()).isEqualByComparingTo("385900.00");
            assertThat(sim.sellerNet()).isEqualByComparingTo("4574099.60");
            assertThat(sim.effectiveRatePercent()).isEqualByComparingTo("7.7802");
        }

        @Test
        void noSalesStillPaysTheFee() {
            PlanSimulation sim = PlanSimulation.of(terms(Modality.SUBSCRIPTION, "90000", null, null),
                    sale("0", "0", "0", "0"), 0);
            assertThat(sim.platformTotal()).isEqualByComparingTo("90000");
            assertThat(sim.effectiveRatePercent()).isEqualByComparingTo("0");
            assertThatThrownBy(() -> PlanSimulation.of(terms(Modality.SUBSCRIPTION, "90000", null, null),
                    sale("0", "0", "0", "0"), -1)).hasMessageContaining("entre 0");
        }
    }

    @Nested
    class Lifecycle {

        private final LocalDate today = LocalDate.of(2026, 11, 5);

        private CommercialPlan draft() {
            return CommercialPlan.draft("básico-1".replace("á", "a"), "Plan básico", null,
                    terms(Modality.COMMISSION, null, "10", null), today, null);
        }

        @Test
        void codeIsNormalizedAndValidated() {
            assertThat(draft().code()).isEqualTo("BASICO-1");
            assertThatThrownBy(() -> CommercialPlan.draft("a", "x", null, terms(Modality.COMMISSION, null, "10", null), today, null))
                    .hasMessageContaining("2 a 30");
        }

        @Test
        void onlyDraftsAreEditedAndPublishedVersionsAreImmutable() {
            CommercialPlan active = draft().publish(today);
            assertThat(active.status()).isEqualTo(Status.ACTIVE);
            assertThatThrownBy(() -> active.edit("Otro", null, active.terms(), today, null)).hasMessageContaining("nueva versión");
            assertThatThrownBy(() -> active.publish(today)).hasMessageContaining("borrador");
        }

        @Test
        void newVersionCopiesTermsAndOldOneIsSupersededNotMigrated() {
            CommercialPlan v1 = draft().publish(today);
            CommercialPlan v2 = v1.nextVersionDraft(2, today.plusDays(10));
            assertThat(v2.version()).isEqualTo(2);
            assertThat(v2.status()).isEqualTo(Status.DRAFT);
            assertThat(v2.terms()).isEqualTo(v1.terms());
            assertThat(v2.validFrom()).isEqualTo(today.plusDays(10));
            assertThat(v1.supersede().status()).isEqualTo(Status.SUPERSEDED);
            assertThatThrownBy(() -> v1.supersede().checkAssignableOn(today)).hasMessageContaining("publicados");
        }

        @Test
        void validityLimitsTheStartOfAnAssignment() {
            CommercialPlan plan = CommercialPlan.draft("PRO", "Pro", null, terms(Modality.COMMISSION, null, "10", null),
                    today, today.plusMonths(2)).publish(today);
            plan.checkAssignableOn(today.plusMonths(1));
            assertThatThrownBy(() -> plan.checkAssignableOn(today.minusDays(1))).hasMessageContaining("desde");
            assertThatThrownBy(() -> plan.checkAssignableOn(today.plusMonths(3))).hasMessageContaining("hasta");
            assertThatThrownBy(() -> CommercialPlan.draft("PRO", "Pro", null, terms(Modality.COMMISSION, null, "10", null),
                    today, today.minusDays(1))).hasMessageContaining("termina antes");
        }

        @Test
        void expiredDraftCannotBePublished() {
            CommercialPlan expired = CommercialPlan.draft("OLD", "Viejo", null, terms(Modality.COMMISSION, null, "10", null),
                    today.minusMonths(2), today.minusMonths(1));
            assertThatThrownBy(() -> expired.publish(today)).hasMessageContaining("ya terminó");
        }
    }
}
