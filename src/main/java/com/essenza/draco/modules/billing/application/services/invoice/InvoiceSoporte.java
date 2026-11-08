package com.essenza.draco.modules.billing.application.services.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.essenza.draco.modules.billing.application.dto.invoice.BuyerDto;
import com.essenza.draco.modules.billing.application.dto.invoice.InvoiceDto;
import com.essenza.draco.modules.billing.application.dto.invoice.InvoiceLineDto;
import com.essenza.draco.modules.billing.application.dto.invoice.InvoiceTaxDto;
import com.essenza.draco.modules.billing.application.output.provider.ElectronicInvoicingProvider;
import com.essenza.draco.modules.billing.application.output.repository.BillingSettingsRepository;
import com.essenza.draco.modules.billing.application.output.repository.InvoiceRepository;
import com.essenza.draco.modules.billing.application.output.repository.ResolutionRepository;
import com.essenza.draco.modules.billing.domain.model.BillingSettings;
import com.essenza.draco.modules.billing.domain.model.Buyer;
import com.essenza.draco.modules.billing.domain.model.Cufe;
import com.essenza.draco.modules.billing.domain.model.DocumentKind;
import com.essenza.draco.modules.billing.domain.model.Invoice;
import com.essenza.draco.modules.billing.domain.model.InvoiceLine;
import com.essenza.draco.modules.billing.domain.model.InvoiceStatus;
import com.essenza.draco.modules.billing.domain.model.NumberingResolution;
import com.essenza.draco.shared.common.sales.OrderBilling.BillableLine;
import com.essenza.draco.shared.common.sales.OrderBilling.BillableOrder;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;

/** Piezas comunes de facturas y notas crédito: emisor, envío al proveedor y DTO. */
@Component
class InvoiceSoporte {

    static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    static final Set<String> PAID_STATES = Set.of("PAID", "PROCESSING", "READY", "SHIPPED", "DELIVERED");

    /** Medio de pago DIAN por tipo de medio de la tienda. */
    private static final Map<String, String> MEANS = Map.of(
            "CASH", "10 Efectivo",
            "CASH_ON_DELIVERY", "10 Efectivo",
            "CARD_TERMINAL", "48 Tarjeta crédito/débito",
            "BANK_TRANSFER", "47 Transferencia bancaria",
            "DIGITAL_WALLET", "47 Transferencia (billetera digital)",
            "GATEWAY", "48 Pago en línea con tarjeta/PSE",
            "OTHER", "ZZZ Otro");

    private final BillingSettingsRepository settings;
    private final InvoiceRepository invoices;
    private final ResolutionRepository resolutions;
    private final List<ElectronicInvoicingProvider> providers;

    InvoiceSoporte(BillingSettingsRepository settings, InvoiceRepository invoices, ResolutionRepository resolutions,
                   List<ElectronicInvoicingProvider> providers) {
        this.settings = settings;
        this.invoices = invoices;
        this.resolutions = resolutions;
        this.providers = providers;
    }

    /** Emisor listo para facturar (si no, 409 con lo que falta). */
    BillingSettings readyIssuer() {
        BillingSettings issuer = settings.get();
        if (!issuer.ready()) {
            throw new ConflictException("Completa los datos del emisor antes de facturar: "
                    + String.join(", ", issuer.missing()) + ".");
        }
        provider(issuer);
        return issuer;
    }

    BillingSettings issuer() {
        return settings.get();
    }

    ElectronicInvoicingProvider provider(BillingSettings issuer) {
        return providers.stream().filter(p -> p.code().equals(issuer.provider())).findFirst()
                .filter(ElectronicInvoicingProvider::configured)
                .orElseThrow(() -> new ConflictException("El proveedor de facturación " + issuer.provider()
                        + " no está configurado."));
    }

    Invoice locked(Long id) {
        return invoices.lockById(id).orElseThrow(() -> new NotFoundException("No se encontró el documento " + id + "."));
    }

    Invoice get(Long id) {
        return invoices.findById(id).orElseThrow(() -> new NotFoundException("No se encontró el documento " + id + "."));
    }

    static LocalDate today() {
        return LocalDate.now(BOGOTA);
    }

    static Instant now() {
        return Instant.now();
    }

    /** Orden pagada y con líneas: lista para facturar. */
    static void checkBillable(BillableOrder order) {
        if (order.thirdPartySale()) {
            throw new ConflictException("La orden " + order.id() + " es un subpedido de un vendedor externo: la factura "
                    + "al comprador la emite ese vendedor.");
        }
        if (!PAID_STATES.contains(order.state())) {
            throw new ConflictException("Solo se facturan órdenes pagadas (la orden " + order.id() + " está "
                    + order.state() + ").");
        }
        if (order.lines().isEmpty() || order.total() == null || order.total().signum() <= 0) {
            throw new ConflictException("La orden " + order.id() + " no tiene líneas para facturar.");
        }
    }

