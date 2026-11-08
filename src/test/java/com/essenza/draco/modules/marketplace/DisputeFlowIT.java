package com.essenza.draco.modules.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
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

import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.CancelRequest;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.DisputeDetailDto;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.DisputeEventDto;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.OpenDisputeRequest;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.ResolveRequest;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.RespondRequest;
import com.essenza.draco.modules.marketplace.application.service.DisputeService;
import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.DisputeJdbcStore;
import com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.SellerLedgerJdbcStore;
import com.essenza.draco.shared.common.web.PageableFactory;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.ForbiddenException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.RequestActor;

/** Reclamos contra MySQL/MariaDB con el adaptador JDBC real. Solo contra una base *_test. */
@EnabledIfEnvironmentVariable(named = "MARKETPLACE_IT_DB_URL", matches = "jdbc:.*_test(\\?.*)?")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DisputeFlowIT {

    static final long COMPANY = 2;
    static final Set<String> ALL = Set.of("DISPUTAS.VER", "DISPUTAS.GESTIONAR", "DISPUTAS.RESOLVER");
    static RequestActor actor = new RequestActor(COMPANY, "u-1", "Servicio al cliente", ALL);
    static NamedParameterJdbcTemplate jdbc;
    static DisputeService disputes;
    static DisputeService later; // 5 días después: el plazo del vendedor (3 días) ya venció
    static long vendor, own, delivered, second, pending, ownOrder, firstId;

    @BeforeAll
    static void setUp() {
        String url = System.getenv("MARKETPLACE_IT_DB_URL");
        assertThat(url).matches("jdbc:.*_test(\\?.*)?");
        jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(url, System.getenv("MARKETPLACE_IT_DB_USER"),
                System.getenv().getOrDefault("MARKETPLACE_IT_DB_PASSWORD", "")));
        clean();
        for (String t : List.of("mp_seller_ledger", "mp_commission_adjustments", "mp_commissions", "mp_offer_reservations",
                "product_orders", "orders", "purchases", "mp_offer_events", "mp_offers", "mp_plan_assignments", "mp_plan_events",
                "mp_seller_events", "mp_seller_documents", "mp_seller_contacts", "mp_sellers", "mp_plans")) {
            jdbc.update("DELETE FROM " + t, new MapSqlParameterSource());
        }
        var store = new DisputeJdbcStore(jdbc);
        var ledger = new SellerLedgerJdbcStore(jdbc);
        disputes = new DisputeService(store, ledger, () -> actor,
                Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneId.of("America/Bogota")), 3);
        later = new DisputeService(store, ledger, () -> actor,
                Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"), ZoneId.of("America/Bogota")), 3);
        vendor = seller("EXTERNAL", "Moda Andina");
        own = seller("OWN", "Tienda");
        delivered = order("ORD-D1", "DELIVERED", vendor);
        second = order("ORD-D2", "SHIPPED", vendor);
        pending = order("ORD-D3", "PENDING", vendor);
        ownOrder = order("ORD-D4", "DELIVERED", own);
    }

    @AfterAll
    static void tearDown() {
        clean();
    }

    static void clean() {
        jdbc.update("DELETE FROM mp_dispute_events", new MapSqlParameterSource());
        jdbc.update("DELETE FROM mp_disputes", new MapSqlParameterSource());
    }

    static long seller(String kind, String name) {
        jdbc.update("""
                INSERT INTO mp_sellers (company_id, kind, legal_name, trade_name, doc_type, doc_number, status, created_at,
                  created_by, updated_at, updated_by)
                VALUES (:c, :k, :n, :n, 'NIT', CONCAT('900', FLOOR(RAND() * 1000000)), 'APPROVED', NOW(6), 'it', NOW(6), 'it')""",
                new MapSqlParameterSource("c", COMPANY).addValue("k", kind).addValue("n", name));
        return jdbc.queryForObject("SELECT MAX(id) FROM mp_sellers", new MapSqlParameterSource(), Long.class);
    }

    static long order(String number, String state, long seller) {
        jdbc.update("""
                INSERT INTO orders (order_number, state, total, stock_managed, seller_id, customer_name, channel, created_at)
                VALUES (:n, :s, 100000, 1, :seller, 'Ana Pérez', 'ONLINE', NOW(6))""",
                new MapSqlParameterSource("n", number).addValue("s", state).addValue("seller", seller));
        return jdbc.queryForObject("SELECT MAX(id_order) FROM orders", new MapSqlParameterSource(), Long.class);
    }

    static OpenDisputeRequest open(long order) {
        return new OpenDisputeRequest(order, "DEFECTIVE", "La chaqueta llegó con la cremallera rota", new BigDecimal("40000"));
    }

    @Test @Order(1)
    void openOnlyOnPaidThirdPartyOrders() {
        DisputeDetailDto created = disputes.open(open(delivered));
        firstId = created.summary().id();
        assertThat(created.summary().number()).startsWith("DSP-");
        assertThat(created.summary().status()).isEqualTo("OPEN");
        assertThat(created.summary().respondBy()).isEqualTo(java.time.LocalDate.of(2026, 11, 12));
        assertThat(created.actions()).containsExactly("RESPOND", "CANCEL"); // aún no se puede decidir
        assertThatThrownBy(() -> disputes.open(open(delivered))).isInstanceOf(MarketplaceRuleViolation.class)
                .hasMessageContaining("ya tiene");
        assertThatThrownBy(() -> disputes.open(open(pending))).hasMessageContaining("pagadas");
        assertThatThrownBy(() -> disputes.open(open(ownOrder))).hasMessageContaining("externos");
        assertThatThrownBy(() -> disputes.open(new OpenDisputeRequest(delivered, "LOST", "Descripción suficiente", null)))
                .hasMessageContaining("motivo");
        assertThatThrownBy(() -> disputes.open(new OpenDisputeRequest(second, "OTHER", "Valor excesivo reclamado",
                new BigDecimal("200000")))).hasMessageContaining("superar");
    }

    @Test @Order(2)
    void sellerRespondsAndStoreDecidesWithCharge() {
        DisputeDetailDto current = disputes.get(firstId);
        assertThatThrownBy(() -> disputes.resolve(firstId, new ResolveRequest("REFUND", new BigDecimal("40000"), null, "Aún no",
                null, current.lockVersion()))).hasMessageContaining("tiene hasta");
        DisputeDetailDto answered = disputes.respond(firstId, new RespondRequest("Se envió en buen estado, guía 77",
                current.lockVersion()));
        assertThat(answered.summary().status()).isEqualTo("SELLER_RESPONDED");
        assertThat(answered.actions()).containsExactly("CANCEL", "RESOLVE");
        assertThatThrownBy(() -> disputes.respond(firstId, new RespondRequest("Otra respuesta", answered.lockVersion())))
                .hasMessageContaining("ya respondió");
        assertThatThrownBy(() -> disputes.resolve(firstId, new ResolveRequest("REFUND", new BigDecimal("40000"), null, "Viejo",
                null, current.lockVersion()))).isInstanceOf(ConflictException.class);

        DisputeDetailDto resolved = disputes.resolve(firstId, new ResolveRequest("refund", new BigDecimal("40000"),
                new BigDecimal("40000"), "Las fotos muestran el daño", null, answered.lockVersion()));
        assertThat(resolved.summary().status()).isEqualTo("RESOLVED_BUYER");
        assertThat(resolved.actions()).isEmpty();
        assertThat(resolved.events()).extracting(DisputeEventDto::action).containsExactly("OPEN", "RESPOND", "RESOLVE");
        BigDecimal charged = jdbc.queryForObject("SELECT SUM(amount) FROM mp_seller_ledger WHERE seller_id = :s AND entry_type = 'DISPUTE_CHARGE'",
                new MapSqlParameterSource("s", vendor), BigDecimal.class);
        assertThat(charged).isEqualByComparingTo("40000");
        // Cerrado: se puede abrir otro reclamo sobre la misma orden.
        assertThat(disputes.ofOrder(delivered)).get().extracting(d -> d.status()).isEqualTo("RESOLVED_BUYER");
    }

    @Test @Order(3)
    void deadlinePassedLetsTheStoreDecideAndBuyersCanWithdraw() {
        DisputeDetailDto open = disputes.open(new OpenDisputeRequest(second, "NOT_RECEIVED", "No ha llegado después de 10 días", null));
        assertThat(later.get(open.summary().id()).summary().overdue()).isTrue();
        assertThat(later.search("OVERDUE", null, null, page()).getContent()).extracting(d -> d.id()).containsExactly(open.summary().id());
        DisputeDetailDto rejected = later.resolve(open.summary().id(), new ResolveRequest("REJECTED", null, null,
                "La transportadora confirma la entrega", null, open.lockVersion()));
        assertThat(rejected.summary().status()).isEqualTo("RESOLVED_SELLER");

        DisputeDetailDto again = disputes.open(new OpenDisputeRequest(second, "OTHER", "Cobro duplicado en el extracto", null));
        DisputeDetailDto withdrawn = disputes.cancel(again.summary().id(), new CancelRequest("El banco reversó el cobro",
                again.lockVersion()));
        assertThat(withdrawn.summary().status()).isEqualTo("CANCELLED");
        var stats = disputes.stats();
        assertThat(stats.open()).isZero();
        assertThat(stats.resolvedBuyer30()).isEqualTo(1);
        assertThat(stats.resolvedSeller30()).isEqualTo(1);
        assertThat(stats.charged30()).isEqualByComparingTo("40000");
        assertThat(disputes.search("ACTIVE", null, null, page()).getTotalElements()).isZero();
        assertThat(disputes.search(null, vendor, "moda", page()).getTotalElements()).isEqualTo(3);
    }

    @Test @Order(4)
    void permissionsAndIsolation() {
        RequestActor previous = actor;
        try {
            actor = new RequestActor(COMPANY, "u-2", "lector", Set.of("DISPUTAS.VER"));
            assertThat(disputes.get(firstId).actions()).isEmpty();
            assertThatThrownBy(() -> disputes.open(open(delivered))).isInstanceOf(ForbiddenException.class);
            actor = new RequestActor(COMPANY, "u-3", "gestor", Set.of("DISPUTAS.GESTIONAR"));
            DisputeDetailDto open = disputes.open(open(delivered));
            assertThatThrownBy(() -> later.resolve(open.summary().id(), new ResolveRequest("REJECTED", null, null, "Sin permiso",
                    null, open.lockVersion()))).isInstanceOf(ForbiddenException.class);
            actor = new RequestActor(3, "u-9", "otra", ALL);
            assertThatThrownBy(() -> disputes.get(firstId)).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> disputes.open(open(second))).isInstanceOf(NotFoundException.class);
            assertThat(disputes.search(null, null, null, page()).getTotalElements()).isZero();
        } finally {
            actor = previous;
        }
    }

    static org.springframework.data.domain.Pageable page() {
        return PageableFactory.of(0, 20, null, Set.of("createdAt"), Sort.by(Sort.Order.desc("createdAt")));
    }
}
