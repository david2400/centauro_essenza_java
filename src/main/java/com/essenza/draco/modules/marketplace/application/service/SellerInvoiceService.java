package com.essenza.draco.modules.marketplace.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.marketplace.application.MarketplacePermissions;
import com.essenza.draco.modules.marketplace.application.dto.SellerInvoiceDtos.ConceptDto;
import com.essenza.draco.modules.marketplace.application.dto.SellerInvoiceDtos.InvoiceRefDto;
import com.essenza.draco.modules.marketplace.application.dto.SellerInvoiceDtos.IssueRequest;
import com.essenza.draco.modules.marketplace.application.dto.SellerInvoiceDtos.IssueResultDto;
import com.essenza.draco.modules.marketplace.application.dto.SellerInvoiceDtos.PeriodPreviewDto;
import com.essenza.draco.modules.marketplace.application.dto.SellerInvoiceDtos.SellerPeriodDto;
import com.essenza.draco.modules.marketplace.application.port.SellerInvoiceStore;
import com.essenza.draco.modules.marketplace.application.port.SellerInvoiceStore.PeriodTotals;
import com.essenza.draco.modules.marketplace.application.port.SellerInvoiceStore.SellerParty;
import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.shared.common.billing.ServiceInvoicing;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Factura mensual de la plataforma a cada vendedor externo: comisiones del mes (netas de reversas),
 * mensualidad y almacenamiento del ciclo y cargos por reclamos. Valores con impuesto incluido (la tarifa
 * por defecto de facturación o {@code essenza.marketplace.invoicing.tax-rate-id}). Lo que la tienda recauda
 * por cuenta del vendedor no se le factura: esa venta la factura el vendedor al comprador (mandato).
 * Una factura vigente por vendedor y periodo. COMISIONES.VER para ver, COMISIONES.GESTIONAR para facturar.
 */
@Service
@Transactional
public class SellerInvoiceService {

    static final String SOURCE = "MARKETPLACE_SELLER_FEES";

    private final SellerInvoiceStore store;
    private final ServiceInvoicing invoicing;
    private final ActorProvider actors;
    private final Clock clock;
    private final Long taxRateId;

    public SellerInvoiceService(SellerInvoiceStore store, ServiceInvoicing invoicing, ActorProvider actors, Clock marketplaceClock,
                                @Value("${essenza.marketplace.invoicing.tax-rate-id:}") String taxRateId) {
        this.store = store;
        this.invoicing = invoicing;
        this.actors = actors;
        this.clock = marketplaceClock;
        this.taxRateId = taxRateId == null || taxRateId.isBlank() ? null : Long.valueOf(taxRateId.trim());
    }

    @Transactional(readOnly = true)
    public PeriodPreviewDto preview(String periodText) {
        RequestActor actor = actors.current().requireAny(MarketplacePermissions.BILLING_VIEW, MarketplacePermissions.BILLING_MANAGE);
        return preview(actor.companyId(), period(periodText));
    }