    static List<InvoiceLine> linesOf(BillableOrder order) {
        List<InvoiceLine> lines = new ArrayList<>();
        int position = 1;
        for (BillableLine line : order.lines()) {
            lines.add(new InvoiceLine(null, position++, line.lineId(), line.skuCode(), line.description(), line.quantity(),
                    line.unitPrice(), line.discount(), line.taxCode(), line.taxKind(), line.taxRate(), line.taxBase(),
                    line.taxAmount(), line.total()));
        }
        return lines;
    }

    /** Copia de las líneas de la factura (nota crédito total). */
    static List<InvoiceLine> copyLines(Invoice invoice) {
        return invoice.lines().stream()
                .map(l -> new InvoiceLine(null, l.position(), l.orderLineId(), l.skuCode(), l.description(), l.quantity(),
                        l.unitPrice(), l.discount(), l.taxCode(), l.taxKind(), l.taxRate(), l.taxBase(), l.taxAmount(),
                        l.total()))
                .toList();
    }

    static String paymentMeans(List<String> kinds) {
        Set<String> means = new LinkedHashSet<>();
        for (String kind : kinds) {
            means.add(MEANS.getOrDefault(kind, "ZZZ Otro"));
        }
        return means.isEmpty() ? MEANS.get("CASH") : String.join(", ", means);
    }

    /** Comprador congelado en la orden (V16); null = consumidor final. */
    static BuyerDto buyerFrom(com.essenza.draco.shared.common.sales.OrderBilling.BillableBuyer buyer) {
        if (buyer == null || buyer.docNumber() == null || buyer.docNumber().isBlank()) {
            return null;
        }
        return BuyerDto.builder().docType(buyer.docType() == null ? "CC" : buyer.docType()).docNumber(buyer.docNumber())
                .name(buyer.name()).email(buyer.email()).phone(buyer.phone()).address(buyer.address()).city(buyer.city())
                .build();
    }

    static Buyer buyerOf(BuyerDto dto) {
        if (dto == null || dto.getDocNumber() == null || dto.getDocNumber().isBlank()) {
            return Buyer.finalConsumer();
        }
        return new Buyer(dto.getDocType(), dto.getDocNumber(), null, dto.getName(), dto.getEmail(), dto.getPhone(),
                dto.getAddress(), dto.getCity());
    }

    /**
     * Envía al proveedor y aplica el resultado. Una nota crédito aceptada anula su factura.
     * Un fallo de comunicación deja el documento PENDING con la nota del error.
     */
    Invoice send(Invoice document, BillingSettings issuer) {
        NumberingResolution resolution = resolutions.findById(document.resolutionId())
                .orElseThrow(() -> new IllegalStateException("Numeración no encontrada: " + document.resolutionId()));
        Invoice related = document.relatedInvoiceId() == null ? null : locked(document.relatedInvoiceId());
        try {
            ElectronicInvoicingProvider.Result result = provider(issuer).send(document, resolution, issuer, related);
            if (result.accepted()) {
                document.markIssued(result.cufe(), result.qrData(), result.providerReference(), result.message());
            } else {
                document.markRejected(result.message());
            }
        } catch (ElectronicInvoicingProvider.ProviderException ex) {
            document.annotate("No se pudo enviar al proveedor: " + ex.getMessage());
        }
        Invoice saved = invoices.save(document);
        if (saved.kind() == DocumentKind.CREDIT_NOTE && saved.status() == InvoiceStatus.ISSUED && related != null
                && related.status() == InvoiceStatus.ISSUED) {
            if (!saved.partial()) {
                related.markVoided("Anulada con la nota crédito " + saved.fullNumber() + ".");
                invoices.save(related);
            } else if (issuedCredits(related.id()).compareTo(related.total()) >= 0) {
                // Las notas parciales ya cubren toda la factura: queda anulada.
                related.markVoided("Acreditada por completo con notas crédito parciales (última " + saved.fullNumber() + ").");
                invoices.save(related);
            }
        }
        return saved;
    }

