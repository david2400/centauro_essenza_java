package com.essenza.draco.modules.billing.infrastructure.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.essenza.draco.shared.common.domain.entity.AuditInfo;

/** Fila única (id = 1) con los datos del emisor. */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@Entity
@Table(name = "billing_settings")
public class BillingSettingsEntity extends AuditInfo {

    public static final long SINGLETON_ID = 1L;

    @Id
    @Column(name = "id_billing_settings")
    private Long id = SINGLETON_ID;

    @Column(name = "nit", length = 20)
    private String nit;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(name = "trade_name", length = 200)
    private String tradeName;

    @Column(name = "tax_regime", nullable = false, length = 30)
    private String taxRegime = "RESPONSABLE_IVA";

    @Column(name = "responsibilities", length = 120)
    private String responsibilities;

    @Column(name = "address", length = 200)
    private String address;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "department", length = 120)
    private String department;

    /** Ubicación en el catálogo del servicio parametros (city y department guardan los nombres). */
    @Column(name = "country_id")
    private Long countryId;

    @Column(name = "state_id")
    private Long stateId;

    @Column(name = "city_id")
    private Long cityId;

    @Column(name = "email", length = 160)
    private String email;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "environment", nullable = false, length = 20)
    private String environment = "TEST";

    @Column(name = "provider", nullable = false, length = 30)
    private String provider = "SIMULATOR";

    @Column(name = "software_id", length = 80)
    private String softwareId;

    @Column(name = "software_pin", length = 40)
    private String softwarePin;

    @Column(name = "auto_invoice", nullable = false)
    private Boolean autoInvoice = false;

    @Column(name = "invoice_footer", length = 500)
    private String invoiceFooter;
}
