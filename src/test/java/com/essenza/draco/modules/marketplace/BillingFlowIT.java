package com.essenza.draco.modules.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.essenza.draco.modules.marketplace.application.dto.BillingDtos.AdjustmentRequest;
import com.essenza.draco.modules.marketplace.application.dto.BillingDtos.CommissionDto;
import com.essenza.draco.modules.marketplace.application.dto.BillingDtos.FeeRunDto;
import com.essenza.draco.modules.marketplace.application.dto.BillingDtos.SellerAccountDto;
import com.essenza.draco.modules.marketplace.application.dto.BillingDtos.SellerPaymentRequest;
import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.AssignPlanRequest;
import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.PlanDto;
import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.SavePlanRequest;
import com.essenza.draco.modules.marketplace.application.port.CommissionStore.CommissionFilter;
import com.essenza.draco.modules.marketplace.application.service.AssignmentService;
import com.essenza.draco.modules.marketplace.application.service.CommissionService;
import com.essenza.draco.modules.marketplace.application.service.PlanService;
import com.essenza.draco.modules.marketplace.application.service.SellerAccountService;
import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.AssignmentJdbcStore;
import com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.CommissionJdbcStore;
import com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.PlanJdbcStore;
import com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.SellerJdbcStore;
import com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.SellerLedgerJdbcStore;
import com.essenza.draco.shared.common.marketplace.SellerCommissions.SaleChange;
import com.essenza.draco.shared.common.web.PageableFactory;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.ForbiddenException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Comisiones y estado de cuenta contra MySQL/MariaDB con los adaptadores JDBC reales y los servicios
 * (sin contenedor Spring). Solo contra una base *_test con V17–V20: borra las tablas mp_*.
 */
