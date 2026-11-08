package com.essenza.draco.modules.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

import com.essenza.draco.modules.marketplace.application.dto.PlanDtos.*;
import com.essenza.draco.modules.marketplace.application.dto.SellerDtos.*;
import com.essenza.draco.modules.marketplace.application.dto.OfferDtos.*;
import com.essenza.draco.modules.marketplace.application.port.OfferStore.OfferFilter;
import com.essenza.draco.modules.marketplace.application.port.PlanStore.PlanFilter;
import com.essenza.draco.modules.marketplace.application.port.SellerStore.SellerFilter;
import com.essenza.draco.modules.marketplace.application.service.*;
import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc.*;
import com.essenza.draco.modules.marketplace.infrastructure.outbound.storage.FileSystemDocumentStorage;
import com.essenza.draco.shared.common.web.PageableFactory;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.ForbiddenException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Flujo completo del marketplace contra MySQL/MariaDB con los adaptadores JDBC reales y los
 * servicios (sin contenedor Spring, así que sin transacciones ni bloqueos).
 *
 * <p>NO corre con {@code mvn test} (sufijo IT) ni sin configuración. Borra TODAS las filas de las
 * tablas mp_*: solo se ejecuta contra una base cuyo nombre termine en {@code _test} con la
 * migración V17 aplicada. Variables: MARKETPLACE_IT_DB_URL (p. ej.
 * jdbc:mysql://localhost:3306/storeweb_test), MARKETPLACE_IT_DB_USER, MARKETPLACE_IT_DB_PASSWORD.
 */
@EnabledIfEnvironmentVariable(named = "MARKETPLACE_IT_DB_URL", matches = "jdbc:.*_test(\\?.*)?")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MarketplaceFlowIT {

    static final Set<String> ALL = Set.of("VENDEDORES.VER", "VENDEDORES.GESTIONAR", "VENDEDORES.APROBAR", "PLANES.VER",
            "PLANES.GESTIONAR", "PLANES.ASIGNAR", "PLANES.EXCEPCIONES", "OFERTAS.VER", "OFERTAS.GESTIONAR", "OFERTAS.MODERAR");
    static RequestActor actor = new RequestActor(2, "u-1", "demo", ALL);
    static NamedParameterJdbcTemplate jdbc;
    static SellerService sellers;
    static PlanService plans;
    static AssignmentService assignments;
    static SellerDocumentService documents;
    static OfferService offers;
    static OfferService offersFourEyes;
    /** SKUs vendibles del catálogo de prueba (el resto existe pero no se puede vender). */
    static final java.util.Set<Long> SELLABLE = new java.util.HashSet<>();
    static long planV1, planV2, planPro, seller, own;
    static LocalDate today;

    @BeforeAll
    static void setUp() throws Exception {
        String url = System.getenv("MARKETPLACE_IT_DB_URL");
        assertThat(url).as("solo bases de prueba (*_test)").matches("jdbc:.*_test(\\?.*)?");
        DriverManagerDataSource ds = new DriverManagerDataSource(url, System.getenv("MARKETPLACE_IT_DB_USER"),
                System.getenv().getOrDefault("MARKETPLACE_IT_DB_PASSWORD", ""));
        jdbc = new NamedParameterJdbcTemplate(ds);
        // V19: las reservas y los subpedidos de prueba apuntan a ofertas y vendedores.
        // V19–V20: reservas, comisiones y estado de cuenta apuntan a ofertas y vendedores.
        for (String t : List.of("mp_seller_ledger", "mp_commission_adjustments", "mp_commissions", "mp_offer_reservations")) {
            if (exists(t)) {
                jdbc.update("DELETE FROM " + t, new MapSqlParameterSource());
            }
        }
        if (exists("orders") && jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'seller_id'""",
                new MapSqlParameterSource(), Integer.class) > 0) {
            jdbc.update("UPDATE orders SET seller_id = NULL WHERE seller_id IS NOT NULL", new MapSqlParameterSource());
            jdbc.update("UPDATE product_orders SET offer_id = NULL WHERE offer_id IS NOT NULL", new MapSqlParameterSource());
        }
        for (String t : List.of("mp_offer_events", "mp_offers", "mp_plan_assignments", "mp_plan_events", "mp_seller_events", "mp_seller_documents",
                "mp_seller_contacts", "mp_sellers", "mp_plans")) {
            jdbc.update("DELETE FROM " + t, new MapSqlParameterSource());
        }
        Clock clock = Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZoneId.of("America/Bogota"));
        today = LocalDate.now(clock);
        var sellerStore = new SellerJdbcStore(jdbc);
        var planStore = new PlanJdbcStore(jdbc);
        var assignStore = new AssignmentJdbcStore(jdbc);
        Path dir = Files.createTempDirectory("mpdocs");
        sellers = new SellerService(sellerStore, assignStore, () -> actor, clock);
        plans = new PlanService(planStore, assignStore, () -> actor, clock);
        assignments = new AssignmentService(sellerStore, planStore, assignStore, () -> actor, clock);
        documents = new SellerDocumentService(sellerStore, new FileSystemDocumentStorage(dir.toString()), () -> actor, clock);
        // Catálogo de prueba: SkuPricing leyendo product_skus (en la app lo implementa el módulo catalog).
        com.essenza.draco.shared.common.catalog.SkuPricing pricing = (productId, skuId) -> jdbc.query(
                "SELECT id_sku, product_id, code, name, price FROM product_skus WHERE id_sku = :id",
                new MapSqlParameterSource("id", skuId),
                (rs, i) -> new com.essenza.draco.shared.common.catalog.SkuPricing.SkuSnapshot(rs.getLong(1), rs.getLong(2),
                        rs.getString(3), rs.getString(4), rs.getBigDecimal(5), SELLABLE.contains(rs.getLong(1))))
                .stream().findFirst().orElseThrow(() -> new NotFoundException("SKU no encontrado: " + skuId));
        var offerStore = new OfferJdbcStore(jdbc);
        offers = new OfferService(offerStore, sellerStore, pricing, () -> actor, clock, "COP", false);
        offersFourEyes = new OfferService(offerStore, sellerStore, pricing, () -> actor, clock, "COP", true);
    }

    static boolean exists(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = :t", new MapSqlParameterSource("t", table), Integer.class) > 0;
    }

    static SavePlanRequest plan(String code, String modality, String fee, String pct, String fixed, Integer lock) {
        return new SavePlanRequest(code, "Plan " + code, null, modality, "COP", fee == null ? null : new BigDecimal(fee),
                pct == null ? null : new BigDecimal(pct), fixed == null ? null : new BigDecimal(fixed), null, false, false,
                null, null, null, null, 5, new BigDecimal("1.5"), null, null, null, lock);
    }

    static SaveSellerRequest sellerReq(String kind, String doc, String email, Integer version) {
        return new SaveSellerRequest(null, kind, "Moda Andina SAS", "Moda Andina", "NIT", doc, email, "3001234567", null,
                "Cra 1 # 2-3", "Medellín", null, null, null,
                List.of(new ContactDto("Laura Díaz", "Gerente", "laura@moda.co", null, true)), version);
    }

    @Test @Order(1)
    void plansLifecycle() {
        PlanDto v1 = plans.create(plan("basico", "COMMISSION", null, "10", "500", null));
        assertThat(v1.code()).isEqualTo("BASICO");
        assertThat(v1.status()).isEqualTo("DRAFT");
        PlanDto edited = plans.update(v1.id(), plan("BASICO", "COMMISSION", null, "12", "500", v1.lockVersion()));
        assertThat(edited.terms().commissionPercent()).isEqualByComparingTo("12");
        assertThatThrownBy(() -> plans.update(v1.id(), plan("BASICO", "COMMISSION", null, "11", null, v1.lockVersion())))
                .isInstanceOf(ConflictException.class);
        PlanDto pub = plans.publish(v1.id(), edited.lockVersion());
        assertThat(pub.status()).isEqualTo("ACTIVE");
        planV1 = pub.id();
        assertThatThrownBy(() -> plans.create(plan("BASICO", "COMMISSION", null, "9", null, null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("nueva versión");
        assertThatThrownBy(() -> plans.update(planV1, plan("BASICO", "COMMISSION", null, "9", null, pub.lockVersion())))
                .isInstanceOf(MarketplaceRuleViolation.class);

        PlanDto v2 = plans.newVersion(planV1, pub.lockVersion());
        assertThat(v2.version()).isEqualTo(2);
        assertThatThrownBy(() -> plans.newVersion(planV1, plans.get(planV1).lockVersion()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("borrador");
        PlanDto v2e = plans.update(v2.id(), plan(null, "COMMISSION", null, "8", "500", v2.lockVersion()));
        planV2 = plans.publish(v2.id(), v2e.lockVersion()).id();
        assertThat(plans.get(planV1).status()).isEqualTo("SUPERSEDED");

        PlanDto pro = plans.create(plan("PRO", "MIXED", "49900", "7.5", "300", null));
        planPro = plans.publish(pro.id(), pro.lockVersion()).id();

        var page = plans.search(new PlanFilter(null, "ACTIVE", null), PageableFactory.of(0, 10, null,
                Set.of("code", "version"), Sort.by(Sort.Order.asc("code"))));
        assertThat(page.getContent()).extracting(PlanDto::code).containsExactly("BASICO", "PRO");
        assertThat(plans.assignable("pro")).extracting(PlanDto::code).containsExactly("PRO");
        assertThat(plans.events(planV1)).extracting(PlanEventDto::type).contains("CREATED", "EDITED", "PUBLISHED", "SUPERSEDED");

        SimulationDto sim = plans.simulate(planPro, new SimulationRequest(new BigDecimal("33333.33"), null,
                new BigDecimal("8000"), null, 120, null, null));
        assertThat(sim.simulation()).isTrue();
        assertThat(sim.platformTotal()).isEqualByComparingTo("385900.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mp_plan_assignments", new MapSqlParameterSource(), Long.class)).isZero();
    }

    @Test @Order(2)
    void sellersAndUniqueness() {
        SellerDetailDto s = sellers.create(sellerReq("EXTERNAL", "900.123.456-7", null, null));
        seller = s.id();
        assertThat(s.code()).isEqualTo(String.format("VEN-%05d", seller));
        assertThat(s.status()).isEqualTo("DRAFT");
        assertThat(s.accountManagerName()).isEqualTo("demo");
        assertThat(s.allowedActions()).containsExactly("SUBMIT", "CLOSE");
        assertThat(s.readiness().forSubmit()).containsExactly("EMAIL", "DOC_RUT", "DOC_CAMARA_COMERCIO");
        assertThatThrownBy(() -> sellers.create(sellerReq("EXTERNAL", "900123456-7", null, null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("documento");
        own = sellers.create(new SaveSellerRequest("PROPIO", "OWN", "Essenza SAS", null, "NIT", "800000001-1", "a@b.co",
                null, null, null, null, null, null, null, List.of(), null)).id();
        assertThatThrownBy(() -> sellers.create(new SaveSellerRequest(null, "OWN", "Otra SAS", null, "NIT", "800000002",
                null, null, null, null, null, null, null, null, List.of(), null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("vendedor propio");

        SellerDetailDto updated = sellers.update(seller, sellerReq("EXTERNAL", "900123456-7", "ventas@moda.co", s.version()));
        assertThat(updated.email()).isEqualTo("ventas@moda.co");
        assertThatThrownBy(() -> sellers.update(seller, sellerReq("EXTERNAL", "900123456-7", "x@moda.co", s.version())))
                .isInstanceOf(ConflictException.class);

        var page = sellers.search(new SellerFilter("moda", null, "EXTERNAL"), PageableFactory.of(0, 10, "legalName,asc",
                Set.of("legalName"), Sort.by(Sort.Order.desc("updatedAt"))));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(sellers.stats().total()).isEqualTo(2);
        assertThat(sellers.stats().own()).isEqualTo(1);

        // Aislamiento: otra empresa no ve el vendedor.
        RequestActor previous = actor;
        actor = new RequestActor(3, "u-9", "otro", ALL);
        try {
            assertThatThrownBy(() -> sellers.get(seller)).isInstanceOf(NotFoundException.class);
            assertThat(sellers.stats().total()).isZero();
        } finally {
            actor = previous;
        }
        // Permisos.
        actor = new RequestActor(2, "u-2", "lector", Set.of("PLANES.VER"));
        try {
            assertThatThrownBy(() -> sellers.get(seller)).isInstanceOf(ForbiddenException.class);
        } finally {
            actor = previous;
        }
    }

    @Test @Order(3)
    void documentsReviewAndApproval() throws Exception {
        assertThatThrownBy(() -> sellers.transition(seller, new SellerTransitionRequest("SUBMIT", null, sellers.get(seller).version())))
                .hasMessageContaining("DOC_RUT");
        byte[] pdf = "%PDF-1.4 prueba".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> documents.upload(seller, "RUT", "rut.exe", "MZ...".getBytes()))
                .hasMessageContaining("PDF, PNG o JPG");
        DocumentDto rutOld = documents.upload(seller, "RUT", "../../etc/rut viejo.pdf", pdf);
        assertThat(rutOld.fileName()).isEqualTo("rut viejo.pdf");
        DocumentDto rut = documents.upload(seller, "RUT", "rut.pdf", pdf);
        DocumentDto camara = documents.upload(seller, "camara_comercio", "camara.PDF", pdf);
        assertThat(documents.list(seller, false)).hasSize(2);
        assertThat(documents.list(seller, true)).hasSize(3);

        SellerDetailDto submitted = sellers.transition(seller, new SellerTransitionRequest("SUBMIT", null, sellers.get(seller).version()));
        assertThat(submitted.status()).isEqualTo("IN_REVIEW");
        assertThatThrownBy(() -> documents.upload(seller, "OTRO", "x.pdf", pdf)).hasMessageContaining("en revisión");
        assertThatThrownBy(() -> sellers.transition(seller, new SellerTransitionRequest("APPROVE", null, submitted.version())))
                .hasMessageContaining("PLAN");

        AssignmentDto a = assignments.assign(seller, new AssignPlanRequest(planV2, null, null, null, null, "Inicial"));
        assertThat(a.state()).isEqualTo("CURRENT");
        assertThat(a.startsOn()).isEqualTo(today);
        assertThat(a.agreedTerms().commissionPercent()).isEqualByComparingTo("8");
        assertThatThrownBy(() -> assignments.assign(seller, new AssignPlanRequest(planV1, null, null, null, null, null)))
                .hasMessageContaining("publicados");

        assertThatThrownBy(() -> documents.review(seller, rutOld.id(), new ReviewDocumentRequest("ACCEPTED", null)))
                .hasMessageContaining("reemplazado");
        assertThatThrownBy(() -> documents.review(seller, rut.id(), new ReviewDocumentRequest("REJECTED", "")))
                .hasMessageContaining("por qué");
        documents.review(seller, rut.id(), new ReviewDocumentRequest("ACCEPTED", null));
        documents.review(seller, camara.id(), new ReviewDocumentRequest("ACCEPTED", "Vigente"));

        SellerDetailDto approved = sellers.transition(seller, new SellerTransitionRequest("APPROVE", null, sellers.get(seller).version()));
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.approvedBy()).isEqualTo("u-1");
        assertThat(approved.allowedActions()).containsExactly("SUSPEND", "CLOSE");

        try (var in = documents.download(seller, rut.id()).content()) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.US_ASCII)).startsWith("%PDF-");
        }
    }

    @Test @Order(4)
    void scheduleCancelAndExceptions() {
        AssignmentDto next = assignments.assign(seller, new AssignPlanRequest(planPro, null, null, null, null, null));
        assertThat(next.state()).isEqualTo("SCHEDULED");
        assertThat(next.startsOn()).isEqualTo(LocalDate.of(2026, 12, 1));
        List<AssignmentDto> list = assignments.list(seller);
        assertThat(list.get(1).endsOn()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(sellers.search(new SellerFilter(null, null, null), PageableFactory.of(0, 10, null, Set.of(),
                Sort.by(Sort.Order.desc("updatedAt")))).getContent())
                .filteredOn(r -> r.id() == seller).first()
                .satisfies(r -> {
                    assertThat(r.currentPlanCode()).isEqualTo("BASICO");
                    assertThat(r.scheduledPlanStartsOn()).isEqualTo(LocalDate.of(2026, 12, 1));
                });
        assertThatThrownBy(() -> assignments.assign(seller, new AssignPlanRequest(planPro, null, null, null, null, null)))
                .hasMessageContaining("Cancélalo");
        assignments.cancel(seller, next.id(), "Cliente pidió esperar");
        assertThat(assignments.list(seller).get(1).endsOn()).isNull();

        RequestActor previous = actor;
        actor = new RequestActor(2, "u-3", "asesor", Set.of("PLANES.ASIGNAR", "VENDEDORES.VER"));
        try {
            assertThatThrownBy(() -> assignments.assign(seller, new AssignPlanRequest(planPro, null, new BigDecimal("5"),
                    null, "Aliado estratégico", null))).isInstanceOf(ForbiddenException.class);
        } finally {
            actor = previous;
        }
        assertThatThrownBy(() -> assignments.assign(seller, new AssignPlanRequest(planPro, null, new BigDecimal("5"),
                null, "", null))).hasMessageContaining("excepción");
        AssignmentDto special = assignments.assign(seller, new AssignPlanRequest(planPro, LocalDate.of(2027, 1, 1),
                new BigDecimal("5"), null, "Aliado estratégico", null));
        assertThat(special.agreedTerms().commissionPercent()).isEqualByComparingTo("5");
        assertThat(special.agreedTerms().monthlyFee()).isEqualByComparingTo("49900");
        assertThat(special.overrideApprovedBy()).isEqualTo("u-1");

        assertThatThrownBy(() -> assignments.assign(own, new AssignPlanRequest(planPro, null, null, null, null, null)))
                .hasMessageContaining("propio");

        List<SellerEventDto> events = sellers.events(seller);
        assertThat(events).extracting(SellerEventDto::type).contains("CREATED", "EDITED", "DOCUMENT_UPLOADED",
                "SUBMIT", "PLAN_ASSIGNED", "DOCUMENT_ACCEPTED", "APPROVE", "DOCUMENT_DOWNLOADED", "PLAN_SCHEDULED",
                "PLAN_CANCELLED");
        assertThat(events.get(0).actorName()).isEqualTo("demo");
    }

    @Test @Order(5)
    void suspendReactivateCloseKeepsHistory() {
        int v = sellers.get(seller).version();
        assertThatThrownBy(() -> sellers.transition(seller, new SellerTransitionRequest("SUSPEND", "", v)))
                .hasMessageContaining("motivo");
        SellerDetailDto suspended = sellers.transition(seller, new SellerTransitionRequest("SUSPEND", "Reclamos sin atender", v));
        assertThat(suspended.statusReason()).isEqualTo("Reclamos sin atender");
        assertThatThrownBy(() -> assignments.assign(seller, new AssignPlanRequest(planV2, null, null, null, null, null)))
                .hasMessageContaining("suspendidos");
        SellerDetailDto back = sellers.transition(seller, new SellerTransitionRequest("REACTIVATE", "Plan de mejora cumplido", suspended.version()));
        SellerDetailDto closed = sellers.transition(seller, new SellerTransitionRequest("CLOSE", "Terminación del contrato", back.version()));
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.allowedActions()).isEmpty();
        assertThatThrownBy(() -> sellers.update(seller, sellerReq("EXTERNAL", "900123456-7", "z@moda.co", closed.version())))
                .hasMessageContaining("cerrado");
        assertThat(sellers.events(seller)).hasSizeGreaterThan(10);
    }

    @Test @Order(6)
    void offersAndModeration() {
        jdbc.update("DELETE FROM product_skus", new MapSqlParameterSource());
        jdbc.update("INSERT INTO product_skus (id_sku, product_id, code, name, price) VALUES (101, 11, 'CAM-AZ-M', 'Camisa azul M', 100000),"
                + " (102, 12, 'PANT-01', 'Pantalón', 80000)", new MapSqlParameterSource());
        SELLABLE.clear();
        SELLABLE.add(101L);
        long vendor = sellers.create(sellerReq("EXTERNAL", "901000111", "v@x.co", null)).id();
        jdbc.update("UPDATE mp_sellers SET status = 'APPROVED' WHERE id = :id", new MapSqlParameterSource("id", vendor));

        OfferDetailDto o = offers.create(new SaveOfferRequest(vendor, 101L, null, new BigDecimal("150000"), 5, 2, "NEW", null, "REF-1", null));
        assertThat(o.status()).isEqualTo("DRAFT");
        assertThat(o.currency()).isEqualTo("COP");
        assertThat(o.sku().name()).isEqualTo("Camisa azul M");
        assertThat(o.priceDeviationPercent()).isEqualByComparingTo("50.00");
        assertThat(o.priceAlert()).isFalse();
        assertThat(o.allowedActions()).containsExactly("SUBMIT", "CLOSE");
        assertThatThrownBy(() -> offers.create(new SaveOfferRequest(vendor, 101L, null, BigDecimal.TEN, 1, 1, null, null, null, null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("abierta");
        assertThatThrownBy(() -> offers.create(new SaveOfferRequest(own, 101L, null, BigDecimal.TEN, 1, 1, null, null, null, null)))
                .hasMessageContaining("propio");
        assertThatThrownBy(() -> offers.create(new SaveOfferRequest(vendor, 999L, null, BigDecimal.TEN, 1, 1, null, null, null, null)))
                .isInstanceOf(NotFoundException.class);

        o = offers.update(o.id(), new SaveOfferRequest(null, null, null, new BigDecimal("160000"), 5, 2, "NEW", null, "REF-1", o.lockVersion()));
        assertThat(o.priceAlert()).isTrue();
        o = offers.transition(o.id(), new OfferTransitionRequest("SUBMIT", null, o.lockVersion()));
        assertThat(o.status()).isEqualTo("PENDING_REVIEW");
        long id = o.id();
        int lock = o.lockVersion();
        assertThatThrownBy(() -> offers.update(id, new SaveOfferRequest(null, null, null, BigDecimal.TEN, 1, 1, null, null, null, lock)))
                .hasMessageContaining("moderación");

        // Cuatro ojos: quien la creó no la aprueba; otro moderador sí.
        assertThatThrownBy(() -> offersFourEyes.transition(id, new OfferTransitionRequest("APPROVE", null, lock)))
                .hasMessageContaining("otra persona");
        RequestActor previous = actor;
        actor = new RequestActor(2, "u-mod", "moderador", Set.of("OFERTAS.MODERAR"));
        try {
            assertThat(offersFourEyes.get(id).allowedActions()).containsExactly("APPROVE", "REJECT");
            o = offersFourEyes.transition(id, new OfferTransitionRequest("APPROVE", null, lock));
        } finally {
            actor = previous;
        }
        assertThat(o.status()).isEqualTo("ACTIVE");
        assertThat(o.visible()).isTrue();
        assertThat(o.publishedAt()).isNotNull();

        int lock2 = o.lockVersion();
        assertThatThrownBy(() -> offers.update(id, new SaveOfferRequest(null, null, null, new BigDecimal("160000"), 5, 2, "USED", null, "REF-1", lock2)))
                .hasMessageContaining("condition");
        o = offers.update(id, new SaveOfferRequest(null, null, null, new BigDecimal("160000"), 8, 2, "NEW", null, "REF-1", lock2));
        assertThat(o.availableQuantity()).isEqualTo(8);
        assertThat(offers.events(id).get(0).detail()).contains("available_quantity: 5 → 8");

        jdbc.update("UPDATE mp_sellers SET status = 'SUSPENDED' WHERE id = :id", new MapSqlParameterSource("id", vendor));
        assertThat(offers.get(id).visible()).isFalse();
        o = offers.transition(id, new OfferTransitionRequest("PAUSE", null, o.lockVersion()));
        int lock3 = o.lockVersion();
        assertThatThrownBy(() -> offers.transition(id, new OfferTransitionRequest("RESUME", null, lock3))).hasMessageContaining("aprobado");
        jdbc.update("UPDATE mp_sellers SET status = 'APPROVED' WHERE id = :id", new MapSqlParameterSource("id", vendor));
        o = offers.transition(id, new OfferTransitionRequest("RESUME", null, lock3));
        assertThat(o.status()).isEqualTo("ACTIVE");

        // SKU no vendible: no se puede enviar a moderación.
        OfferDetailDto pants = offers.create(new SaveOfferRequest(vendor, 102L, "COP", new BigDecimal("75000"), 3, 1, null, null, null, null));
        assertThatThrownBy(() -> offers.transition(pants.id(), new OfferTransitionRequest("SUBMIT", null, pants.lockVersion())))
                .hasMessageContaining("SKU");

        assertThat(offers.search(new OfferFilter(null, "ACTIVE", vendor, null), PageableFactory.of(0, 10, "price,desc",
                Set.of("price"), Sort.by(Sort.Order.desc("updatedAt")))).getTotalElements()).isEqualTo(1);
        assertThat(offers.search(new OfferFilter("pantal", null, null, null), PageableFactory.of(0, 10, null,
                Set.of(), Sort.by(Sort.Order.desc("updatedAt")))).getContent()).extracting(OfferSummaryDto::skuCode).containsExactly("PANT-01");
        assertThat(offers.stats(vendor).active()).isEqualTo(1);
        assertThat(offers.stats(vendor).draft()).isEqualTo(1);

        assertThatThrownBy(() -> offers.transition(id, new OfferTransitionRequest("CLOSE", "", offers.get(id).lockVersion())))
                .hasMessageContaining("motivo");
        offers.transition(id, new OfferTransitionRequest("CLOSE", "Producto descontinuado", offers.get(id).lockVersion()));
        // Cerrada libera el SKU para una oferta nueva del mismo vendedor.
        assertThat(offers.create(new SaveOfferRequest(vendor, 101L, null, new BigDecimal("99000"), 1, 1, null, null, null, null)).status())
                .isEqualTo("DRAFT");

        actor = new RequestActor(3, "u-9", "otro", Set.of("OFERTAS.VER", "OFERTAS.GESTIONAR"));
        try {
            assertThatThrownBy(() -> offers.get(id)).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> offers.create(new SaveOfferRequest(vendor, 101L, null, BigDecimal.TEN, 1, 1, null, null, null, null)))
                    .isInstanceOf(NotFoundException.class);
            assertThat(offers.stats(null).total()).isZero();
        } finally {
            actor = previous;
        }
        actor = new RequestActor(2, "u-2", "lector", Set.of("VENDEDORES.VER"));
        try {
            assertThatThrownBy(() -> offers.get(id)).isInstanceOf(ForbiddenException.class);
            assertThat(sellers.lookup("moda", "EXTERNAL")).extracting(o2 -> o2.id()).contains(vendor);
        } finally {
            actor = previous;
        }
    }
}
