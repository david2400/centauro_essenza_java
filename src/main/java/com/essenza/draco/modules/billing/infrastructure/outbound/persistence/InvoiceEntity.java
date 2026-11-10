package com.essenza.draco.modules.billing.infrastructure.outbound.persistence;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.essenza.draco.shared.common.domain.entity.AuditInfo;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Index;
import jakarta.persistence.CheckConstraint;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@Entity
@Table(name = "invoices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_invoices_number", columnNames = {"kind", "prefix", "number"}),
                @UniqueConstraint(name = "uk_invoices_active_order", columnNames = {"active_order_invoice"}),
                @UniqueConstraint(name = "uk_invoices_devolution", columnNames = {"devolution_id"})},
        indexes = {
                @Index(name = "idx_invoices_order", columnList = "order_id"),
                @Index(name = "idx_invoices_status_issued", columnList = "status, issued_at")},
        check = {
                @CheckConstraint(name = "ck_invoices_kind", constraint = "kind IN ('INVOICE', 'CREDIT_NOTE')"),
                @CheckConstraint(name = "ck_invoices_status", constraint = "status IN ('PENDING', 'ISSUED', 'REJECTED', 'VOIDED')")})
public class InvoiceEntity extends AuditInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_invoice")
    private Long id;

    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "related_invoice_id")
    private Long relatedInvoiceId;

    @Column(name = "resolution_id", nullable = false)
    private Long resolutionId;

    @Column(name = "prefix", nullable = false, length = 10)
    private String prefix = "";

    @Column(name = "number", nullable = false)
    private Long number;

    @Column(name = "full_number", nullable = false, length = 30)
    private String fullNumber;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "environment", nullable = false, length = 20)
    private String environment;

    @Column(name = "buyer_doc_type", nullable = false, length = 10)
    private String buyerDocType;

    @Column(name = "buyer_doc_number", nullable = false, length = 30)
    private String buyerDocNumber;

    @Column(name = "buyer_dv", length = 2)
    private String buyerDv;

    @Column(name = "buyer_name", nullable = false, length = 200)
    private String buyerName;

    @Column(name = "buyer_email", length = 160)
    private String buyerEmail;

    @Column(name = "buyer_phone", length = 40)
    private String buyerPhone;

    @Column(name = "buyer_address", length = 200)
    private String buyerAddress;

    @Column(name = "buyer_city", length = 120)
    private String buyerCity;

    @Column(name = "net_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "COP";

    @Column(name = "payment_form", nullable = false, length = 20)
    private String paymentForm = "CASH";

    @Column(name = "payment_means", length = 200)
    private String paymentMeans;

    @Column(name = "cufe", length = 120)
    private String cufe;

    @Column(name = "qr_data", columnDefinition = "TEXT")
    private String qrData;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_reference", length = 120)
    private String providerReference;

    @Column(name = "status_detail", length = 500)
    private String statusDetail;

    @Column(name = "reason", length = 500)
    private String reason;

    /** V10: devolución que originó la nota crédito parcial. */
    @Column(name = "devolution_id")
    private Long devolutionId;

    /** TOTAL (anula la factura) o PARTIAL (por las líneas devueltas). */
    @Column(name = "credit_scope", nullable = false, length = 10)
    private String creditScope = "TOTAL";

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    /** Columna generada (solo lectura para JPA). */
    @Column(name = "active_order_invoice", columnDefinition = "bigint generated always as (if(kind = 'INVOICE' and status <> 'VOIDED' and deleted = 0, order_id, null)) stored", insertable = false, updatable = false)
    private Long activeOrderInvoice;

}
