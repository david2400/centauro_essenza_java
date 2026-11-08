package com.essenza.draco.modules.sales.application.services.customer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.sales.application.dto.customer.CustomerDtos.ChannelTotalDto;
import com.essenza.draco.modules.sales.application.dto.customer.CustomerDtos.CustomerAfterSalesDto;
import com.essenza.draco.modules.sales.application.dto.customer.CustomerDtos.CustomerInvoiceDto;
import com.essenza.draco.modules.sales.application.dto.customer.CustomerDtos.CustomerReturnDto;
import com.essenza.draco.modules.sales.application.dto.customer.CustomerDtos.InvoicesDto;
import com.essenza.draco.modules.sales.application.dto.customer.CustomerDtos.ReturnsDto;
import com.essenza.draco.modules.sales.application.dto.customer.CustomerDtos.CustomerDetailDto;
import com.essenza.draco.modules.sales.application.dto.customer.CustomerDtos.CustomerLinkDto;
import com.essenza.draco.modules.sales.application.dto.customer.CustomerDtos.CustomerOverviewDto;
import com.essenza.draco.modules.sales.application.dto.customer.CustomerDtos.CustomerSummaryDto;
import com.essenza.draco.modules.sales.application.dto.customer.CustomerDtos.ImportResultDto;
import com.essenza.draco.modules.sales.application.dto.customer.CustomerDtos.RecentOrderDto;
import com.essenza.draco.modules.sales.application.dto.customer.CustomerDtos.SaveCustomerRequest;
import com.essenza.draco.modules.sales.application.output.repository.CustomerRegistry;
import com.essenza.draco.modules.sales.application.output.repository.CustomerRegistry.Activity;
import com.essenza.draco.modules.sales.application.output.repository.CustomerRegistry.CustomerRow;
import com.essenza.draco.modules.sales.domain.model.customer.CustomerProfile;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Clientes persistentes y ficha 360: datos del cliente, consentimiento de comunicaciones y su
 * actividad (órdenes por documento, lo comprado, devuelto y pendiente, canales y compras
 * multivendedor). Empresa y permisos salen del token (VENTAS.VER / VENTAS.GESTIONAR).
 */
@Service
@Transactional
public class CustomerService {

    public static final String SALES_VIEW = "VENTAS.VER";
    public static final String SALES_MANAGE = "VENTAS.GESTIONAR";
    static final String RETURNS_VIEW = "POSTVENTA.VER";
    static final String RETURNS_MANAGE = "POSTVENTA.GESTIONAR";
    static final String BILLING_VIEW = "FACTURACION.VER";
    static final String BILLING_MANAGE = "FACTURACION.GESTIONAR";
    static final int RECENT = 10;
    static final ZoneId ZONE = ZoneId.of("America/Bogota");

    private final CustomerRegistry customers;
    private final ActorProvider actors;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public CustomerService(CustomerRegistry customers, ActorProvider actors) {
        this(customers, actors, Clock.system(ZONE));
    }

