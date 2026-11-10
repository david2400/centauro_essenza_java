package com.essenza.draco.modules.billing.application.services.invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.billing.application.output.repository.InvoiceRepository;
import com.essenza.draco.modules.billing.application.output.repository.InvoiceSourceStore;
import com.essenza.draco.modules.billing.application.output.repository.ResolutionRepository;
import com.essenza.draco.modules.billing.domain.model.BillingRuleViolation;
import com.essenza.draco.modules.billing.domain.model.BillingSettings;
import com.essenza.draco.modules.billing.domain.model.DocumentKind;
import com.essenza.draco.modules.billing.domain.model.Invoice;
import com.essenza.draco.modules.billing.domain.model.InvoiceLine;
import com.essenza.draco.modules.billing.domain.model.InvoiceStatus;
import com.essenza.draco.modules.billing.domain.model.NumberingResolution;
import com.essenza.draco.shared.common.billing.ServiceInvoicing;
import com.essenza.draco.shared.common.billing.TaxRates;
import com.essenza.draco.shared.exceptions.ConflictException;

/**
 * Facturas de servicios sin orden (comisiones y cargos al vendedor del marketplace). Toma el consecutivo
 * de la numeración activa (bloqueada: serializa la emisión y la revisión de duplicados), calcula la base
 * y el impuesto de cada línea (valores con impuesto incluido) y la envía al proveedor. Un origen tiene a
 * lo sumo una factura vigente; si ya la tiene, la devuelve sin emitir otra.
 */
@Service
@Transactional
public class IssueServiceInvoiceService implements ServiceInvoicing {

    private final InvoiceSoporte soporte;
    private final InvoiceRepository invoices;
    private final ResolutionRepository resolutions;
    private final InvoiceSourceStore sources;
    private final TaxRates taxRates;

    public IssueServiceInvoiceService(InvoiceSoporte soporte, InvoiceRepository invoices, ResolutionRepository resolutions,
                                      InvoiceSourceStore sources, TaxRates taxRates) {
        this.soporte = soporte;
        this.invoices = invoices;
        this.resolutions = resolutions;
        this.sources = sources;
        this.taxRates = taxRates;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Result> active(String sourceType, String sourceKey) {
        return activeInvoice(sourceType, sourceKey).map(invoice -> result(invoice, false));
    }

    @Override
    public Result issue(Request request) {
        if (request == null || blank(request.sourceType()) || blank(request.sourceKey())) {
            throw new BillingRuleViolation("Falta el origen de la factura.");
        }
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new BillingRuleViolation("La factura no tiene conceptos.");
        }
        BillingSettings issuer = soporte.readyIssuer();
        NumberingResolution resolution = resolutions.lockActive(DocumentKind.INVOICE, issuer.environment())
                .orElseThrow(() -> new ConflictException("No hay una numeración de facturas activa para el ambiente "
                        + issuer.environment() + ": regístrala en Facturación → Numeración."));
        // Con la numeración bloqueada, dos emisiones del mismo origen no pueden cruzarse.
        Optional<Invoice> existing = activeInvoice(request.sourceType(), request.sourceKey());
        if (existing.isPresent()) {
            return result(existing.get(), false);
        }
        List<InvoiceLine> lines = new ArrayList<>();
        int position = 1;
        for (Line line : request.lines()) {
            if (line == null || line.total() == null || line.total().signum() <= 0) {
                continue;
            }
            lines.add(line(position++, line));
        }
        if (lines.isEmpty()) {
            throw new BillingRuleViolation("La factura no tiene conceptos con valor.");
        }
        long number = resolution.take(InvoiceSoporte.today());
        resolutions.save(resolution);
        Invoice invoice = invoices.save(new Invoice(null, DocumentKind.INVOICE, null, null, resolution.id(), resolution.prefix(), number,
                InvoiceSoporte.now(), InvoiceStatus.PENDING, issuer.environment(), buyer(request.buyer()), "CREDIT",
                "47 Transferencia bancaria", null, null, issuer.provider(), null, null, clip(request.note(), 500), lines));
        sources.link(invoice.id(), request.sourceType(), request.sourceKey());
        return result(soporte.send(invoice, issuer), true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Result> status(List<Long> invoiceIds) {
        List<Result> result = new ArrayList<>();
        if (invoiceIds != null) {
            for (Long id : invoiceIds) {
                invoices.findById(id).ifPresent(invoice -> result.add(result(invoice, false)));
            }
        }
        return result;
    }

    private Optional<Invoice> activeInvoice(String sourceType, String sourceKey) {
        for (Long id : sources.invoicesOf(sourceType, sourceKey)) {
            Optional<Invoice> invoice = invoices.findById(id);
            if (invoice.isPresent() && invoice.get().kind() == DocumentKind.INVOICE && invoice.get().status() != InvoiceStatus.VOIDED) {
                return invoice;
            }
        }
        return Optional.empty();
    }

    /** Valor con impuesto incluido: base = total / (1 + tarifa), impuesto = total − base. */
    InvoiceLine line(int position, Line line) {
        TaxRates.TaxRateView rate = taxRates.effective(line.taxRateId());
        BigDecimal total = line.total().setScale(2, RoundingMode.HALF_UP);
        BigDecimal base = rate.taxable()
                ? total.divide(BigDecimal.ONE.add(rate.rate().movePointLeft(2)), 2, RoundingMode.HALF_UP) : total;
        return new InvoiceLine(null, position, null, clip(line.code(), 40), clip(line.description(), 300), 1, total, BigDecimal.ZERO,
                rate.code(), rate.kind(), rate.taxable() ? rate.rate() : BigDecimal.ZERO, base, total.subtract(base), total);
    }

    private static com.essenza.draco.modules.billing.domain.model.Buyer buyer(ServiceInvoicing.Buyer b) {
        if (b == null || blank(b.docNumber())) {
            throw new BillingRuleViolation("Faltan el documento y el nombre de a quién se le factura.");
        }
        String doc = b.docNumber().replaceAll("[\\s.]", "");
        String dv = null;
        if ("NIT".equalsIgnoreCase(b.docType()) && doc.matches("\\d+-\\d")) {
            dv = doc.substring(doc.indexOf('-') + 1);
            doc = doc.substring(0, doc.indexOf('-'));
        }
        return new com.essenza.draco.modules.billing.domain.model.Buyer(b.docType(), doc, dv, clip(b.name(), 200), clip(b.email(), 160), clip(b.phone(), 40), clip(b.address(), 200),
                clip(b.city(), 120));
    }

    private static Result result(Invoice invoice, boolean created) {
        return new Result(invoice.id(), invoice.fullNumber(), invoice.status().name(), invoice.netAmount(), invoice.taxAmount(),
                invoice.total(), invoice.statusDetail(), created);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        return v.length() <= max ? v : v.substring(0, max);
    }
}
