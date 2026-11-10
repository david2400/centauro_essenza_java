package com.essenza.draco.modules.marketplace.infrastructure.outbound.persistence.schema;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Tabla {@code mp_seller_bank_accounts}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "mp_seller_bank_accounts",
        indexes = {
        @Index(name = "fk_mp_seller_bank_accounts_bank", columnList = "bank_id"),
        @Index(name = "ix_mp_seller_bank_accounts_company", columnList = "company_id")},
        check = {
        @CheckConstraint(name = "ck_mp_seller_bank_accounts_type", constraint = "account_type in ('SAVINGS','CHECKING','DIGITAL_WALLET','BRE_B_KEY')")})
public class MpSellerBankAccountEntity {

    @Id
    @Column(name = "seller_id", columnDefinition = "bigint", nullable = false)
    private Long sellerId;

    @Column(name = "company_id", columnDefinition = "bigint not null")
    private Long companyId;

    @Column(name = "bank_id", columnDefinition = "bigint")
    private Long bankId;

    @Column(name = "bank_name", columnDefinition = "varchar(80) not null")
    private String bankName;

    @Column(name = "account_type", columnDefinition = "varchar(20) not null")
    private String accountType;

    @Column(name = "key_type", columnDefinition = "varchar(20)")
    private String keyType;

    @Column(name = "account_number", columnDefinition = "varchar(120) not null")
    private String accountNumber;

    @Column(name = "holder_name", columnDefinition = "varchar(160) not null")
    private String holderName;

    @Column(name = "holder_doc_type", columnDefinition = "varchar(10) not null")
    private String holderDocType;

    @Column(name = "holder_doc_number", columnDefinition = "varchar(30) not null")
    private String holderDocNumber;

    @Column(name = "updated_by", columnDefinition = "varchar(64) not null")
    private String updatedBy;

    @Column(name = "updated_by_name", columnDefinition = "varchar(120)")
    private String updatedByName;

    @Column(name = "updated_at", columnDefinition = "datetime(6) not null")
    private java.time.LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_seller_bank_accounts_bank"))
    private MpBankEntity bank;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_seller_bank_accounts_seller"))
    private MpSellerEntity seller;
}