    /** Suma de las notas crédito emitidas de una factura. */
    BigDecimal issuedCredits(Long invoiceId) {
        return invoices.creditNotesFor(invoiceId).stream().filter(note -> note.status() == InvoiceStatus.ISSUED)
                .map(Invoice::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Suma de las notas crédito vigentes (emitidas o en trámite; no rechazadas). */
    BigDecimal activeCredits(Long invoiceId) {
        return invoices.creditNotesFor(invoiceId).stream().filter(note -> note.status() != InvoiceStatus.REJECTED)
                .map(Invoice::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    InvoiceDto toDto(Invoice invoice) {
        NumberingResolution resolution = resolutions.findById(invoice.resolutionId()).orElse(null);
        String relatedNumber = null;
        Long relatedId = invoice.relatedInvoiceId();
        if (relatedId != null) {
            relatedNumber = invoices.findById(relatedId).map(Invoice::fullNumber).orElse(null);
        } else if (invoice.kind() == DocumentKind.INVOICE) {
            var note = invoices.creditNoteFor(invoice.id());
            if (note.isPresent()) {
                relatedId = note.get().id();
                relatedNumber = note.get().fullNumber();
            }
        }
        BigDecimal credited = invoice.kind() == DocumentKind.INVOICE ? activeCredits(invoice.id()) : null;
        List<String> actions = new ArrayList<>();
        if (invoice.canRetry()) {
            actions.add("retry");
        }
        if (invoice.canCredit() && relatedId == null && (credited == null || credited.signum() == 0)) {
            actions.add("credit_note");
        }
        return base(invoice)
                .relatedInvoiceId(relatedId)
                .relatedNumber(relatedNumber)
                .buyer(buyerDto(invoice.buyer(), true))
                .paymentForm(invoice.paymentForm())
                .qrData(invoice.qrData())
                .verificationUrl(invoice.cufe() == null ? null : Cufe.verificationUrl(invoice.cufe(), invoice.environment()))
                .provider(invoice.provider())
                .providerReference(invoice.providerReference())
                .reason(invoice.reason())
                .resolutionNumber(resolution == null ? null : resolution.resolutionNumber())
                .resolutionRange(resolution == null ? null : resolution.label())
                .resolutionValidTo(resolution == null ? null : resolution.validTo())
                .lines(invoice.lines().stream().map(InvoiceSoporte::lineDto).toList())
                .taxes(taxes(invoice))
                .actions(actions)
                .creditedAmount(credited)
                .build();
    }

    /** Listado: sin líneas ni consultas extra. */
    static InvoiceDto summaryDto(Invoice invoice) {
        return base(invoice)
                .relatedInvoiceId(invoice.relatedInvoiceId())
                .buyer(buyerDto(invoice.buyer(), false))
                .actions(invoice.canRetry() ? List.of("retry") : invoice.canCredit() ? List.of("credit_note") : List.of())
                .build();
    }

    private static InvoiceDto.InvoiceDtoBuilder base(Invoice invoice) {
        return InvoiceDto.builder()
                .id(invoice.id())
                .kind(invoice.kind().name())
                .orderId(invoice.orderId())
                .prefix(invoice.prefix())
                .number(invoice.number())
                .fullNumber(invoice.fullNumber())
                .issuedAt(invoice.issuedAt())
                .status(invoice.status().name())
                .environment(invoice.environment().name())
                .netAmount(invoice.netAmount())
                .taxAmount(invoice.taxAmount())
                .discountAmount(invoice.discountAmount())
                .total(invoice.total())
                .currency("COP")
                .paymentMeans(invoice.paymentMeans())
                .cufe(invoice.cufe())
                .statusDetail(invoice.statusDetail())
                .creditScope(invoice.kind() == DocumentKind.CREDIT_NOTE ? (invoice.partial() ? "PARTIAL" : "TOTAL") : null)
                .devolutionId(invoice.devolutionId());
    }

    private static BuyerDto buyerDto(Buyer b, boolean full) {
        BuyerDto.BuyerDtoBuilder dto = BuyerDto.builder().docType(b.docType()).docNumber(b.docNumber()).dv(b.dv())
                .name(b.name()).email(b.email());
        if (full) {
            dto.phone(b.phone()).address(b.address()).city(b.city());
        }
        return dto.build();
    }

    private static InvoiceLineDto lineDto(InvoiceLine l) {
        return InvoiceLineDto.builder()
                .id(l.id())
                .position(l.position())
                .orderLineId(l.orderLineId())
                .skuCode(l.skuCode())
                .description(l.description())
                .quantity(l.quantity())
                .unitPrice(l.unitPrice())
                .discount(l.discount())
                .taxCode(l.taxCode())
                .taxKind(l.taxKind())
                .taxRate(l.taxRate())
                .taxBase(l.taxBase())
                .taxAmount(l.taxAmount())
                .total(l.total())
                .build();
    }

    /** Impuestos agrupados por tarifa. */
    static List<InvoiceTaxDto> taxes(Invoice invoice) {
        Map<String, InvoiceTaxDto> byRate = new LinkedHashMap<>();
        for (InvoiceLine l : invoice.lines()) {
            String key = (l.taxCode() == null ? "-" : l.taxCode()) + "|" + l.taxRate();
            InvoiceTaxDto current = byRate.get(key);
            if (current == null) {
                byRate.put(key, InvoiceTaxDto.builder().taxCode(l.taxCode()).taxKind(l.taxKind()).taxRate(l.taxRate())
                        .taxBase(l.taxBase()).taxAmount(l.taxAmount()).build());
            } else {
                current.setTaxBase(current.getTaxBase().add(l.taxBase()));
                current.setTaxAmount(current.getTaxAmount().add(l.taxAmount()));
            }
        }
        return new ArrayList<>(byRate.values());
    }
}
