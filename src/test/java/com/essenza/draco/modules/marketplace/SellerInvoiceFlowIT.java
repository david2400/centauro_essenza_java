package com.essenza.draco.modules.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.essenza.draco.modules.marketplace.application.dto.SellerInvoiceDtos.IssueRequest;
import com.essenza.draco.modules.marketplace.application.dto.SellerInvoiceDtos.SellerPeriodDto;
import com.essenza.draco.modules.marketplace.application.port.SellerLedgerStore;
import com.essenza.draco.modules.marketplace.application.service.SellerInvoiceService;
import com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.SellerInvoiceJdbcStore;
import com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.SellerLedgerJdbcStore;
import com.essenza.draco.shared.common.billing.ServiceInvoicing;
import com.essenza.draco.shared.exceptions.ForbiddenException;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Factura mensual al vendedor contra MySQL/MariaDB: conceptos del periodo con el JDBC real; la emisión
 * electrónica se simula (en la app la hace facturación con su numeración y proveedor). Solo *_test.
 */
@EnabledIfEnvironmentVariable(named = "MARKETPLACE_IT_DB_URL", matches = "jdbc:.*_test(\\?.*)?")
class SellerInvoiceFlowIT {

    static final long COMPANY = 2;
    static RequestActor actor = new RequestActor(COMPANY, "u-1", "Contabilidad", Set.of("COMISIONES.VER", "COMISIONES.GESTIONAR"));
    static NamedParameterJdbcTemplate jdbc;
    static SellerLedgerJdbcStore ledger;
    static SellerInvoiceService service;
    static long vendor, noEmail;
    static final List<ServiceInvoicing.Request> SENT = new ArrayList<>();

    /** Facturación simulada: una factura vigente por origen. */
    static final ServiceInvoicing FAKE = new ServiceInvoicing() {
        final Map<String, Result> bySource = new HashMap<>();
        long next = 900;

        @Override
        public Optional<Result> active(String type, String key) {
            return Optional.ofNullable(bySource.get(type + key));
        }

        @Override
        public Result issue(Request request) {
            Result existing = bySource.get(request.sourceType() + request.sourceKey());
            if (existing != null) {
                return new Result(existing.invoiceId(), existing.number(), existing.status(), existing.net(), existing.tax(),
                        existing.total(), null, false);
            }
            SENT.add(request);
            BigDecimal total = request.lines().stream().map(Line::total).reduce(BigDecimal.ZERO, BigDecimal::add);
            long id = next++;
            Result result = new Result(id, "SETT" + id, "ISSUED", total, BigDecimal.ZERO, total, null, true);
            bySource.put(request.sourceType() + request.sourceKey(), result);
            return result;
        }

        @Override
        public List<Result> status(List<Long> ids) {
            return bySource.values().stream().filter(r -> ids.contains(r.invoiceId())).toList();
        }
    };

