package com.essenza.draco.modules.inventory.infrastructure.outbound.persistence.mysql.shop;

import com.essenza.draco.shared.common.domain.entity.AuditInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Index;
import jakarta.persistence.CheckConstraint;

/** Proveedor. V13: ficha empresarial (fiscal, ubicación, estado, condiciones y banco). */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@Entity
@Table(name = "suppliers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_suppliers_code_active", columnNames = {"code_active"}),
                @UniqueConstraint(name = "uk_suppliers_document_active", columnNames = {"document_active"})},
        indexes = {
                @Index(name = "ix_suppliers_status", columnList = "status")},
        check = {
                @CheckConstraint(name = "ck_suppliers_status", constraint = "status IN ('ACTIVE', 'BLOCKED', 'INACTIVE')"),
                @CheckConstraint(name = "ck_suppliers_person", constraint = "person_type IN ('NATURAL', 'JURIDICA')")})
public class SupplierEntity extends AuditInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_supplier")
    private Long id;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "doc_type", length = 10)
    private String docType;

    @Column(name = "doc_number", length = 20)
    private String docNumber;

    @Column(name = "dv", length = 1)
    private String dv;

    @Column(name = "person_type", nullable = false, length = 10)
    private String personType;

    /** Razón social o nombre completo. */
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "trade_name", length = 160)
    private String tradeName;

    @Column(name = "tax_regime", length = 20)
    private String taxRegime;

    @Column(name = "great_contributor", nullable = false)
    private Boolean greatContributor;

    @Column(name = "self_retainer", nullable = false)
    private Boolean selfRetainer;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "website", length = 160)
    private String website;

    @Column(name = "address")
    private String address;

    /** Ids del catálogo de georeferencing del servicio parametros (sin FK: otra base de datos). */
    @Column(name = "country_id")
    private Long countryId;

    @Column(name = "state_id")
    private Long stateId;

    @Column(name = "city_id")
    private Long cityId;

    @Column(name = "city", length = 80)
    private String city;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "block_reason")
    private String blockReason;

    @Column(name = "payment_term_days", nullable = false)
    private Integer paymentTermDays;

    @Column(name = "credit_limit", precision = 15, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "min_order_amount", precision = 15, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "discount_percent", precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(name = "bank_name", length = 80)
    private String bankName;

    @Column(name = "bank_account_type", length = 10)
    private String bankAccountType;

    @Column(name = "bank_account_number", length = 30)
    private String bankAccountNumber;

    @Column(name = "bank_account_holder", length = 160)
    private String bankAccountHolder;

    @Column(name = "bank_holder_document", length = 20)
    private String bankHolderDocument;

    @Column(name = "notes", length = 1000)
    private String notes;

    /** Columna generada (solo lectura para JPA). */
    @Column(name = "code_active", columnDefinition = "varchar(30) generated always as (if(deleted = 0, code, null)) stored", insertable = false, updatable = false)
    private String codeActive;

    /** Columna generada (solo lectura para JPA). */
    @Column(name = "document_active", columnDefinition = "varchar(31) generated always as (if(deleted = 0 and doc_number is not null, concat(doc_type, ':', doc_number), null)) stored", insertable = false, updatable = false)
    private String documentActive;
}
