package com.essenza.draco.modules.billing.application.services.invoice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.billing.application.dto.invoice.BuyerDto;
import com.essenza.draco.modules.billing.application.dto.invoice.IssueInvoiceDto;
import com.essenza.draco.modules.billing.application.input.invoice.AutoInvoiceUseCase;
import com.essenza.draco.modules.billing.application.input.invoice.IssueInvoiceUseCase;
import com.essenza.draco.modules.billing.application.output.repository.InvoiceRepository;
import com.essenza.draco.modules.billing.domain.model.BillingSettings;
import com.essenza.draco.shared.common.sales.OrderBilling;
import com.essenza.draco.shared.common.sales.PosSaleCompletedEvent;

/** Factura automática a consumidor final tras el pago (si está activada y el emisor está listo). */
@Service
public class AutoInvoiceService implements AutoInvoiceUseCase {

    private static final Logger log = LoggerFactory.getLogger(AutoInvoiceService.class);

    private final InvoiceSoporte soporte;
    private final InvoiceRepository invoices;
    private final IssueInvoiceUseCase issue;
    private final OrderBilling orders;

    public AutoInvoiceService(InvoiceSoporte soporte, InvoiceRepository invoices, IssueInvoiceUseCase issue, OrderBilling orders) {
        this.soporte = soporte;
        this.invoices = invoices;
        this.issue = issue;
        this.orders = orders;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderPaid(Long orderId) {
        BillingSettings issuer = soporte.issuer();
        if (!issuer.autoInvoice()) {
            return;
        }
        if (!issuer.ready()) {
            log.warn("Factura automática omitida para la orden {}: faltan datos del emisor {}", orderId, issuer.missing());
            return;
        }
        if (invoices.activeInvoiceFor(orderId).isPresent()) {
            return;
        }
        // Las ventas de mostrador se facturan con PosSaleCompletedEvent (con los datos del comprador).
        // Los subpedidos de vendedores externos los factura cada vendedor (marketplace).
        if (orders.find(orderId).map(order -> "POS".equals(order.channel()) || order.thirdPartySale()).orElse(false)) {
            return;
        }
        issue.issue(IssueInvoiceDto.builder().orderId(orderId).build());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPosSale(PosSaleCompletedEvent event) {
        BillingSettings issuer = soporte.issuer();
        if (!event.autoInvoice() && !issuer.autoInvoice()) {
            return;
        }
        if (!issuer.ready()) {
            log.warn("Factura de la venta POS {} omitida: faltan datos del emisor {}", event.orderId(), issuer.missing());
            return;
        }
        if (invoices.activeInvoiceFor(event.orderId()).isPresent()) {
            return;
        }
        BuyerDto buyer = event.buyerDocNumber() == null || event.buyerDocNumber().isBlank() ? null
                : BuyerDto.builder().docType(event.buyerDocType() == null ? "CC" : event.buyerDocType())
                        .docNumber(event.buyerDocNumber().trim()).name(event.buyerName()).email(event.buyerEmail())
                        .phone(event.buyerPhone()).build();
        issue.issue(IssueInvoiceDto.builder().orderId(event.orderId()).buyer(buyer).build());
    }
}
