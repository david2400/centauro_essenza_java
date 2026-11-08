package com.essenza.draco.modules.marketplace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.marketplace.domain.billing.CommissionRules;
import com.essenza.draco.modules.marketplace.domain.billing.FeeCycle;
import com.essenza.draco.modules.marketplace.domain.billing.SellerStatement;
import com.essenza.draco.modules.marketplace.domain.billing.SellerStatement.Entry;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.AccrualEvent;
import com.essenza.draco.modules.marketplace.domain.plan.PlanEnums.FeeBilling;

class BillingRulesTest {

    static final LocalDate PAID = LocalDate.of(2026, 11, 3);
    static final LocalDate DELIVERED = LocalDate.of(2026, 11, 6);

    @Test
    void accrualDateFollowsThePlanEvent() {
        assertThat(CommissionRules.accrueOn(AccrualEvent.PAYMENT_CONFIRMED, PAID, null, 0)).isEqualTo(PAID);
        assertThat(CommissionRules.accrueOn(AccrualEvent.ORDER_DELIVERED, PAID, null, 0)).isNull();
        assertThat(CommissionRules.accrueOn(AccrualEvent.ORDER_DELIVERED, PAID, DELIVERED, 0)).isEqualTo(DELIVERED);
        assertThat(CommissionRules.accrueOn(AccrualEvent.RETURN_WINDOW_CLOSED, PAID, null, 15)).isNull();
        assertThat(CommissionRules.accrueOn(AccrualEvent.RETURN_WINDOW_CLOSED, PAID, DELIVERED, 15)).isEqualTo(DELIVERED.plusDays(15));
        assertThat(CommissionRules.isDue(DELIVERED, DELIVERED)).isTrue();
        assertThat(CommissionRules.isDue(DELIVERED, DELIVERED.minusDays(1))).isFalse();
        assertThat(CommissionRules.isDue(null, DELIVERED)).isFalse();
    }

    @Test
    void refundedBaseIsProportionalAndCapped() {
        BigDecimal base = new BigDecimal("100000.00");
        assertThat(CommissionRules.refundedBase(new BigDecimal("59500"), new BigDecimal("119000"), base, "COP"))
                .isEqualByComparingTo("50000");
        assertThat(CommissionRules.refundedBase(new BigDecimal("200000"), new BigDecimal("119000"), base, "COP"))
                .isEqualByComparingTo("100000");
        assertThat(CommissionRules.refundedBase(BigDecimal.ZERO, new BigDecimal("119000"), base, "COP")).isZero();
    }

    @Test
    void feeCycles() {
        YearMonth november = FeeCycle.parse("2026-11");
        assertThat(FeeCycle.chargeDate(november, FeeBilling.IN_ADVANCE)).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(FeeCycle.chargeDate(november, FeeBilling.IN_ARREARS)).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(FeeCycle.chargeable(november, FeeBilling.IN_ADVANCE, LocalDate.of(2026, 11, 7))).isTrue();
        assertThat(FeeCycle.chargeable(november, FeeBilling.IN_ARREARS, LocalDate.of(2026, 11, 7))).isFalse();
        assertThat(FeeCycle.key(5, november)).isEqualTo("FEE:5:2026-11");
        assertThatThrownBy(() -> FeeCycle.parse("11-2026")).isInstanceOf(MarketplaceRuleViolation.class);
    }

    @Test
    void paymentsApplyToOldestChargesFirst() {
        LocalDate asOf = LocalDate.of(2026, 12, 31);
        List<Entry> entries = List.of(
                new Entry(1, new BigDecimal("300000.00"), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 6), new BigDecimal("2")),
                new Entry(2, new BigDecimal("50000.00"), LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 4), new BigDecimal("2")),
                new Entry(3, new BigDecimal("-200000.00"), LocalDate.of(2026, 11, 20), null, BigDecimal.ZERO));
        SellerStatement.Result result = SellerStatement.of("COP", entries, asOf);
        assertThat(result.balance()).isEqualByComparingTo("150000");
        // Del primer cargo quedan 100.000 vencidos hace 55 días; el segundo aún no vence.
        assertThat(result.overdue()).isEqualByComparingTo("100000");
        assertThat(result.open()).hasSize(2);
        assertThat(result.open().get(0).daysOverdue()).isEqualTo(55);
        // 100.000 × 2 % × 55/30 = 3.666,67
        assertThat(result.lateInterest()).isEqualByComparingTo("3666.67");
        assertThat(result.unappliedCredit()).isZero();
    }

    @Test
    void creditBeyondChargesStaysUnapplied() {
        SellerStatement.Result result = SellerStatement.of("COP", List.of(
                new Entry(1, new BigDecimal("10000.00"), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1), BigDecimal.ONE),
                new Entry(2, new BigDecimal("-25000.00"), LocalDate.of(2026, 11, 2), null, BigDecimal.ZERO),
                new Entry(3, new BigDecimal("99999.00"), LocalDate.of(2027, 1, 1), null, BigDecimal.ONE)),
                LocalDate.of(2026, 12, 1));
        assertThat(result.balance()).isEqualByComparingTo("-15000");
        assertThat(result.overdue()).isZero();
        assertThat(result.unappliedCredit()).isEqualByComparingTo("15000");
        assertThat(result.open()).isEmpty();
    }
}
