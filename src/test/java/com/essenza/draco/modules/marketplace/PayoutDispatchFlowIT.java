package com.essenza.draco.modules.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.BatchDetailDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.CreateBatchRequest;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.CreateItem;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.PayRequest;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.ReasonRequest;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.SaveBankAccountRequest;
import com.essenza.draco.modules.marketplace.application.port.PayoutDispatcher;
import com.essenza.draco.modules.marketplace.application.port.SellerLedgerStore;
import com.essenza.draco.modules.marketplace.application.service.PayoutDispatchService;
import com.essenza.draco.modules.marketplace.application.service.PayoutService;
import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.PayoutJdbcStore;
import com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.SellerLedgerJdbcStore;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.ForbiddenException;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Dispersión automática de lotes de pago contra MySQL/MariaDB con los adaptadores JDBC reales y un
 * proveedor simulado (no sale a la red). Solo *_test.
 */
@EnabledIfEnvironmentVariable(named = "MARKETPLACE_IT_DB_URL", matches = "jdbc:.*_test(\\?.*)?")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PayoutDispatchFlowIT {

    static final long COMPANY = 4;
    static final RequestActor MANAGER = new RequestActor(COMPANY, "u-1", "Tesorería", Set.of("COMISIONES.VER", "COMISIONES.GESTIONAR"));
    static RequestActor actor = MANAGER;
    static NamedParameterJdbcTemplate jdbc;
    static SellerLedgerJdbcStore ledger;
    static PayoutService payouts;
    static PayoutDispatchService dispatch;
    static FakeDispatcher provider = new FakeDispatcher();
    static MutableClock clock = new MutableClock(Instant.parse("2026-11-09T15:00:00Z"));
    static long alpha, beta, gamma, firstBatch;

    static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("America/Bogota");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** Proveedor simulado: guarda los envíos y responde lo que diga la prueba. */
    static final class FakeDispatcher implements PayoutDispatcher {
        boolean configured = true;
        final List<DispatchRequest> sent = new ArrayList<>();
        RuntimeException failNext;
        String payoutId = "po-1";
        Optional<String> byReference = Optional.empty();
        List<TxStatus> statuses = List.of();

        @Override
        public String provider() {
            return "WOMPI";
        }

        @Override
        public boolean configured() {
            return configured;
        }

        @Override
        public String environment() {
            return "sandbox";
        }

        @Override
        public Dispatched dispatch(DispatchRequest request) {
            sent.add(request);
            if (failNext != null) {
                RuntimeException ex = failNext;
                failNext = null;
                throw ex;
            }
            return new Dispatched(payoutId, "PENDING");
        }

        @Override
        public Optional<String> findByReference(String reference) {
            return byReference;
        }

        @Override
        public List<TxStatus> transactions(String id) {
            return statuses;
        }

        @Override
        public List<ProviderBank> banks() {
            return List.of(new ProviderBank("w-bancolombia", "BANCOLOMBIA S.A.", "1007"), new ProviderBank("w-x", "Banco Inexistente", "9"));
        }
    }

    @BeforeAll
    static void setUp() {
        String url = System.getenv("MARKETPLACE_IT_DB_URL");
        assertThat(url).matches("jdbc:.*_test(\\?.*)?");
        jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(url, System.getenv("MARKETPLACE_IT_DB_USER"),
                System.getenv().getOrDefault("MARKETPLACE_IT_DB_PASSWORD", "")));
        clean();
        ledger = new SellerLedgerJdbcStore(jdbc);
        PayoutJdbcStore store = new PayoutJdbcStore(jdbc);
        payouts = new PayoutService(store, ledger, () -> actor, clock, 7, provider);
        TransactionOperations direct = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        };
        dispatch = new PayoutDispatchService(store, ledger, payouts, provider, () -> actor, clock, direct);
        alpha = seller("Alfa Dispersión", "pagos@alfa.co");
        beta = seller("Beta Dispersión", "beta@x.co");
        gamma = seller("Gama Dispersión", null);
        for (long s : List.of(alpha, beta, gamma)) {
            post(s, "MARKETPLACE_SALE", "-100000", "MP-SALE:DIT-" + s);
        }
        long bancolombia = bankId("BANCOLOMBIA");
        payouts.saveBankAccount(alpha, new SaveBankAccountRequest(bancolombia, "SAVINGS", "12345678901", null, "Alfa SAS", "NIT", "900111222"));
        payouts.saveBankAccount(beta, new SaveBankAccountRequest(null, "BRE_B_KEY", "3001234567", "PHONE", "Beta Pérez", "CC", "1020304050"));
        payouts.saveBankAccount(gamma, new SaveBankAccountRequest(bancolombia, "CHECKING", "99887766", null, "Gama", "CC", "123456"));
    }

    @AfterAll
    static void tearDown() {
        clean();
    }

    static void clean() {
        jdbc.update("UPDATE mp_banks SET wompi_bank_id = NULL WHERE code = 'BANCOLOMBIA'", new MapSqlParameterSource());
        jdbc.update("DELETE i FROM mp_payout_items i JOIN mp_payout_batches b ON b.id = i.batch_id WHERE b.company_id = :c",
                new MapSqlParameterSource("c", COMPANY));
        jdbc.update("DELETE FROM mp_payout_batches WHERE company_id = :c", new MapSqlParameterSource("c", COMPANY));
        jdbc.update("DELETE FROM mp_seller_bank_accounts WHERE company_id = :c", new MapSqlParameterSource("c", COMPANY));
        jdbc.update("DELETE FROM mp_seller_ledger WHERE company_id = :c", new MapSqlParameterSource("c", COMPANY));
        jdbc.update("DELETE FROM mp_sellers WHERE company_id = :c AND created_by = 'it-dispatch'", new MapSqlParameterSource("c", COMPANY));
    }

    static long seller(String name, String email) {
        jdbc.update("""
                INSERT INTO mp_sellers (company_id, kind, legal_name, trade_name, doc_type, doc_number, email, status, created_at,
                  created_by, updated_at, updated_by)
                VALUES (:c, 'EXTERNAL', :n, :n, 'NIT', CONCAT('902', FLOOR(RAND() * 1000000)), :e, 'APPROVED', NOW(6), 'it-dispatch',
                  NOW(6), 'it')""",
                new MapSqlParameterSource("c", COMPANY).addValue("n", name).addValue("e", email));
        return jdbc.queryForObject("SELECT MAX(id) FROM mp_sellers", new MapSqlParameterSource(), Long.class);
    }

    static void post(long seller, String type, String amount, String key) {
        ledger.post(COMPANY, new SellerLedgerStore.NewEntry(seller, type, new BigDecimal(amount), "COP", LocalDate.of(2026, 11, 1), null,
                BigDecimal.ZERO, "IT", null, null, key, null), "it", "it", Instant.now());
    }

    static long bankId(String code) {
        return jdbc.queryForObject("SELECT id FROM mp_banks WHERE code = :c", new MapSqlParameterSource("c", code), Long.class);
    }

    static BigDecimal balance(long seller) {
        return ledger.balances(COMPANY).getOrDefault(seller, BigDecimal.ZERO);
    }

    static long itemOf(BatchDetailDto batch, long seller) {
        return batch.items().stream().filter(i -> i.sellerId() == seller).findFirst().orElseThrow().id();
    }

    @Test @Order(1)
    void problemsBlockDispatchUntilDataIsComplete() {
        BatchDetailDto batch = payouts.create(new CreateBatchRequest(List.of(new CreateItem(alpha, null), new CreateItem(beta, null),
                new CreateItem(gamma, null)), null));
        firstBatch = batch.summary().id();
        assertThat(batch.dispatch().provider()).isEqualTo("WOMPI");
        assertThat(batch.dispatch().status()).isNull();
        assertThat(batch.actions()).doesNotContain("DISPATCH");
        assertThat(batch.dispatch().problems()).hasSize(2)
                .anySatisfy(p -> assertThat(p).startsWith("Alfa").contains("id de la entidad"))
                .anySatisfy(p -> assertThat(p).startsWith("Gama").contains("correo"));
        assertThatThrownBy(() -> dispatch.dispatch(firstBatch)).isInstanceOf(MarketplaceRuleViolation.class).hasMessageContaining("Faltan datos");
        assertThat(provider.sent).isEmpty();

        // Ligar el catálogo con el proveedor: Bancolombia por código; la entidad del proveedor sin par se ignora.
        var match = dispatch.matchProviderBanks();
        assertThat(match.matched()).isGreaterThanOrEqualTo(1);
        assertThat(match.providerBanks()).anySatisfy(b -> {
            assertThat(b.id()).isEqualTo("w-bancolombia");
            assertThat(b.linkedBankId()).isEqualTo(bankId("BANCOLOMBIA"));
        });
        payouts.reject(firstBatch, itemOf(batch, gamma), new ReasonRequest("Sin correo registrado"));
        BatchDetailDto ready = payouts.get(firstBatch);
        assertThat(ready.dispatch().problems()).isEmpty();
        assertThat(ready.actions()).contains("DISPATCH", "PAY");

        RequestActor previous = actor;
        try {
            actor = new RequestActor(COMPANY, "u-2", "Consulta", Set.of("COMISIONES.VER"));
            assertThatThrownBy(() -> dispatch.dispatch(firstBatch)).isInstanceOf(ForbiddenException.class);
            assertThat(payouts.get(firstBatch).actions()).isEmpty();
        } finally {
            actor = previous;
        }
        provider.configured = false;
        try {
            assertThat(payouts.get(firstBatch).actions()).doesNotContain("DISPATCH");
            assertThatThrownBy(() -> dispatch.dispatch(firstBatch)).hasMessageContaining("no está configurada");
        } finally {
            provider.configured = true;
        }
    }

    @Test @Order(2)
    void uncertainDispatchBlocksManualPaymentAndResolvesBySync() {
        provider.failNext = new PayoutDispatcher.DispatchException("timeout", true);
        BatchDetailDto sent = dispatch.dispatch(firstBatch);
        assertThat(sent.dispatch().status()).isEqualTo("SENT");
        assertThat(sent.dispatch().providerBatchId()).isNull();
        assertThat(sent.dispatch().detail()).contains("incierto");
        assertThat(sent.actions()).containsExactly("EXPORT", "SYNC");
        PayoutDispatcher.DispatchRequest request = provider.sent.get(0);
        assertThat(request.reference()).isEqualTo(sent.summary().number());
        assertThat(request.idempotencyKey()).isEqualTo("PAYOUT-" + firstBatch + "-" + sent.summary().number());
        assertThat(request.items()).hasSize(2);
        assertThat(request.items()).anySatisfy(i -> {
            assertThat(i.amountInCents()).isEqualTo(10_000_000L);
            assertThat(i.bankId()).isEqualTo("w-bancolombia");
            assertThat(i.accountType()).isEqualTo("AHORROS");
            assertThat(i.email()).isEqualTo("pagos@alfa.co");
        });
        assertThat(request.items()).anySatisfy(i -> {
            assertThat(i.key()).isEqualTo("3001234567");
            assertThat(i.bankId()).isNull();
        });
        long alphaItem = itemOf(sent, alpha);
        long betaItem = itemOf(sent, beta);
        assertThatThrownBy(() -> payouts.pay(firstBatch, alphaItem, new PayRequest("TRF-1", null, null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("dispersión");
        assertThatThrownBy(() -> dispatch.dispatch(firstBatch)).isInstanceOf(ConflictException.class);

        // El proveedor sí creó el lote: se encuentra por referencia; Alfa aprobado, Beta aún procesando.
        provider.byReference = Optional.of("po-1");
        String alphaRef = request.items().stream().filter(i -> i.key() == null).findFirst().orElseThrow().reference();
        String betaRef = request.items().stream().filter(i -> i.key() != null).findFirst().orElseThrow().reference();
        provider.statuses = List.of(new PayoutDispatcher.TxStatus(alphaRef, "tx-a", "APPROVED", null),
                new PayoutDispatcher.TxStatus(betaRef, "tx-b", "PROCESSING", null));
        BatchDetailDto synced = dispatch.sync(firstBatch);
        assertThat(synced.dispatch().providerBatchId()).isEqualTo("po-1");
        assertThat(synced.dispatch().status()).isEqualTo("SENT");
        assertThat(synced.items()).filteredOn(i -> i.id() == alphaItem).singleElement().satisfies(i -> {
            assertThat(i.status()).isEqualTo("PAID");
            assertThat(i.reference()).isEqualTo("tx-a");
            assertThat(i.providerStatus()).isEqualTo("APPROVED");
        });
        assertThat(balance(alpha)).isEqualByComparingTo("0");
        // Repetir la sincronización no asienta dos veces.
        dispatch.sync(firstBatch);
        assertThat(balance(alpha)).isEqualByComparingTo("0");

        // Aviso del proveedor: solo dispara la consulta (un id desconocido se ignora).
        provider.statuses = List.of(new PayoutDispatcher.TxStatus(alphaRef, "tx-a", "APPROVED", null),
                new PayoutDispatcher.TxStatus(betaRef, "tx-b", "FAILED", "Llave inexistente"));
        dispatch.onProviderEvent(List.of("desconocido", "po-1"));
        BatchDetailDto done = payouts.get(firstBatch);
        assertThat(done.dispatch().status()).isEqualTo("DONE");
        assertThat(done.summary().status()).isEqualTo("CLOSED");
        assertThat(done.items()).filteredOn(i -> i.id() == betaItem).singleElement().satisfies(i -> {
            assertThat(i.status()).isEqualTo("REJECTED");
            assertThat(i.note()).contains("Llave inexistente");
            assertThat(i.providerDetail()).isEqualTo("Llave inexistente");
        });
        assertThat(balance(beta)).isEqualByComparingTo("-100000");
    }

    @Test @Order(3)
    void definiteFailureAllowsResendWithNewKey() {
        BatchDetailDto batch = payouts.create(new CreateBatchRequest(List.of(new CreateItem(beta, null)), null));
        long id = batch.summary().id();
        provider.sent.clear();
        provider.byReference = Optional.empty();
        provider.failNext = new PayoutDispatcher.DispatchException("El proveedor rechazó el lote (400)", false);
        BatchDetailDto failed = dispatch.dispatch(id);
        assertThat(failed.dispatch().status()).isEqualTo("FAILED");
        assertThat(failed.actions()).contains("PAY", "DISPATCH");
        clock.now = clock.now.plusSeconds(90);
        provider.payoutId = "po-2";
        BatchDetailDto resent = dispatch.dispatch(id);
        assertThat(resent.dispatch().status()).isEqualTo("SENT");
        assertThat(resent.dispatch().providerBatchId()).isEqualTo("po-2");
        assertThat(provider.sent).hasSize(2);
        assertThat(provider.sent.get(1).idempotencyKey()).isNotEqualTo(provider.sent.get(0).idempotencyKey())
                .matches("[A-Za-z0-9-]{1,64}");

        provider.statuses = List.of(new PayoutDispatcher.TxStatus(provider.sent.get(1).items().get(0).reference(), "tx-c", "APPROVED", null));
        assertThat(dispatch.syncAll()).isEqualTo(1);
        assertThat(payouts.get(id).summary().status()).isEqualTo("CLOSED");
        assertThat(balance(beta)).isEqualByComparingTo("0");
    }

    @Test @Order(4)
    void lostDispatchFailsAfterWaiting() {
        post(gamma, "COMMISSION", "1", "COM-DIT:1");
        jdbc.update("UPDATE mp_sellers SET email = 'gama@x.co' WHERE id = :s", new MapSqlParameterSource("s", gamma));
        BatchDetailDto batch = payouts.create(new CreateBatchRequest(List.of(new CreateItem(gamma, null)), null));
        long id = batch.summary().id();
        provider.byReference = Optional.empty();
        provider.failNext = new IllegalStateException("bug inesperado");
        assertThat(dispatch.dispatch(id).dispatch().status()).isEqualTo("SENT");
        dispatch.sync(id);
        assertThat(payouts.get(id).dispatch().status()).isEqualTo("SENT");
        clock.now = clock.now.plus(Duration.ofMinutes(31));
        assertThat(dispatch.sync(id).dispatch().status()).isEqualTo("FAILED");
        assertThat(payouts.get(id).actions()).contains("PAY", "REJECT", "CANCEL", "DISPATCH");
    }
}