    public IssueResultDto issue(IssueRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_MANAGE);
        MarketplaceRuleViolation.check(request != null, "Indica el periodo a facturar.");
        YearMonth period = period(request.period());
        MarketplaceRuleViolation.check(period.atEndOfMonth().isBefore(LocalDate.now(clock)),
                "Solo se facturan meses cerrados (el " + period + " aún no termina).");
        Set<Long> chosen = request.sellerIds() == null || request.sellerIds().isEmpty() ? null : new HashSet<>(request.sellerIds());
        PeriodPreviewDto before = preview(actor.companyId(), period);
        int issued = 0;
        int already = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        Map<Long, SellerParty> parties = store.sellers(actor.companyId());
        for (SellerPeriodDto seller : before.sellers()) {
            if (chosen != null && !chosen.contains(seller.sellerId())) {
                continue;
            }
            if (seller.invoice() != null && !"VOIDED".equals(seller.invoice().status())) {
                already++;
                continue;
            }
            if (seller.total().signum() <= 0) {
                skipped++;
                continue;
            }
            if (!seller.missing().isEmpty()) {
                errors.add(seller.sellerName() + ": faltan " + String.join(", ", seller.missing()) + ".");
                continue;
            }
            SellerParty party = parties.get(seller.sellerId());
            ServiceInvoicing.Result result = invoicing.issue(new ServiceInvoicing.Request(SOURCE,
                    actor.companyId() + ":" + seller.sellerId() + ":" + period,
                    new ServiceInvoicing.Buyer(party.docType(), party.docNumber(), party.name(), party.email(), party.phone(),
                            party.address(), party.city()),
                    seller.concepts().stream().map(c -> new ServiceInvoicing.Line(c.code(), c.description(), c.total(), taxRateId)).toList(),
                    "Comisiones y cargos del marketplace " + period));
            store.link(actor.companyId(), seller.sellerId(), period.toString(), result.invoiceId(), result.number(), result.total(),
                    actor.userId(), actor.displayName(), clock.instant());
            if (result.created()) {
                issued++;
            } else {
                already++;
            }
        }
        return new IssueResultDto(period.toString(), issued, already, skipped, errors, preview(actor.companyId(), period));
    }

    private PeriodPreviewDto preview(long companyId, YearMonth period) {
        Map<Long, SellerParty> parties = store.sellers(companyId);
        Map<Long, PeriodTotals> totals = store.totals(companyId, period.atDay(1), period.atEndOfMonth(), period.toString());
        Map<Long, List<SellerInvoiceStore.IssuedRef>> issued = store.issued(companyId, period.toString());
        Map<Long, InvoiceRefDto> current = new java.util.HashMap<>();
        List<Long> ids = issued.values().stream().map(list -> list.get(0).invoiceId()).toList();
        Map<Long, ServiceInvoicing.Result> status = new java.util.HashMap<>();
        invoicing.status(ids).forEach(r -> status.put(r.invoiceId(), r));
        issued.forEach((sellerId, list) -> {
            SellerInvoiceStore.IssuedRef last = list.get(0);
            ServiceInvoicing.Result r = status.get(last.invoiceId());
            current.put(sellerId, new InvoiceRefDto(last.invoiceId(), r == null ? last.number() : r.number(),
                    r == null ? "PENDING" : r.status(), r == null ? last.total() : r.total(), r == null ? null : r.detail(), last.createdAt()));
        });
        List<SellerPeriodDto> sellers = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int toInvoice = 0;
        int invoiced = 0;
        for (SellerParty party : parties.values()) {
            PeriodTotals t = totals.get(party.id());
            InvoiceRefDto invoice = current.get(party.id());
            if (t == null && invoice == null) {
                continue;
            }
            List<ConceptDto> concepts = t == null ? List.of() : concepts(t, period);
            BigDecimal sellerTotal = concepts.stream().map(ConceptDto::total).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal collected = t == null ? BigDecimal.ZERO : t.collected();
            if (sellerTotal.signum() <= 0 && invoice == null && collected.signum() == 0) {
                continue;
            }
            List<String> missing = missing(party);
            sellers.add(new SellerPeriodDto(party.id(), party.name(), party.docType() + " " + party.docNumber(), concepts, sellerTotal,
                    collected, invoice, missing));
            total = total.add(sellerTotal);
            if (invoice != null && !"VOIDED".equals(invoice.status())) {
                invoiced++;
            } else if (sellerTotal.signum() > 0) {
                toInvoice++;
            }
        }
        return new PeriodPreviewDto(period.toString(), period.atEndOfMonth().isBefore(LocalDate.now(clock)), total, toInvoice, invoiced,
                sellers);
    }

    static List<ConceptDto> concepts(PeriodTotals t, YearMonth period) {
        List<ConceptDto> concepts = new ArrayList<>();
        if (t.commissions().signum() > 0) {
            concepts.add(new ConceptDto("COMMISSIONS", "Comisiones por ventas en el marketplace " + period + " (" + t.commissionCount()
                    + (t.commissionCount() == 1 ? " venta)" : " ventas)"), t.commissions()));
        }
        if (t.monthlyFee().signum() > 0) {
            concepts.add(new ConceptDto("MONTHLY_FEE", "Mensualidad del plan comercial " + period, t.monthlyFee()));
        }
        if (t.storage().signum() > 0) {
            concepts.add(new ConceptDto("STORAGE", "Almacenamiento de mercancía en consignación " + period, t.storage()));
        }
        if (t.disputes().signum() > 0) {
            concepts.add(new ConceptDto("DISPUTES", "Cargos por reclamos resueltos a favor del comprador " + period, t.disputes()));
        }
        return concepts;
    }

    static List<String> missing(SellerParty party) {
        List<String> missing = new ArrayList<>();
        if (party.docNumber() == null || party.docNumber().isBlank()) {
            missing.add("documento");
        }
        if (party.name() == null || party.name().isBlank()) {
            missing.add("razón social");
        }
        if (party.email() == null || party.email().isBlank()) {
            missing.add("correo (para enviarle la factura)");
        }
        return missing;
    }

    static YearMonth period(String text) {
        if (text == null || text.isBlank()) {
            throw new MarketplaceRuleViolation("Indica el periodo (AAAA-MM).");
        }
        try {
            return YearMonth.parse(text.trim());
        } catch (DateTimeParseException ex) {
            throw new MarketplaceRuleViolation("Periodo no válido: " + text + " (AAAA-MM).");
        }
    }

}
