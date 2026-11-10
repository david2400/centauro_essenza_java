package com.essenza.draco.modules.billing.application.services.settings;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.billing.application.dto.settings.BillingSettingsDto;
import com.essenza.draco.modules.billing.application.dto.settings.SaveBillingSettingsDto;
import com.essenza.draco.modules.billing.application.input.settings.UpdateBillingSettingsUseCase;
import com.essenza.draco.modules.billing.application.output.provider.ElectronicInvoicingProvider;
import com.essenza.draco.modules.billing.application.output.repository.BillingSettingsRepository;
import com.essenza.draco.modules.billing.domain.model.BillingEnvironment;
import com.essenza.draco.modules.billing.domain.model.BillingRuleViolation;
import com.essenza.draco.modules.billing.domain.model.BillingSettings;

/** Reemplaza los datos del emisor; un PIN vacío conserva el actual. */
@Service
@Transactional
public class UpdateBillingSettingsService implements UpdateBillingSettingsUseCase {

    private final BillingSettingsRepository settings;
    private final List<ElectronicInvoicingProvider> providers;

    public UpdateBillingSettingsService(BillingSettingsRepository settings, List<ElectronicInvoicingProvider> providers) {
        this.settings = settings;
        this.providers = providers;
    }

    @Override
    public BillingSettingsDto update(SaveBillingSettingsDto in) {
        BillingSettings current = settings.get();
        String provider = in.getProvider() == null || in.getProvider().isBlank() ? current.provider()
                : in.getProvider().trim().toUpperCase(java.util.Locale.ROOT);
        if (providers.stream().noneMatch(p -> p.code().equals(provider))) {
            throw new BillingRuleViolation("Proveedor de facturación no disponible: " + provider + ".");
        }
        BillingSettings next = new BillingSettings(in.getNit(), in.getLegalName(), in.getTradeName(), in.getTaxRegime(),
                in.getResponsibilities(), in.getAddress(), in.getCity(), in.getDepartment(), in.getEmail(), in.getPhone(),
                in.getEnvironment() == null ? current.environment() : BillingEnvironment.parse(in.getEnvironment()),
                provider, in.getSoftwareId(),
                in.getSoftwarePin() == null || in.getSoftwarePin().isBlank() ? current.softwarePin() : in.getSoftwarePin(),
                in.getAutoInvoice() != null ? in.getAutoInvoice() : current.autoInvoice(), in.getInvoiceFooter(),
                in.getCountryId(), in.getStateId(), in.getCityId());
        return GetBillingSettingsService.toDto(settings.save(next), providers);
    }
}
