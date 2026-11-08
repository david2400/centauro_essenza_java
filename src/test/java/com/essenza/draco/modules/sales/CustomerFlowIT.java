package com.essenza.draco.modules.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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

import com.essenza.draco.modules.sales.application.dto.customer.CustomerDtos.CustomerDetailDto;
import com.essenza.draco.modules.sales.application.dto.customer.CustomerDtos.CustomerSummaryDto;
import com.essenza.draco.modules.sales.application.dto.customer.CustomerDtos.SaveCustomerRequest;
import com.essenza.draco.modules.sales.application.services.customer.CustomerService;
import com.essenza.draco.modules.sales.application.services.customer.CustomerSync;
import com.essenza.draco.modules.sales.domain.model.order.CustomerInfo;
import com.essenza.draco.modules.sales.infrastructure.outbound.repositories.customer.CustomerJdbcAdapter;
import com.essenza.draco.shared.common.web.PageableFactory;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.ForbiddenException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Clientes y ficha 360 contra MySQL/MariaDB con el adaptador JDBC real. Solo contra una base
 * *_test (borra customers, órdenes y líneas de prueba).
 */
@EnabledIfEnvironmentVariable(named = "MARKETPLACE_IT_DB_URL", matches = "jdbc:.*_test(\\?.*)?")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CustomerFlowIT {

    static final Set<String> MANAGE = Set.of("VENTAS.VER", "VENTAS.GESTIONAR");
    static RequestActor actor = new RequestActor(2, "u-1", "demo", MANAGE);
    static NamedParameterJdbcTemplate jdbc;
    static CustomerService customers;

    @BeforeAll
    static void setUp() {
        String url = System.getenv("MARKETPLACE_IT_DB_URL");
        assertThat(url).matches("jdbc:.*_test(\\?.*)?");
        jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(url, System.getenv("MARKETPLACE_IT_DB_USER"),
                System.getenv().getOrDefault("MARKETPLACE_IT_DB_PASSWORD", "")));
        // Posventa de las órdenes de prueba (si las tablas existen en esta base).
        for (String sql : List.of("DELETE FROM invoice_lines", "UPDATE invoices SET related_invoice_id = NULL", "DELETE FROM invoices",
                "DELETE FROM order_devolution_evidences", "DELETE FROM order_devolution_details", "DELETE FROM order_devolutions")) {
            try {
                jdbc.update(sql, new MapSqlParameterSource());
            } catch (org.springframework.jdbc.BadSqlGrammarException missingTable) {
                // tabla ausente en esta base de prueba
            }
        }
        for (String t : List.of("customers", "mp_seller_ledger", "mp_commission_adjustments", "mp_commissions",
                "mp_offer_reservations", "product_orders", "orders")) {
            jdbc.update("DELETE FROM " + t, new MapSqlParameterSource());
        }
        customers = new CustomerService(new CustomerJdbcAdapter(jdbc), () -> actor,
                Clock.fixed(Instant.parse("2026-11-07T15:00:00Z"), ZoneId.of("America/Bogota")));
        order("CC", "1020", "Ana Pérez", "ana@x.co", "DELIVERED", "100000", "20000", "2026-10-01T15:00:00Z", "ONLINE");
        order("CC", "1020", "Ana P.", "ana.nueva@x.co", "PAID", "50000", "0", "2026-11-02T15:00:00Z", "BACKOFFICE");
        order("CC", "1020", "Ana", null, "CANCELLED", "70000", "0", "2026-11-03T15:00:00Z", "ONLINE");
        order(null, "1020", "Ana (antigua)", null, "PENDING", "30000", "0", "2026-09-01T15:00:00Z", "ONLINE");
        order("NIT", "900123", "Moda SAS", "compras@moda.co", "DELIVERED", "500000", "0", "2026-08-01T15:00:00Z", "WHATSAPP");
        order("CC", "999", null, null, "PENDING", "10000", "0", "2026-08-01T15:00:00Z", "ONLINE"); // sin nombre: no se importa
    }

    static void order(String type, String doc, String name, String email, String state, String total, String returned,
                      String at, String channel) {
        jdbc.update("""
                INSERT INTO orders (state, total, returned_amount, customer_doc_type, customer_doc_number, customer_name,
                  customer_email, customer_phone, channel, created_at, stock_managed)
                VALUES (:state, :total, :returned, :type, :doc, :name, :email, '3001234567', :channel, :at, 1)""",
                new MapSqlParameterSource("state", state).addValue("total", new BigDecimal(total))
                        .addValue("returned", new BigDecimal(returned)).addValue("type", type).addValue("doc", doc)
                        .addValue("name", name).addValue("email", email).addValue("channel", channel)
                        .addValue("at", java.time.LocalDateTime.ofInstant(Instant.parse(at), java.time.ZoneOffset.UTC)));
    }

    static List<CustomerSummaryDto> search(String q) {
        return customers.search(q, PageableFactory.of(0, 50, null, Set.of(), Sort.by(Sort.Order.desc("lastOrderAt")))).getContent();
    }

    static SaveCustomerRequest request(String doc, String name, Boolean consent, Integer lock) {
        return new SaveCustomerRequest(null, "CC", doc, name, "ana@x.co", "3001234567", "Cra 1", "Medellín", null, consent,
                null, lock);
    }

    @Test @Order(1)
    void importCreatesOneCustomerPerDocumentWithLatestData() {
        assertThat(customers.importFromOrders().created()).isEqualTo(2);
        assertThat(customers.importFromOrders().created()).isZero(); // idempotente
        List<CustomerSummaryDto> list = search(null);
        assertThat(list).extracting(CustomerSummaryDto::docNumber).containsExactly("1020", "900123");
        CustomerSummaryDto ana = list.get(0);
        assertThat(ana.name()).isEqualTo("Ana"); // la orden más reciente
        assertThat(ana.ordersCount()).isEqualTo(4); // incluye la antigua sin tipo de documento
        assertThat(ana.totalSpent()).isEqualByComparingTo("160000"); // 100.000 − 20.000 + 50.000 + 30.000 (sin canceladas)
        assertThat(search("moda")).extracting(CustomerSummaryDto::kind).containsExactly("COMPANY");
    }

    @Test @Order(2)
    void overviewSummarizesTheCustomer() {
        long id = search("1020").get(0).id();
        CustomerDetailDto detail = customers.get(id);
        var o = detail.overview();
        assertThat(o.orders()).isEqualTo(4);
        assertThat(o.activeOrders()).isEqualTo(3);
        assertThat(o.cancelled()).isEqualTo(1);
        assertThat(o.pendingOrders()).isEqualTo(1);
        assertThat(o.spent()).isEqualByComparingTo("160000");
        assertThat(o.returned()).isEqualByComparingTo("20000");
        assertThat(o.pendingAmount()).isEqualByComparingTo("30000");
        assertThat(o.averageTicket()).isEqualByComparingTo("53333.33");
        assertThat(o.daysSinceLastOrder()).isEqualTo(4);
        assertThat(o.recentOrders()).hasSize(4);
        assertThat(o.recentOrders().get(0).state()).isEqualTo("CANCELLED");
        assertThat(o.byChannel()).extracting(c -> c.channel()).containsExactly("ONLINE", "BACKOFFICE");
    }

    @Test @Order(3)
    void afterSalesDependsOnModulePermissions() {
        Long motive = firstId("SELECT MIN(id_motive_devolution) FROM motive_devolutions");
        Long resolution = firstId("SELECT MIN(id_billing_resolution) FROM billing_resolutions");
        org.junit.jupiter.api.Assumptions.assumeTrue(motive != null && resolution != null,
                "La base de prueba necesita al menos un motivo de devolución y una resolución de facturación.");
        long delivered = orderId("1020", "DELIVERED");
        long paid = orderId("1020", "PAID");
        long other = orderId("900123", "DELIVERED");
        devolution(motive, delivered, "F", "20000", "20000", "2026-10-05T15:00:00Z");
        devolution(motive, paid, "A", "15000", "0", "2026-11-04T15:00:00Z");
        devolution(motive, other, "P", "9000", "0", "2026-11-04T15:00:00Z"); // de otro cliente
        long invoice = invoice(resolution, 1, delivered, "INVOICE", null, "SETP-1", "ISSUED", "100000", "2026-10-01T16:00:00Z");
        invoice(resolution, 1, delivered, "CREDIT_NOTE", invoice, "NC-1", "ISSUED", "20000", "2026-10-06T16:00:00Z");
        invoice(resolution, 2, paid, "INVOICE", null, "SETP-2", "PENDING", "50000", "2026-11-02T16:00:00Z");

        long id = search("1020").get(0).id();
        RequestActor previous = actor;
        try {
            actor = new RequestActor(2, "u-5", "todo", Set.of("VENTAS.VER", "POSTVENTA.VER", "FACTURACION.VER"));
            var after = customers.get(id).afterSales();
            assertThat(after.returns().total()).isEqualTo(2);
            assertThat(after.returns().open()).isEqualTo(1);
            assertThat(after.returns().refunded()).isEqualByComparingTo("20000");
            assertThat(after.returns().recent()).extracting(r -> r.state()).containsExactly("A", "F");
            assertThat(after.invoices().invoices()).isEqualTo(2);
            assertThat(after.invoices().creditNotes()).isEqualTo(1);
            assertThat(after.invoices().pending()).isEqualTo(1);
            assertThat(after.invoices().invoiced()).isEqualByComparingTo("80000"); // 100.000 emitida − 20.000 nota crédito
            assertThat(after.invoices().recent()).extracting(i -> i.number()).containsExactly("SETP-2", "NC-1", "SETP-1");

            actor = new RequestActor(2, "u-2", "solo ventas", Set.of("VENTAS.VER"));
            var limited = customers.get(id).afterSales();
            assertThat(limited.returns()).isNull();
            assertThat(limited.invoices()).isNull();
            actor = new RequestActor(2, "u-4", "postventa", Set.of("VENTAS.VER", "POSTVENTA.VER"));
            assertThat(customers.get(id).afterSales().returns()).isNotNull();
            assertThat(customers.get(id).afterSales().invoices()).isNull();
        } finally {
            actor = previous;
            // Otros IT borran órdenes sin conocer la posventa: se limpia aquí.
            jdbc.update("UPDATE invoices SET related_invoice_id = NULL", new MapSqlParameterSource());
            jdbc.update("DELETE FROM invoices", new MapSqlParameterSource());
            jdbc.update("DELETE FROM order_devolutions", new MapSqlParameterSource());
        }
    }

    static long orderId(String doc, String state) {
        return jdbc.queryForObject("SELECT MIN(id_order) FROM orders WHERE customer_doc_number = :doc AND state = :state",
                new MapSqlParameterSource("doc", doc).addValue("state", state), Long.class);
    }

    static Long firstId(String sql) {
        try {
            return jdbc.queryForObject(sql, new MapSqlParameterSource(), Long.class);
        } catch (org.springframework.dao.DataAccessException missing) {
            return null;
        }
    }

    static void devolution(long motive, long orderId, String state, String requested, String refunded, String at) {
        jdbc.update("""
                INSERT INTO order_devolutions (order_id, observation, state, total_refund_amount, refunded_amount,
                  motive_devolution_id, usr_crea, created_at)
                VALUES (:order, 'prueba', :state, :requested, :refunded, :motive, 1, :at)""",
                new MapSqlParameterSource("order", orderId).addValue("state", state).addValue("motive", motive)
                        .addValue("requested", new BigDecimal(requested)).addValue("refunded", new BigDecimal(refunded))
                        .addValue("at", java.time.LocalDateTime.ofInstant(Instant.parse(at), java.time.ZoneOffset.UTC)));
    }

    static long invoice(long resolution, long number, long orderId, String kind, Long related, String fullNumber, String status,
                        String total, String at) {
        org.springframework.jdbc.support.GeneratedKeyHolder keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO invoices (kind, order_id, related_invoice_id, resolution_id, prefix, number, full_number, issued_at,
                  status, environment, buyer_doc_type, buyer_doc_number, buyer_name, net_amount, tax_amount, total, provider,
                  usr_crea)
                VALUES (:kind, :order, :related, :resolution, 'IT', :number, :fullNumber, :at, :status, 'TEST', 'CC', '1020',
                  'Ana', :total, 0, :total, 'NONE', 1)""",
                new MapSqlParameterSource("kind", kind).addValue("order", orderId).addValue("related", related)
                        .addValue("resolution", resolution).addValue("number", number).addValue("fullNumber", fullNumber).addValue("status", status).addValue("total", new BigDecimal(total))
                        .addValue("at", java.time.LocalDateTime.ofInstant(Instant.parse(at), java.time.ZoneOffset.UTC)),
                keys, new String[] {"id_invoice"});
        return keys.getKey().longValue();
    }

    @Test @Order(4)
    void createUpdateConsentAndConcurrency() {
        CustomerDetailDto created = customers.create(request("55.123", "Luis Gómez", true, null));
        assertThat(created.docNumber()).isEqualTo("55123");
        assertThat(created.source()).isEqualTo("PANEL");
        assertThat(created.consentAt()).isNotNull();
        assertThat(created.overview().orders()).isZero();
        assertThatThrownBy(() -> customers.create(request("55123", "Otro", false, null))).isInstanceOf(ConflictException.class);

        CustomerDetailDto updated = customers.update(created.id(), request("55123", "Luis A. Gómez", true, created.lockVersion()));
        assertThat(updated.name()).isEqualTo("Luis A. Gómez");
        assertThat(updated.consentAt()).isEqualTo(created.consentAt());
        assertThatThrownBy(() -> customers.update(created.id(), request("55123", "Viejo", true, created.lockVersion())))
                .isInstanceOf(ConflictException.class);
        CustomerDetailDto revoked = customers.update(created.id(), request("55123", "Luis A. Gómez", false, updated.lockVersion()));
        assertThat(revoked.marketingConsent()).isFalse();
        assertThat(revoked.consentAt()).isNull();
    }

    @Test @Order(5)
    void ordersCreateOrCompleteCustomersWithoutOverwriting() {
        CustomerSync sync = new CustomerSync(new CustomerJdbcAdapter(jdbc), () -> actor, null,
                Clock.fixed(Instant.parse("2026-11-07T16:00:00Z"), ZoneId.of("America/Bogota")));
        // Documento nuevo: crea el cliente con origen ORDERS.
        assertThat(sync.fromOrder(CustomerInfo.of(null, null, "CE", "77.001", "Carla Ruiz", null, "3109998877", null, null))).isTrue();
        CustomerSummaryDto carla = search("77001").get(0);
        CustomerDetailDto created = customers.get(carla.id());
        assertThat(created.source()).isEqualTo("ORDERS");
        assertThat(created.marketingConsent()).isFalse();
        assertThat(created.email()).isNull();
        assertThat(customers.findByDocument("ce", "77 001").id()).isEqualTo(carla.id());
        assertThat(customers.findByDocument("CC", "77001").id()).isNull();

        // Orden posterior: completa lo vacío, no cambia nombre ni teléfono y sube la versión.
        assertThat(sync.fromOrder(CustomerInfo.of(null, null, "CE", "77001", "C. Ruiz", "carla@x.co", "3000000000",
                "Cl 10 # 5-20", "Cali"))).isTrue();
        CustomerDetailDto filled = customers.get(carla.id());
        assertThat(filled.name()).isEqualTo("Carla Ruiz");
        assertThat(filled.phone()).isEqualTo("3109998877");
        assertThat(filled.email()).isEqualTo("carla@x.co");
        assertThat(filled.billingCity()).isEqualTo("Cali");
        assertThat(filled.lockVersion()).isEqualTo(created.lockVersion() + 1);

        // Sin nada que completar: no toca la fila (la edición abierta en el panel no entra en conflicto).
        sync.fromOrder(CustomerInfo.of(null, null, "CE", "77001", "Otra", "otra@x.co", null, null, null));
        assertThat(customers.get(carla.id()).email()).isEqualTo("carla@x.co");
        assertThat(customers.get(carla.id()).lockVersion()).isEqualTo(filled.lockVersion());

        // Sin documento o sin nombre: no se registra.
        assertThat(sync.fromOrder(CustomerInfo.of(null, null, null, null, "Anónimo", "a@x.co", null, null, null))).isFalse();
        assertThat(sync.fromOrder(CustomerInfo.of(null, null, "CC", "8080", null, "b@x.co", null, null, null))).isFalse();

        // El consentimiento dado en el panel se conserva aunque llegue otra orden.
        CustomerDetailDto consented = customers.update(carla.id(), new SaveCustomerRequest(null, "CE", "77001", "Carla Ruiz",
                "carla@x.co", "3109998877", "Cl 10 # 5-20", "Cali", null, true, null, filled.lockVersion()));
        sync.fromOrder(CustomerInfo.of(null, null, "CE", "77001", "Carla", null, null, null, null));
        assertThat(customers.get(carla.id()).marketingConsent()).isTrue();
        assertThat(customers.get(carla.id()).consentAt()).isEqualTo(consented.consentAt());

        // Otra empresa: el mismo documento es otro cliente.
        RequestActor previous = actor;
        try {
            actor = new RequestActor(3, "u-9", "otra", MANAGE);
            assertThat(sync.fromOrder(CustomerInfo.of(null, null, "CE", "77001", "Carla (otra tienda)", null, null, null, null))).isTrue();
            assertThat(search(null)).extracting(CustomerSummaryDto::name).containsExactly("Carla (otra tienda)");
        } finally {
            actor = previous;
        }
        jdbc.update("DELETE FROM customers WHERE company_id = 3", new MapSqlParameterSource());
    }

    @Test @Order(6)
    void permissionsAndIsolation() {
        long id = search("1020").get(0).id();
        RequestActor previous = actor;
        try {
            actor = new RequestActor(3, "u-9", "otra", MANAGE);
            assertThatThrownBy(() -> customers.get(id)).isInstanceOf(NotFoundException.class);
            assertThat(search(null)).isEmpty();
            actor = new RequestActor(2, "u-2", "lector", Set.of("VENTAS.VER"));
            assertThat(customers.get(id).name()).isEqualTo("Ana");
            assertThatThrownBy(() -> customers.create(request("777", "X", false, null))).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> customers.importFromOrders()).isInstanceOf(ForbiddenException.class);
            actor = new RequestActor(2, "u-3", "sin permisos", Set.of());
            assertThatThrownBy(() -> search(null)).isInstanceOf(ForbiddenException.class);
        } finally {
            actor = previous;
        }
    }
}