    @BeforeAll
    static void setUp() {
        String url = System.getenv("MARKETPLACE_IT_DB_URL");
        assertThat(url).matches("jdbc:.*_test(\\?.*)?");
        jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(url, System.getenv("MARKETPLACE_IT_DB_USER"),
                System.getenv().getOrDefault("MARKETPLACE_IT_DB_PASSWORD", "")));
        clean();
        ledger = new SellerLedgerJdbcStore(jdbc);
        service = new SellerInvoiceService(new SellerInvoiceJdbcStore(jdbc), FAKE, () -> actor,
                Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneId.of("America/Bogota")), "");
        vendor = seller("Moda Andina SAS", "ventas@moda.co");
        noEmail = seller("Sin Correo SAS", null);
        post(vendor, "COMMISSION", "50000", "2026-10-05", null, "INV-IT:1");
        post(vendor, "COMMISSION", "30000", "2026-10-20", null, "INV-IT:2");
        post(vendor, "COMMISSION_REVERSAL", "-10000", "2026-10-25", null, "INV-IT:3");
        post(vendor, "COMMISSION", "99999", "2026-11-02", null, "INV-IT:4");           // otro mes
        post(vendor, "MONTHLY_FEE", "119000", "2026-11-01", "2026-10", "INV-IT:5");    // ciclo octubre, asentado en noviembre
        post(vendor, "STORAGE_FEE", "8000", "2026-10-31", "2026-10", "INV-IT:6");
        post(vendor, "DISPUTE_CHARGE", "15000", "2026-10-28", null, "INV-IT:7");
        post(vendor, "MARKETPLACE_SALE", "-400000", "2026-10-05", null, "INV-IT:8");   // recaudo: no se factura
        post(noEmail, "COMMISSION", "20000", "2026-10-10", null, "INV-IT:9");
    }

    @AfterAll
    static void tearDown() {
        clean();
    }

    static void clean() {
        jdbc.update("DELETE FROM mp_seller_invoices WHERE created_by = 'u-1'", new MapSqlParameterSource());
        jdbc.update("DELETE FROM mp_seller_ledger WHERE idempotency_key LIKE 'INV-IT:%'", new MapSqlParameterSource());
        jdbc.update("DELETE FROM mp_sellers WHERE created_by = 'it-inv'", new MapSqlParameterSource());
    }

    static long seller(String name, String email) {
        jdbc.update("""
                INSERT INTO mp_sellers (company_id, kind, legal_name, trade_name, doc_type, doc_number, email, status, created_at,
                  created_by, updated_at, updated_by)
                VALUES (:c, 'EXTERNAL', :n, :n, 'NIT', CONCAT('902', FLOOR(RAND() * 1000000)), :e, 'APPROVED', NOW(6), 'it-inv', NOW(6), 'it')""",
                new MapSqlParameterSource("c", COMPANY).addValue("n", name).addValue("e", email));
        return jdbc.queryForObject("SELECT MAX(id) FROM mp_sellers", new MapSqlParameterSource(), Long.class);
    }

    static void post(long seller, String type, String amount, String date, String cycle, String key) {
        ledger.post(COMPANY, new SellerLedgerStore.NewEntry(seller, type, new BigDecimal(amount), "COP", LocalDate.parse(date), null,
                BigDecimal.ZERO, "IT", null, cycle, key, null), "it", "it", Instant.now());
    }

    static SellerPeriodDto row(long seller) {
        return service.preview("2026-10").sellers().stream().filter(s -> s.sellerId() == seller).findFirst().orElse(null);
    }

    @Test
    void monthlyInvoicePerSeller() {
        SellerPeriodDto preview = row(vendor);
        assertThat(preview.concepts()).extracting(c -> c.code()).containsExactly("COMMISSIONS", "MONTHLY_FEE", "STORAGE", "DISPUTES");
        assertThat(preview.concepts().get(0).total()).isEqualByComparingTo("70000"); // 50.000 + 30.000 − 10.000
        assertThat(preview.concepts().get(0).description()).contains("2 ventas");
        assertThat(preview.total()).isEqualByComparingTo("212000");
        assertThat(preview.collected()).isEqualByComparingTo("400000");
        assertThat(preview.invoice()).isNull();
        assertThat(row(noEmail).missing()).contains("correo (para enviarle la factura)");

        assertThatThrownBy(() -> service.issue(new IssueRequest("2026-11", null))).hasMessageContaining("meses cerrados");
        var result = service.issue(new IssueRequest("2026-10", List.of(vendor, noEmail)));
        assertThat(result.issued()).isEqualTo(1);
        assertThat(result.errors()).singleElement().asString().contains("Sin Correo SAS");
        assertThat(SENT).singleElement().satisfies(r -> {
            assertThat(r.sourceKey()).isEqualTo(COMPANY + ":" + vendor + ":2026-10");
            assertThat(r.buyer().docType()).isEqualTo("NIT");
            assertThat(r.buyer().email()).isEqualTo("ventas@moda.co");
        });
        assertThat(row(vendor).invoice().status()).isEqualTo("ISSUED");
        assertThat(row(vendor).invoice().total()).isEqualByComparingTo("212000");

        var again = service.issue(new IssueRequest("2026-10", List.of(vendor)));
        assertThat(again.issued()).isZero();
        assertThat(again.alreadyInvoiced()).isEqualTo(1);
        assertThat(SENT).hasSize(1);

        RequestActor previous = actor;
        try {
            actor = new RequestActor(COMPANY, "u-2", "Consulta", Set.of("COMISIONES.VER"));
            assertThat(service.preview("2026-10").invoiced()).isEqualTo(1);
            assertThatThrownBy(() -> service.issue(new IssueRequest("2026-10", null))).isInstanceOf(ForbiddenException.class);
            actor = new RequestActor(3, "u-9", "Otra", Set.of("COMISIONES.VER"));
            assertThat(service.preview("2026-10").sellers()).isEmpty();
        } finally {
            actor = previous;
        }
        assertThatThrownBy(() -> service.preview("octubre")).hasMessageContaining("AAAA-MM");
    }
}
