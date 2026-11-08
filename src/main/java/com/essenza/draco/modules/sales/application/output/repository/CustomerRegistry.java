package com.essenza.draco.modules.sales.application.output.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.essenza.draco.modules.sales.domain.model.customer.CustomerProfile;

/** Clientes persistentes por empresa y su actividad (órdenes relacionadas por documento). */
public interface CustomerRegistry {

    long insert(long companyId, CustomerProfile profile, String source, String actorId, Instant now);

    /** @return false si {@code lockVersion} no coincide */
    boolean update(long companyId, long customerId, int lockVersion, CustomerProfile profile, String actorId, Instant now);

    Optional<CustomerRow> find(long companyId, long customerId);

    Page<CustomerRow> search(long companyId, String q, Pageable pageable);

    /** Crea los clientes de los documentos que aparecen en órdenes y aún no están registrados. */
    int importFromOrders(long companyId, String actorId, Instant now);

    /**
     * Crea el cliente desde una orden (origen ORDERS) o completa solo los datos vacíos (correo,
     * teléfono, dirección, ciudad, usuario de la tienda). No cambia nombre ni consentimiento.
     *
     * @return true si el cliente quedó registrado (el conteo de filas del driver no distingue "sin cambios")
     */
    boolean upsertFromOrder(long companyId, com.essenza.draco.modules.sales.domain.model.order.CustomerInfo info,
                            String actorId, Instant now);

    /** Id del cliente con ese documento en la empresa. */
    Optional<Long> findIdByDocument(long companyId, String docType, String docNumber);

    Activity activity(String docType, String docNumber);

    List<RecentOrder> recentOrders(String docType, String docNumber, int limit);

    /** Devoluciones de las órdenes del cliente: resumen y las más recientes. */
    Returns returns(String docType, String docNumber, int limit);

    /** Facturas y notas crédito de las órdenes del cliente: resumen y las más recientes. */
    Invoices invoices(String docType, String docNumber, int limit);

    record Returns(long total, long open, BigDecimal refunded, List<ReturnRow> recent) {
    }

    record ReturnRow(long id, long orderId, String orderNumber, String state, BigDecimal requested, BigDecimal refunded,
                     Instant createdAt) {
    }

    record Invoices(long invoices, long creditNotes, long pending, BigDecimal invoiced, List<InvoiceRow> recent) {
    }

    record InvoiceRow(long id, long orderId, String kind, String number, String status, BigDecimal total, Instant issuedAt) {
    }

    record CustomerRow(long id, String kind, String docType, String docNumber, String name, String email, String phone,
                       String billingAddress, String billingCity, Long storeUserId, boolean marketingConsent,
                       Instant consentAt, String notes, String source, int lockVersion, Instant createdAt, String createdBy,
                       Instant updatedAt, String updatedBy, long ordersCount, BigDecimal totalSpent, Instant lastOrderAt) {
    }

    /**
     * Actividad del cliente en las órdenes: cantidad (todas y sin canceladas), lo comprado neto de
     * devoluciones, lo devuelto, lo pendiente de pago, compras multivendedor y fechas.
     */
    record Activity(long orders, long activeOrders, long cancelled, long pendingOrders, BigDecimal spent, BigDecimal returned,
                    BigDecimal pendingAmount, BigDecimal marketplaceSpent, long purchases, Instant firstOrderAt,
                    Instant lastOrderAt, List<ChannelTotal> byChannel) {
    }

    record ChannelTotal(String channel, long orders, BigDecimal spent) {
    }

    record RecentOrder(long id, String orderNumber, String state, BigDecimal total, BigDecimal returned, String channel,
                       boolean thirdParty, Long purchaseId, Instant createdAt) {
    }
}
