package com.essenza.draco.modules.billing.application.dto.settings;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Datos del emisor y de la facturación electrónica. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingSettingsDto {
    private String nit;
    @Schema(description = "Dígito de verificación (calculado)")
    private String dv;
    private String legalName;
    private String tradeName;
    @Schema(description = "RESPONSABLE_IVA o NO_RESPONSABLE_IVA")
    private String taxRegime;
    @Schema(description = "Responsabilidades fiscales (p. ej. R-99-PN, O-13)")
    private String responsibilities;
    private String address;
    private String city;
    private String department;
    @Schema(description = "País (catálogo del servicio parametros)")
    private Long countryId;
    @Schema(description = "Departamento (catálogo del servicio parametros)")
    private Long stateId;
    @Schema(description = "Ciudad (catálogo del servicio parametros); city y department guardan sus nombres")
    private Long cityId;
    private String email;
    private String phone;
    @Schema(description = "TEST o PRODUCTION")
    private String environment;
    @Schema(description = "Proveedor tecnológico: SIMULATOR")
    private String provider;
    private String softwareId;
    @Schema(description = "PIN del software (solo se informa si está configurado)")
    private Boolean softwarePinSet;
    private Boolean autoInvoice;
    private String invoiceFooter;
    @Schema(description = "Datos mínimos completos para facturar")
    private Boolean ready;
    @Schema(description = "Qué falta para poder facturar")
    private java.util.List<String> missing;
    private Boolean providerConfigured;
}
