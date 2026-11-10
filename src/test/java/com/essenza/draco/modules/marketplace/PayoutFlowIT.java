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

import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.BatchDetailDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.CreateBatchRequest;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.CreateItem;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.PayRequest;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.PayeeDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.ReasonRequest;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.SaveBankAccountRequest;
import com.essenza.draco.modules.marketplace.application.port.SellerLedgerStore;
import com.essenza.draco.modules.marketplace.application.service.PayoutService;
import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.PayoutJdbcStore;
import com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.SellerLedgerJdbcStore;
import com.essenza.draco.shared.common.web.PageableFactory;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.ForbiddenException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.RequestActor;

/** Pagos a vendedores por lote contra MySQL/MariaDB con los adaptadores JDBC reales. Solo *_test. */
@EnabledIfEnvironmentVariable(named = "MARKETPLACE_IT_DB_URL", matches = "jdbc:.*_test(\\?.*)?")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PayoutFlowIT {

    static final long COMPANY = 2;
    static final RequestActor MANAGER = new RequestActor(COMPANY, "u-1", "Tesorería", Set.of("COMISIONES.VER", "COMISIONES.GESTIONAR"));
    static RequestActor actor = MANAGER;
    static NamedParameterJdbcTemplate jdbc;
    static SellerLedgerJdbcStore ledger;
    static PayoutService payouts;
    static long vendor, noBank, foreign, deliveredLine, pendingOrder, pendingLine, batchId;

    @BeforeAll
    static void setUp() {
        String url = System.getenv("MARKETPLACE_IT_DB_URL");
        assertThat(url).matches("jdbc:.*_test(\\?.*)?");
        jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(url, System.getenv("MARKETPLACE_IT_DB_USER"),
                System.getenv().getOrDefault("MARKETPLACE_IT_DB_PASSWORD", "")));
        clean();
        ledger = new SellerLedgerJdbcStore(jdbc);
        // Hoy 9-nov, retención de 7 días: se liberan las órdenes entregadas hasta el 1-nov.
        payouts = new PayoutService(new PayoutJdbcStore(jdbc), ledger, () -> actor,
                Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneId.of("America/Bogota")), 7);
        vendor = seller(COMPANY, "Moda Andina");
        noBank = seller(COMPANY, "Sin Cuenta");
        foreign = seller(3, "Otra empresa");
        long delivered = order("PAY-IT-1", "DELIVERED", vendor, "2026-10-20 10:00:00");
        deliveredLine = line(delivered);
        pendingOrder = order("PAY-IT-2", "PAID", vendor, null);
        pendingLine = line(pendingOrder);
        long other = line(order("PAY-IT-3", "DELIVERED", noBank, "2026-10-01 10:00:00"));
        post(vendor, "MARKETPLACE_SALE", "-100000", "MP-SALE:" + deliveredLine);
        post(vendor, "MARKETPLACE_SALE", "-50000", "MP-SALE:" + pendingLine);
        post(vendor, "COMMISSION", "10000", "COM-IT:1");
        post(noBank, "MARKETPLACE_SALE", "-30000", "MP-SALE:" + other);
    }

    @AfterAll
    static void tearDown() {
        clean();
    }

    static void clean() {
        jdbc.update("DELETE FROM mp_payout_formats WHERE name LIKE 'Banco pagador IT%'", new MapSqlParameterSource());
        for (String t : List.of("mp_payout_items", "mp_payout_batches", "mp_seller_bank_accounts")) {
            jdbc.update("DELETE FROM " + t, new MapSqlParameterSource());
        }
        jdbc.update("DELETE FROM mp_seller_ledger WHERE idempotency_key LIKE 'MP-SALE:%' OR idempotency_key LIKE 'COM-IT:%'"
                + " OR idempotency_key LIKE 'PAYOUT:%' OR idempotency_key LIKE 'MP-CANCEL:%'", new MapSqlParameterSource());
        jdbc.update("DELETE FROM product_orders WHERE order_id IN (SELECT id_order FROM orders WHERE order_number LIKE 'PAY-IT-%')",
                new MapSqlParameterSource());
        jdbc.update("DELETE FROM orders WHERE order_number LIKE 'PAY-IT-%'", new MapSqlParameterSource());
        jdbc.update("DELETE FROM mp_sellers WHERE legal_name IN ('Moda Andina', 'Sin Cuenta', 'Otra empresa') AND created_by = 'it-pay'",
                new MapSqlParameterSource());
    }

    static long seller(long company, String name) {
        jdbc.update("""
                INSERT INTO mp_sellers (company_id, kind, legal_name, trade_name, doc_type, doc_number, status, created_at,
                  created_by, updated_at, updated_by)
                VALUES (:c, 'EXTERNAL', :n, :n, 'NIT', CONCAT('901', FLOOR(RAND() * 1000000)), 'APPROVED', NOW(6), 'it-pay', NOW(6), 'it')""",
                new MapSqlParameterSource("c", company).addValue("n", name));
        return jdbc.queryForObject("SELECT MAX(id) FROM mp_sellers", new MapSqlParameterSource(), Long.class);
    }

    static long order(String number, String state, long seller, String deliveredAt) {
        jdbc.update("""
                INSERT INTO orders (order_number, state, total, stock_managed, seller_id, customer_name, channel, created_at, delivered_at)
                VALUES (:n, :s, 100000, 1, :seller, 'Ana', 'ONLINE', NOW(6), :d)""",
                new MapSqlParameterSource("n", number).addValue("s", state).addValue("seller", seller).addValue("d", deliveredAt));
        return jdbc.queryForObject("SELECT MAX(id_order) FROM orders", new MapSqlParameterSource(), Long.class);
    }

    static long line(long order) {
        jdbc.update("INSERT INTO product_orders (order_id, quantity, total) VALUES (:o, 1, 100000)", new MapSqlParameterSource("o", order));
        return jdbc.queryForObject("SELECT MAX(id_product_order) FROM product_orders", new MapSqlParameterSource(), Long.class);
    }

    static void post(long seller, String type, String amount, String key) {
        ledger.post(COMPANY, new SellerLedgerStore.NewEntry(seller, type, new BigDecimal(amount), "COP", LocalDate.of(2026, 11, 1), null,
                BigDecimal.ZERO, "IT", null, null, key, null), "it", "it", Instant.now());
    }

    static long bankId(String code) {
        return jdbc.queryForObject("SELECT id FROM mp_banks WHERE code = :c", new MapSqlParameterSource("c", code), Long.class);
    }

    static com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.ColumnDto col(String field, int width, String align, String pad,
                                                                                         String value) {
        return new com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.ColumnDto(field, null, width, align, pad, value);
    }

    static PayeeDto payee(long seller) {
        return payouts.preview().payees().stream().filter(p -> p.sellerId() == seller).findFirst().orElse(null);
    }

    static BigDecimal balance(long seller) {
        return ledger.balances(COMPANY).get(seller);
    }

    @Test @Order(1)
    void previewHoldsUndeliveredOrders() {
        PayeeDto p = payee(vendor);
        assertThat(p.owed()).isEqualByComparingTo("140000");   // 150.000 abonados − 10.000 de comisión
        assertThat(p.held()).isEqualByComparingTo("50000");    // orden pagada, aún sin entregar
        assertThat(p.payable()).isEqualByComparingTo("90000");
        assertThat(p.bankAccount()).isNull();
        assertThat(payee(noBank).payable()).isEqualByComparingTo("30000");
        assertThat(payee(foreign)).isNull();
        assertThat(payouts.preview().holdDays()).isEqualTo(7);
    }

    @Test @Order(2)
    void bankAccountIsValidatedAndMaskedForViewers() {
        long bancolombia = bankId("BANCOLOMBIA");
        assertThatThrownBy(() -> payouts.saveBankAccount(vendor, new SaveBankAccountRequest(bancolombia, "SAVINGS", "12", null, "Moda", "NIT",
                "900123"))).isInstanceOf(MarketplaceRuleViolation.class).hasMessageContaining("dígitos");
        assertThatThrownBy(() -> payouts.saveBankAccount(vendor, new SaveBankAccountRequest(bankId("NEQUI"), "SAVINGS", "1234567", null,
                "Moda", "NIT", "900123"))).hasMessageContaining("no admite");
        assertThatThrownBy(() -> payouts.saveBankAccount(vendor, new SaveBankAccountRequest(999999L, "SAVINGS", "1234567", null,
                "Moda", "NIT", "900123"))).isInstanceOf(NotFoundException.class);
        // Llave Bre-B sin entidad: sirve para cualquier banco o billetera participante.
        var key = payouts.saveBankAccount(vendor, new SaveBankAccountRequest(null, "BRE_B_KEY", "@modaandina", "ALPHANUMERIC", "Moda",
                "NIT", "900123456"));
        assertThat(key.bankName()).isEqualTo("Bre-B");
        assertThat(key.keyType()).isEqualTo("ALPHANUMERIC");
        var saved = payouts.saveBankAccount(vendor, new SaveBankAccountRequest(bancolombia, "savings", "123-456-78901", null,
                "=Moda Andina SAS", "NIT", "900123456"));
        assertThat(saved.accountNumber()).isEqualTo("12345678901");
        assertThat(saved.bankId()).isEqualTo(bancolombia);
        assertThat(saved.keyType()).isNull();
        assertThatThrownBy(() -> payouts.saveBankAccount(foreign, new SaveBankAccountRequest(bancolombia, "SAVINGS", "1234567", null, "X",
                "CC", "1234"))).isInstanceOf(NotFoundException.class);
        RequestActor previous = actor;
        try {
            actor = new RequestActor(COMPANY, "u-2", "Consulta", Set.of("COMISIONES.VER"));
            assertThat(payouts.bankAccount(vendor).accountNumber()).isEqualTo("••••8901");
            assertThat(payouts.bankAccount(vendor).masked()).isTrue();
            assertThatThrownBy(() -> payouts.saveBankAccount(vendor, new SaveBankAccountRequest(null, "BRE_B_KEY", "3001234567", "PHONE",
                    "X", "CC", "1234"))).isInstanceOf(ForbiddenException.class);
            assertThat(payouts.banks(true)).allMatch(b -> b.active()); // sin GESTIONAR no ve las inactivas
            assertThatThrownBy(() -> payouts.createBank(new com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.SaveBankRequest(
                    null, "Banco X", "BANK", null, null, null, null))).isInstanceOf(ForbiddenException.class);
        } finally {
            actor = previous;
        }
    }

    @Test @Order(3)
    void createBatchAndPay() {
        assertThatThrownBy(() -> payouts.create(new CreateBatchRequest(List.of(new CreateItem(noBank, null)), null)))
                .hasMessageContaining("cuenta bancaria");
        assertThatThrownBy(() -> payouts.create(new CreateBatchRequest(List.of(new CreateItem(vendor, new BigDecimal("90001"))), null)))
                .hasMessageContaining("máximo");
        BatchDetailDto batch = payouts.create(new CreateBatchRequest(List.of(new CreateItem(vendor, null)), "Corte semanal"));
        batchId = batch.summary().id();
        assertThat(batch.summary().number()).startsWith("LIQ-");
        assertThat(batch.summary().status()).isEqualTo("OPEN");
        assertThat(batch.items()).singleElement().satisfies(i -> {
            assertThat(i.amount()).isEqualByComparingTo("90000");
            assertThat(i.accountNumber()).isEqualTo("12345678901");
        });
        assertThat(batch.actions()).containsExactly("PAY", "REJECT", "CANCEL", "EXPORT");
        assertThatThrownBy(() -> payouts.create(new CreateBatchRequest(List.of(new CreateItem(vendor, null)), null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("pendiente");
        assertThat(payee(vendor).pendingBatchNumber()).isEqualTo(batch.summary().number());

        // Formato del banco pagador: ancho fijo, ISO-8859-1, encabezado con totales y códigos propios.
        var fixed = new com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.LayoutDto("FIXED", null, false, "CRLF", 2, "",
                "yyyyMMdd", true, java.util.Map.of("SAVINGS", "37"), java.util.Map.of(), java.util.Map.of("NIT", "3"),
                List.of(col("CONSTANT", 1, "LEFT", " ", "1"), col("PAYMENT_DATE", 8, "LEFT", " ", null), col("COUNT", 3, "RIGHT", "0", null),
                        col("TOTAL_AMOUNT", 12, "RIGHT", "0", null)),
                List.of(col("HOLDER_DOC_TYPE", 1, "LEFT", " ", null), col("ACCOUNT_TYPE", 2, "LEFT", " ", null),
                        col("ACCOUNT_NUMBER", 15, "RIGHT", "0", null), col("AMOUNT", 12, "RIGHT", "0", null), col("HOLDER_NAME", 20, "LEFT", " ", null)),
                List.of());
        assertThat(payouts.previewFormat(fixed).content()).contains("1250000").startsWith("1");
        var format = payouts.saveFormat(null, new com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.SaveFormatRequest(
                "Banco pagador IT", "txt", "ISO-8859-1", null, fixed));
        assertThat(payouts.formats(false)).extracting(f -> f.name()).containsExactly("Genérico (CSV)", "Banco pagador IT");
        assertThatThrownBy(() -> payouts.saveFormat(null, new com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.SaveFormatRequest(
                "banco pagador it", "txt", "UTF-8", null, fixed))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> payouts.saveFormat(0L, new com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.SaveFormatRequest(
                "X", "txt", "UTF-8", null, fixed))).hasMessageContaining("genérico");
        var file = payouts.export(batchId, format.id(), java.time.LocalDate.of(2026, 11, 10));
        assertThat(file.fileName()).isEqualTo(batch.summary().number() + "-banco_pagador_it.txt");
        String text = new String(file.content(), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(text.split("\r\n")).containsExactly("120261110001000009000000",
                "337000012345678901000009000000=Moda Andina SAS    ");
        jdbc.update("DELETE FROM mp_payout_formats WHERE name = 'Banco pagador IT'", new MapSqlParameterSource());

        String csv = payouts.exportCsv(batchId);
        assertThat(csv).contains("codigo_banco").contains("Bancolombia").contains("12345678901").contains(batch.summary().number()).contains("'=Moda Andina SAS").contains("90000.00");

        long item = batch.items().get(0).id();
        assertThatThrownBy(() -> payouts.pay(batchId, item, new PayRequest(" ", null, null))).hasMessageContaining("referencia");
        BatchDetailDto paid = payouts.pay(batchId, item, new PayRequest("TRF-778", null, null));
        assertThat(paid.summary().status()).isEqualTo("CLOSED");
        assertThat(paid.items().get(0).status()).isEqualTo("PAID");
        assertThat(paid.actions()).containsExactly("EXPORT");
        assertThat(balance(vendor)).isEqualByComparingTo("-50000");
        assertThat(payee(vendor).payable()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> payouts.pay(batchId, item, new PayRequest("TRF-778", null, null))).hasMessageContaining("cerrado");
    }

    @Test @Order(4)
    void cancelAndBalanceChangedConflict() {
        // La orden retenida se entrega hace más de 7 días: se libera.
        jdbc.update("UPDATE orders SET state = 'DELIVERED', delivered_at = '2026-10-25 09:00:00' WHERE id_order = :o",
                new MapSqlParameterSource("o", pendingOrder));
        assertThat(payee(vendor).payable()).isEqualByComparingTo("50000");
        BatchDetailDto batch = payouts.create(new CreateBatchRequest(List.of(new CreateItem(vendor, null)), null));
        assertThatThrownBy(() -> payouts.cancel(batch.summary().id(), new ReasonRequest("no"))).hasMessageContaining("motivo");
        BatchDetailDto cancelled = payouts.cancel(batch.summary().id(), new ReasonRequest("Se pagará con el próximo corte"));
        assertThat(cancelled.summary().status()).isEqualTo("CANCELLED");
        assertThat(cancelled.items()).extracting(i -> i.status()).containsOnly("REJECTED");

        BatchDetailDto again = payouts.create(new CreateBatchRequest(List.of(new CreateItem(vendor, null)), null));
        post(vendor, "MARKETPLACE_REVERSAL", "30000", "MP-CANCEL:" + pendingLine); // cancelación después de armar el lote
        long item = again.items().get(0).id();
        assertThatThrownBy(() -> payouts.payAll(again.summary().id(), new PayRequest("TRF-1", null, null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("hoy se le deben 20000");
        BatchDetailDto rejected = payouts.reject(again.summary().id(), item, new ReasonRequest("Saldo cambió por cancelación"));
        assertThat(rejected.summary().status()).isEqualTo("CANCELLED");
        assertThat(balance(vendor)).isEqualByComparingTo("-20000");
        assertThat(payouts.search("CLOSED", PageableFactory.of(0, 10, null, Set.of("createdAt"), Sort.by(Sort.Order.desc("createdAt"))))
                .getContent()).extracting(b -> b.id()).containsExactly(batchId);
    }

    @Test @Order(6)
    void bankCatalogCanGrowAndBeDisabled() {
        var banks = payouts.banks(false);
        assertThat(banks).extracting(b -> b.code()).contains("BANCOLOMBIA", "NU", "LULO", "NEQUI", "DAVIPLATA", "MOVII");
        assertThat(banks.stream().filter(b -> b.code().equals("NEQUI")).findFirst().orElseThrow().accountTypes())
                .containsExactly("BRE_B_KEY", "DIGITAL_WALLET");
        var created = payouts.createBank(new com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.SaveBankRequest(
                null, "Cooperativa Pruebas Ñandú", "COOPERATIVE", "9999", null, 200, null));
        assertThat(created.code()).isEqualTo("COOPERATIVA_PRUEBAS_NANDU");
        assertThatThrownBy(() -> payouts.createBank(new com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.SaveBankRequest(
                null, "Cooperativa Pruebas Ñandú", "COOPERATIVE", null, null, null, null))).isInstanceOf(ConflictException.class);
        var disabled = payouts.updateBank(created.id(), new com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.SaveBankRequest(
                null, null, null, "", false, null, "wompi-uuid-1"));
        assertThat(disabled.active()).isFalse();
        assertThat(disabled.achCode()).isNull();
        assertThat(disabled.wompiBankId()).isEqualTo("wompi-uuid-1");
        assertThatThrownBy(() -> payouts.updateBank(created.id(), new com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.SaveBankRequest(
                null, null, null, null, null, null, "x y"))).hasMessageContaining("id de la entidad");
        assertThat(payouts.banks(false)).extracting(b -> b.id()).doesNotContain(created.id());
        assertThat(payouts.banks(true)).extracting(b -> b.id()).contains(created.id());
        assertThatThrownBy(() -> payouts.saveBankAccount(vendor, new SaveBankAccountRequest(created.id(), "SAVINGS", "1234567", null, "Moda",
                "NIT", "900123456"))).hasMessageContaining("no está activo");
        jdbc.update("DELETE FROM mp_banks WHERE id = :id", new MapSqlParameterSource("id", created.id()));
    }

    @Test @Order(5)
    void permissionsAndIsolation() {
        RequestActor previous = actor;
        try {
            actor = new RequestActor(COMPANY, "u-2", "Consulta", Set.of("COMISIONES.VER"));
            BatchDetailDto view = payouts.get(batchId);
            assertThat(view.items().get(0).accountNumber()).isEqualTo("••••8901");
            assertThat(view.actions()).isEmpty();
            assertThatThrownBy(() -> payouts.exportCsv(batchId)).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> payouts.create(new CreateBatchRequest(List.of(new CreateItem(vendor, null)), null)))
                    .isInstanceOf(ForbiddenException.class);
            actor = new RequestActor(3, "u-9", "Otra", MANAGER.permissions());
            assertThatThrownBy(() -> payouts.get(batchId)).isInstanceOf(NotFoundException.class);
            actor = new RequestActor(COMPANY, "u-3", "Nadie", Set.of());
            assertThatThrownBy(() -> payouts.preview()).isInstanceOf(ForbiddenException.class);
        } finally {
            actor = previous;
        }
    }
}
