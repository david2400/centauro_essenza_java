package com.essenza.draco.modules.billing.infrastructure.outbound.persistence;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.Index;

/** Línea de factura (inmutable; sin auditoría ni borrado lógico). */
@Data
@NoArgsConstructor
@Entity
@Table(name = "invoice_lines",
        indexes = {
                @Index(name = "idx_invoice_lines_invoice", columnList = "invoice_id, position")})
public class InvoiceLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_invoice_line")
    private Long id;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "order_line_id")
    private Long orderLineId;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "sku_code", length = 64)
    private String skuCode;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "tax_code", length = 30)
    private String taxCode;

    @Column(name = "tax_kind", length = 20)
    private String taxKind;

    @Column(name = "tax_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "tax_base", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxBase;

    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total", nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

}
