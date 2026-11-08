package com.essenza.draco.modules.sales.application.dto.customer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Clientes persistentes y ficha 360 (contrato JSON en snake_case). */
public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record SaveCustomerRequest(String kind, String docType, String docNumber, String name, String email, String phone,
                                      String billingAddress, String billingCity, Long storeUserId, Boolean marketingConsent,
                                      String notes, Integer lockVersion) {
    }

    public record CustomerSummaryDto(Long id, String kind, String docType, String docNumber, String name, String email,
                                     String phone, String billingCity, boolean marketingConsent, long ordersCount,
                                     BigDecimal totalSpent, Instant lastOrderAt) {
    }

    public record ChannelTotalDto(String channel, long orders, BigDecimal spent) {
    }

    public record RecentOrderDto(Long id, String orderNumber, String state, BigDecimal total, BigDecimal returned,
                                 String channel, boolean thirdParty, Long purchaseId, Instant createdAt) {
    }

    /**
     * Indicadores de la ficha 360. {@code spent} es lo comprado sin canceladas y neto de devoluciones;
     * {@code ownSpent} excluye los subpedidos de vendedores externos.
     */
    public record CustomerOverviewDto(long orders, long activeOrders, long cancelled, long pendingOrders, BigDecimal spent,
                                      BigDecimal ownSpent, BigDecimal marketplaceSpent, BigDecimal returned,
                                      BigDecimal pendingAmount, BigDecimal averageTicket, long purchases, Instant firstOrderAt,
                                      Instant lastOrderAt, Long daysSinceLastOrder, List<ChannelTotalDto> byChannel,
                                      List<RecentOrderDto> recentOrders) {
    }

    public record CustomerDetailDto(Long id, String kind, String docType, String docNumber, String name, String email,
                                    String phone, String billingAddress, String billingCity, Long storeUserId,
                                    boolean marketingConsent, Instant consentAt, String notes, String source,
                                    int lockVersion, Instant createdAt, String createdBy, Instant updatedAt, String updatedBy,
                                    CustomerOverviewDto overview, CustomerAfterSalesDto afterSales) {
    }

    /**
     * Posventa del cliente. Cada bloque sale solo si el usuario puede ver ese módulo
     * ({@code null} si no): devoluciones con POSTVENTA.VER y facturas con FACTURACION.VER.
     */
    public record CustomerAfterSalesDto(ReturnsDto returns, InvoicesDto invoices) {
    }

    /** {@code open}: pendientes, aprobadas, recibidas o inspeccionadas (aún sin reembolso ni rechazo). */
    public record ReturnsDto(long total, long open, BigDecimal refunded, List<CustomerReturnDto> recent) {
    }

    public record CustomerReturnDto(Long id, Long orderId, String orderNumber, String state, BigDecimal requested,
                                    BigDecimal refunded, Instant createdAt) {
    }

    /** {@code invoiced}: facturas de venta emitidas (sin anuladas) menos notas crédito emitidas. */
    public record InvoicesDto(long invoices, long creditNotes, long pending, BigDecimal invoiced,
                              List<CustomerInvoiceDto> recent) {
    }

    public record CustomerInvoiceDto(Long id, Long orderId, String kind, String number, String status, BigDecimal total,
                                     Instant issuedAt) {
    }

    public record ImportResultDto(int created) {
    }

    /** Cliente registrado con ese documento; {@code id} nulo si aún no existe. */
    public record CustomerLinkDto(Long id, String docType, String docNumber) {
    }
}