    /** Para pruebas: reloj fijo. */
    public CustomerService(CustomerRegistry customers, ActorProvider actors, Clock clock) {
        this.customers = customers;
        this.actors = actors;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<CustomerSummaryDto> search(String q, Pageable pageable) {
        RequestActor actor = actors.current().requireAny(SALES_VIEW, SALES_MANAGE);
        return customers.search(actor.companyId(), q, pageable).map(c -> new CustomerSummaryDto(c.id(), c.kind(), c.docType(),
                c.docNumber(), c.name(), c.email(), c.phone(), c.billingCity(), c.marketingConsent(), c.ordersCount(),
                c.totalSpent(), c.lastOrderAt()));
    }

    @Transactional(readOnly = true)
    public CustomerDetailDto get(long id) {
        RequestActor actor = actors.current().requireAny(SALES_VIEW, SALES_MANAGE);
        return detail(actor, row(actor, id));
    }

    /** Cliente registrado para el documento de una orden (mismas reglas de limpieza que la orden). */
    @Transactional(readOnly = true)
    public CustomerLinkDto findByDocument(String docType, String docNumber) {
        RequestActor actor = actors.current().requireAny(SALES_VIEW, SALES_MANAGE);
        String number = docNumber == null ? null : docNumber.trim().replace(" ", "").replace(".", "");
        if (number == null || number.isEmpty()) {
            return new CustomerLinkDto(null, null, null);
        }
        String type = docType == null || docType.isBlank() ? "CC" : docType.trim().toUpperCase(java.util.Locale.ROOT);
        return new CustomerLinkDto(customers.findIdByDocument(actor.companyId(), type, number).orElse(null), type, number);
    }

    public CustomerDetailDto create(SaveCustomerRequest request) {
        RequestActor actor = actors.current().require(SALES_MANAGE);
        Instant now = clock.instant();
        CustomerProfile profile = profile(request, null, now);
        long id = customers.insert(actor.companyId(), profile, "PANEL", actor.userId(), now);
        return detail(actor, row(actor, id));
    }

    public CustomerDetailDto update(long id, SaveCustomerRequest request) {
        RequestActor actor = actors.current().require(SALES_MANAGE);
        CustomerRow current = row(actor, id);
        if (request.lockVersion() == null) {
            throw new IllegalArgumentException("Falta lock_version.");
        }
        Instant now = clock.instant();
        CustomerProfile profile = profile(request, current, now);
        if (!customers.update(actor.companyId(), id, request.lockVersion(), profile, actor.userId(), now)) {
            throw new ConflictException("Otra persona modificó el cliente. Recarga para ver los cambios.");
        }
        return detail(actor, row(actor, id));
    }

    /** Registra como clientes los documentos de órdenes anteriores (idempotente). */
    public ImportResultDto importFromOrders() {
        RequestActor actor = actors.current().require(SALES_MANAGE);
        return new ImportResultDto(customers.importFromOrders(actor.companyId(), actor.userId(), clock.instant()));
    }

    private CustomerProfile profile(SaveCustomerRequest r, CustomerRow current, Instant now) {
        return CustomerProfile.of(r.kind(), r.docType(), r.docNumber(), r.name(), r.email(), r.phone(), r.billingAddress(),
                r.billingCity(), r.storeUserId(), r.marketingConsent(), current == null ? null : current.consentAt(),
                current != null && current.marketingConsent(), r.notes(), now);
    }

    private CustomerRow row(RequestActor actor, long id) {
        return customers.find(actor.companyId(), id)
                .orElseThrow(() -> new NotFoundException("No se encontró el cliente " + id + "."));
    }

    private CustomerDetailDto detail(RequestActor actor, CustomerRow c) {
        return new CustomerDetailDto(c.id(), c.kind(), c.docType(), c.docNumber(), c.name(), c.email(), c.phone(),
                c.billingAddress(), c.billingCity(), c.storeUserId(), c.marketingConsent(), c.consentAt(), c.notes(), c.source(),
                c.lockVersion(), c.createdAt(), c.createdBy(), c.updatedAt(), c.updatedBy(), overview(c), afterSales(actor, c));
    }

    /** Posventa visible según los permisos del usuario en cada módulo (no basta con ver ventas). */
    CustomerAfterSalesDto afterSales(RequestActor actor, CustomerRow c) {
        ReturnsDto returns = null;
        if (actor.has(RETURNS_VIEW) || actor.has(RETURNS_MANAGE)) {
            CustomerRegistry.Returns r = customers.returns(c.docType(), c.docNumber(), RECENT);
            returns = new ReturnsDto(r.total(), r.open(), r.refunded(), r.recent().stream()
                    .map(d -> new CustomerReturnDto(d.id(), d.orderId(), d.orderNumber(), d.state(), d.requested(), d.refunded(),
                            d.createdAt()))
                    .toList());
        }
        InvoicesDto invoices = null;
        if (actor.has(BILLING_VIEW) || actor.has(BILLING_MANAGE)) {
            CustomerRegistry.Invoices v = customers.invoices(c.docType(), c.docNumber(), RECENT);
            invoices = new InvoicesDto(v.invoices(), v.creditNotes(), v.pending(), v.invoiced(), v.recent().stream()
                    .map(i -> new CustomerInvoiceDto(i.id(), i.orderId(), i.kind(), i.number(), i.status(), i.total(), i.issuedAt()))
                    .toList());
        }
        return new CustomerAfterSalesDto(returns, invoices);
    }

    CustomerOverviewDto overview(CustomerRow c) {
        Activity a = customers.activity(c.docType(), c.docNumber());
        BigDecimal average = a.activeOrders() == 0 ? BigDecimal.ZERO.setScale(2)
                : a.spent().divide(BigDecimal.valueOf(a.activeOrders()), 2, RoundingMode.HALF_UP);
        Long days = a.lastOrderAt() == null ? null
                : ChronoUnit.DAYS.between(LocalDate.ofInstant(a.lastOrderAt(), ZONE), LocalDate.now(clock));
        return new CustomerOverviewDto(a.orders(), a.activeOrders(), a.cancelled(), a.pendingOrders(), a.spent(),
                a.spent().subtract(a.marketplaceSpent()), a.marketplaceSpent(), a.returned(), a.pendingAmount(), average,
                a.purchases(), a.firstOrderAt(), a.lastOrderAt(), days,
                a.byChannel().stream().map(ch -> new ChannelTotalDto(ch.channel(), ch.orders(), ch.spent())).toList(),
                customers.recentOrders(c.docType(), c.docNumber(), 10).stream()
                        .map(o -> new RecentOrderDto(o.id(), o.orderNumber(), o.state(), o.total(), o.returned(), o.channel(),
                                o.thirdParty(), o.purchaseId(), o.createdAt()))
                        .toList());
    }
}