@EnabledIfEnvironmentVariable(named = "MARKETPLACE_IT_DB_URL", matches = "jdbc:.*_test(\\?.*)?")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BillingFlowIT {

    static final Set<String> ALL = Set.of("PLANES.VER", "PLANES.GESTIONAR", "PLANES.ASIGNAR", "COMISIONES.VER",
            "COMISIONES.GESTIONAR", "COMISIONES.AJUSTAR");
    static final Instant NOW = Instant.parse("2026-11-07T15:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 7);
    static RequestActor actor = new RequestActor(2, "u-1", "demo", ALL);
    static NamedParameterJdbcTemplate jdbc;
    static CommissionService commissions;
    static CommissionService commissionsLater;
    static SellerAccountService accounts;
    static SellerAccountService accountsDec;
    static long delivered, windowed, mixed, noPlan, pDelivered;
    static AssignmentService assignments;

    @BeforeAll
    static void setUp() {
        String url = System.getenv("MARKETPLACE_IT_DB_URL");
        assertThat(url).matches("jdbc:.*_test(\\?.*)?");
        jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(url, System.getenv("MARKETPLACE_IT_DB_USER"),
                System.getenv().getOrDefault("MARKETPLACE_IT_DB_PASSWORD", "")));
        jdbc.update("UPDATE orders SET seller_id = NULL WHERE seller_id IS NOT NULL", new MapSqlParameterSource());
        jdbc.update("UPDATE product_orders SET offer_id = NULL WHERE offer_id IS NOT NULL", new MapSqlParameterSource());
        for (String t : List.of("mp_seller_ledger", "mp_commission_adjustments", "mp_commissions", "mp_offer_reservations",
                "mp_offer_events", "mp_offers", "mp_plan_assignments", "mp_plan_events", "mp_seller_events",
                "mp_seller_documents", "mp_seller_contacts", "mp_sellers", "mp_plans")) {
            jdbc.update("DELETE FROM " + t, new MapSqlParameterSource());
        }
        Clock clock = Clock.fixed(NOW, ZoneId.of("America/Bogota"));
        var sellerStore = new SellerJdbcStore(jdbc);
        var planStore = new PlanJdbcStore(jdbc);
        var assignStore = new AssignmentJdbcStore(jdbc);
        var commissionStore = new CommissionJdbcStore(jdbc);
        var ledgerStore = new SellerLedgerJdbcStore(jdbc);
        commissions = new CommissionService(commissionStore, ledgerStore, assignStore, () -> actor, clock);
        commissionsLater = new CommissionService(commissionStore, ledgerStore, assignStore, () -> actor,
                Clock.fixed(NOW.plusSeconds(5 * 86400), ZoneId.of("America/Bogota")));
        accounts = new SellerAccountService(sellerStore, ledgerStore, commissionStore, assignStore, () -> actor, clock);
        accountsDec = new SellerAccountService(sellerStore, ledgerStore, commissionStore, assignStore, () -> actor,
                Clock.fixed(Instant.parse("2026-12-02T15:00:00Z"), ZoneId.of("America/Bogota")));
        PlanService plans = new PlanService(planStore, assignStore, () -> actor, clock);
        assignments = new AssignmentService(sellerStore, planStore, assignStore, () -> actor, clock);

        pDelivered = publish(plans, plan("ENTREGA", "COMMISSION", null, "10", "500", "ORDER_DELIVERED", null, null));
        long pWindow = publish(plans, plan("VENTANA", "COMMISSION", null, "8", null, "RETURN_WINDOW_CLOSED", 3, null));
        long pMixed = publish(plans, plan("MIXTO", "MIXED", "200000", "5", null, "PAYMENT_CONFIRMED", null, "IN_ADVANCE"));
        delivered = seller(2, "Moda Andina");
        windowed = seller(2, "Ropa Sur");
        mixed = seller(2, "Casa Bonita");
        noPlan = seller(2, "Sin Plan");
        assignments.assign(delivered, new AssignPlanRequest(pDelivered, null, null, null, null, null));
        assignments.assign(windowed, new AssignPlanRequest(pWindow, null, null, null, null, null));
        assignments.assign(mixed, new AssignPlanRequest(pMixed, null, null, null, null, null));
    }

    static SavePlanRequest plan(String code, String modality, String fee, String pct, String fixed, String accrual,
                                Integer window, String billing) {
        return new SavePlanRequest(code, "Plan " + code, null, modality, "COP", fee == null ? null : new BigDecimal(fee),
                new BigDecimal(pct), fixed == null ? null : new BigDecimal(fixed), "ITEMS_NET_OF_DISCOUNTS", false, false,
                accrual, window, "REVERSE_PROPORTIONAL", billing, 5, new BigDecimal("2"), null, null, null, null);
    }

    static long publish(PlanService plans, SavePlanRequest request) {
        PlanDto draft = plans.create(request);
        return plans.publish(draft.id(), draft.lockVersion()).id();
    }

    static long seller(long company, String name) {
        jdbc.update("""
                INSERT INTO mp_sellers (company_id, kind, legal_name, trade_name, doc_type, doc_number, status, created_at,
                  created_by, updated_at, updated_by)
                VALUES (:company, 'EXTERNAL', :name, :name, 'NIT', CONCAT('800', FLOOR(RAND() * 1000000)), 'APPROVED', NOW(6),
                  'it', NOW(6), 'it')""", new MapSqlParameterSource("company", company).addValue("name", name));
        return jdbc.queryForObject("SELECT MAX(id) FROM mp_sellers", new MapSqlParameterSource(), Long.class);
    }

    /** Venta de 100.000 sin impuestos + 19.000 de IVA, sin envío. */
    static SaleChange sale(long orderId, long sellerId, String status, String from) {
        return new SaleChange(orderId, null, sellerId, "ORD-" + orderId, status, from, "COP", new BigDecimal("100000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("19000.00"), NOW);
    }

    static CommissionDto commissionOf(long orderId) {
        return commissions.search(new CommissionFilter("ORD-" + orderId, null, null), PageableFactory.of(0, 5, null, Set.of(),
                Sort.by(Sort.Order.desc("createdAt")))).getContent().stream().filter(c -> c.orderId() == orderId).findFirst()
                .orElseThrow();
    }

    @Test @Order(1)
    void commissionAccruesOnDeliveryAndRefundsReverseIt() {
        commissions.orderChanged(sale(9001, delivered, "PAID", "PENDING"));
        commissions.orderChanged(sale(9001, delivered, "PAID", "PENDING")); // repetido: no duplica
        CommissionDto c = commissionOf(9001);
        assertThat(c.status()).isEqualTo("PENDING");
        assertThat(c.base()).isEqualByComparingTo("100000");
        assertThat(c.amount()).isEqualByComparingTo("10500"); // 10 % + 500 fijo
        assertThat(c.accrueOn()).isNull();
        assertThat(accounts.statement(delivered, null).balance()).isZero();
        assertThat(accounts.statement(delivered, null).pendingCommissions()).isEqualByComparingTo("10500");

        commissions.orderChanged(sale(9001, delivered, "SHIPPED", "PAID"));
        commissions.orderChanged(sale(9001, delivered, "DELIVERED", "SHIPPED"));
        c = commissionOf(9001);
        assertThat(c.status()).isEqualTo("ACCRUED");
        assertThat(c.accrueOn()).isEqualTo(TODAY);
        SellerAccountDto account = accounts.statement(delivered, null);
        assertThat(account.balance()).isEqualByComparingTo("10500");
        assertThat(account.entries()).extracting(e -> e.type()).containsExactly("COMMISSION");
        assertThat(account.entries().get(0).dueOn()).isEqualTo(TODAY.plusDays(5));

        // Devolución de la mitad (59.500 con IVA): se reversa la mitad de la comisión, incluido el fijo.
        commissions.refunded(77L, 9001L, new BigDecimal("59500"));
        commissions.refunded(77L, 9001L, new BigDecimal("59500")); // misma devolución: no repite
        c = commissionOf(9001);
        assertThat(c.reversedAmount()).isEqualByComparingTo("5250");
        account = accounts.statement(delivered, null);
        assertThat(account.balance()).isEqualByComparingTo("5250");
        assertThat(account.entries()).extracting(e -> e.type()).containsExactly("COMMISSION_REVERSAL", "COMMISSION");

        // Cancelar después de exigible: reverso total, nunca más de lo cobrado.
        commissions.orderChanged(sale(9001, delivered, "CANCELLED", "DELIVERED"));
        assertThat(commissionOf(9001).reversedAmount()).isEqualByComparingTo("10500");
        assertThat(accounts.statement(delivered, null).balance()).isZero();
    }

    @Test @Order(2)
    void cancelledBeforeAccrualIsVoidedAndWindowWaits() {
        commissions.orderChanged(sale(9002, delivered, "PAID", "PENDING"));
        commissions.orderChanged(sale(9002, delivered, "CANCELLED", "PAID"));
        assertThat(commissionOf(9002).status()).isEqualTo("VOIDED");
        assertThat(accounts.statement(delivered, null).balance()).isZero();

        commissions.orderChanged(sale(9003, windowed, "PAID", "PENDING"));
        commissions.orderChanged(sale(9003, windowed, "DELIVERED", "SHIPPED"));
        CommissionDto c = commissionOf(9003);
        assertThat(c.status()).isEqualTo("PENDING");
        assertThat(c.accrueOn()).isEqualTo(TODAY.plusDays(3));
        assertThat(commissions.dueIds()).doesNotContain(c.id());
        // Una devolución antes de exigible reduce la comisión que se asentará.
        commissions.refunded(78L, 9003L, new BigDecimal("119000"));
        assertThat(commissionOf(9003).reversedAmount()).isEqualByComparingTo("8000");

        commissions.orderChanged(sale(9004, windowed, "PAID", "PENDING"));
        commissions.orderChanged(sale(9004, windowed, "DELIVERED", "SHIPPED"));
        // Cinco días después el trabajo diario devenga ambas; la devuelta completa no asienta nada.
        assertThat(commissionsLater.dueIds()).hasSize(2);
        commissionsLater.dueIds().forEach(commissionsLater::accrueOne);
        assertThat(commissionOf(9003).status()).isEqualTo("ACCRUED");
        assertThat(commissionOf(9004).status()).isEqualTo("ACCRUED");
        assertThat(accounts.statement(windowed, TODAY.plusDays(5)).balance()).isEqualByComparingTo("8000");
    }

    @Test @Order(3)
    void withoutPlanGoesToReviewAndPaymentConfirmedAccruesAtOnce() {
        commissions.orderChanged(sale(9005, noPlan, "PAID", "PENDING"));
        CommissionDto review = commissionOf(9005);
        assertThat(review.status()).isEqualTo("REVIEW");
        assertThat(review.reviewReason()).contains("plan vigente");

        commissions.orderChanged(sale(9006, mixed, "PAID", "PENDING"));
        assertThat(commissionOf(9006).status()).isEqualTo("ACCRUED");
        assertThat(commissionOf(9006).amount()).isEqualByComparingTo("5000");
    }

    @Test @Order(4)
    void monthlyFeesPaymentsAndOverdue() {
        // Sin prorrateo: cobra el ciclo la vigencia en curso el día de cobro. Empezó el 7: noviembre no se cobra.
        assertThat(accounts.generateFees("2026-11").created()).isZero();
        FeeRunDto run = accountsDec.generateFees("2026-12");
        assertThat(run.created()).isEqualTo(1);
        assertThat(run.total()).isEqualByComparingTo("200000");
        assertThat(accountsDec.generateFees("2026-12").alreadyCharged()).isEqualTo(1);
        assertThatThrownBy(() -> accountsDec.generateFees("2027-01")).isInstanceOf(MarketplaceRuleViolation.class);

        SellerAccountDto account = accountsDec.statement(mixed, null);
        assertThat(account.balance()).isEqualByComparingTo("205000");
        // La comisión venció el 12 de noviembre; la mensualidad vence el 6 de diciembre.
        assertThat(account.overdue()).isEqualByComparingTo("5000");
        account = accountsDec.registerPayment(mixed, new SellerPaymentRequest(new BigDecimal("150000"), null, "TRF-1", null,
                "pago-0001-abcd"));
        assertThat(account.balance()).isEqualByComparingTo("55000");
        assertThat(account.overdue()).isZero();
        assertThatThrownBy(() -> accountsDec.registerPayment(mixed, new SellerPaymentRequest(new BigDecimal("150000"), null,
                "TRF-1", null, "pago-0001-abcd"))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> accountsDec.registerPayment(mixed, new SellerPaymentRequest(BigDecimal.TEN,
                LocalDate.of(2026, 12, 3), "X", null, null))).hasMessageContaining("futura");

        // A fin de diciembre lo que queda de la mensualidad está vencido y genera interés estimado.
        SellerAccountDto later = accountsDec.statement(mixed, LocalDate.of(2026, 12, 31));
        assertThat(later.overdue()).isEqualByComparingTo("55000");
        assertThat(later.lateInterestEstimate()).isPositive();

        account = accountsDec.adjust(mixed, new AdjustmentRequest(new BigDecimal("-5000"), null, "Descuento comercial acordado",
                "ajuste-0001-abcd"));
        assertThat(account.balance()).isEqualByComparingTo("50000");
        assertThatThrownBy(() -> accountsDec.adjust(mixed, new AdjustmentRequest(BigDecimal.ONE, null, "x", null)))
                .hasMessageContaining("motivo");
        assertThat(commissions.summary().receivable()).isEqualByComparingTo("58000");
    }

    @Test @Order(5)
    void permissionsAndIsolation() {
        RequestActor previous = actor;
        try {
            actor = new RequestActor(3, "u-9", "otra", ALL);
            assertThatThrownBy(() -> accounts.statement(mixed, null)).isInstanceOf(NotFoundException.class);
            assertThat(commissions.summary().receivable()).isZero();
            actor = new RequestActor(2, "u-2", "lector", Set.of("COMISIONES.VER"));
            assertThat(accounts.statement(mixed, LocalDate.of(2026, 12, 31)).balance()).isEqualByComparingTo("50000");
            assertThatThrownBy(() -> accounts.registerPayment(mixed, new SellerPaymentRequest(BigDecimal.TEN, TODAY, "R", null,
                    null))).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> accounts.generateFees("2026-11")).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> accounts.adjust(mixed, new AdjustmentRequest(BigDecimal.ONE, TODAY, "motivo largo", null)))
                    .isInstanceOf(ForbiddenException.class);
        } finally {
            actor = previous;
        }
    }

    @Test @Order(6)
    void reviewIsRecalculatedOnceThePlanExists() {
        // La venta sin plan quedó en revisión; se entrega y luego se asigna el plan que faltaba.
        commissions.orderChanged(sale(9005, noPlan, "DELIVERED", "SHIPPED"));
        CommissionDto review = commissionOf(9005);
        assertThat(commissions.recalculate(review.id()).status()).isEqualTo("REVIEW");
        assignments.assign(noPlan, new AssignPlanRequest(pDelivered, null, null, null, null, null));
        CommissionDto fixed = commissions.recalculate(review.id());
        assertThat(fixed.status()).isEqualTo("ACCRUED");
        assertThat(fixed.amount()).isEqualByComparingTo("10500");
        assertThat(fixed.deliveredOn()).isEqualTo(TODAY);
        assertThat(accounts.statement(noPlan, null).balance()).isEqualByComparingTo("10500");
        assertThatThrownBy(() -> commissions.recalculate(review.id())).hasMessageContaining("revisión");
    }
}
