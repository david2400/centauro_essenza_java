package com.essenza.draco.modules.sales.infrastructure.outbound.persistence.mysql.shop;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.essenza.draco.shared.common.domain.entity.AuditInfo;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.CheckConstraint;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@Entity
@Table(name = "cash_registers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cash_registers_code_active", columnNames = {"code_active"})},
        check = {
                @CheckConstraint(name = "ck_cash_registers_opening", constraint = "opening_mode IN ('FREE', 'SUGGESTED', 'FIXED')"),
                @CheckConstraint(name = "ck_cash_registers_receipt", constraint = "receipt_width IN ('58MM', '80MM')")})
public class CashRegisterEntity extends AuditInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cash_register")
    private Long id;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "location", length = 160)
    private String location;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    /** V11: punto de venta y reglas de la caja. */
    @Column(name = "store_id")
    private Long storeId;

    @Column(name = "device_name", length = 80)
    private String deviceName;

    @Column(name = "opening_mode", nullable = false, length = 10)
    private String openingMode = "FREE";

    @Column(name = "default_opening_amount", precision = 15, scale = 2)
    private BigDecimal defaultOpeningAmount;

    @Column(name = "cash_limit", precision = 15, scale = 2)
    private BigDecimal cashLimit;

    @Column(name = "blind_close", nullable = false)
    private Boolean blindClose = false;

    @Column(name = "difference_tolerance", nullable = false, precision = 15, scale = 2)
    private BigDecimal differenceTolerance = BigDecimal.ZERO;

    @Column(name = "max_discount_percent", precision = 5, scale = 2)
    private BigDecimal maxDiscountPercent;

    @Column(name = "auto_invoice", nullable = false)
    private Boolean autoInvoice = false;

    @Column(name = "receipt_width", nullable = false, length = 5)
    private String receiptWidth = "80MM";

    /** Medios de pago permitidos (vacío = todos). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cash_register_methods", joinColumns = @JoinColumn(name = "cash_register_id"))
    @Column(name = "payment_method_id")
    private Set<Long> allowedMethodIds = new LinkedHashSet<>();

    /** Columna generada (solo lectura para JPA). */
    @Column(name = "code_active", columnDefinition = "varchar(30) generated always as (if(deleted = 0, code, null)) stored", insertable = false, updatable = false)
    private String codeActive;

}
