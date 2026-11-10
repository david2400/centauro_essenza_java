package com.essenza.draco.modules.billing.application.services.settings;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.billing.application.dto.settings.BillingSettingsDto;
import com.essenza.draco.modules.billing.application.input.settings.GetBillingSettingsUseCase;
import com.essenza.draco.modules.billing.application.output.provider.ElectronicInvoicingProvider;
import com.essenza.draco.modules.billing.application.output.repository.BillingSettingsRepository;
import com.essenza.draco.modules.billing.domain.model.BillingSettings;

@Service
@Transactional(readOnly = true)
public class GetBillingSettingsService implements GetBillingSettingsUseCase {

    private final BillingSettingsRepository settings;
    private final List<ElectronicInvoicingProvider> providers;

    public GetBillingSettingsService(BillingSettingsRepository settings, List<ElectronicInvoicingProvider> providers) {
        this.settings = settings;
        this.providers = providers;
    }

    @Override
    public BillingSettingsDto get() {
        return toDto(settings.get(), providers);
    }

    static BillingSettingsDto toDto(BillingSettings s, List<ElectronicInvoicingProvider> providers) {
        boolean configured = providers.stream().anyMatch(p -> p.code().equals(s.provider()) && p.configured());
        return BillingSettingsDto.builder()
                .nit(s.nit())
                .dv(s.dv())
                .legalName(s.legalName())
                .tradeName(s.tradeName())
                .taxRegime(s.taxRegime())
                .responsibilities(s.responsibilities())
                .address(s.address())
                .city(s.city())
                .department(s.department())
                .countryId(s.countryId())
                .stateId(s.stateId())
                .cityId(s.cityId())
                .email(s.email())
                .phone(s.phone())
                .environment(s.environment().name())
                .provider(s.provider())
                .softwareId(s.softwareId())
                .softwarePinSet(s.softwarePin() != null)
                .autoInvoice(s.autoInvoice())
                .invoiceFooter(s.invoiceFooter())
                .ready(s.ready() && configured)
                .missing(s.missing())
                .providerConfigured(configured)
                .build();
    }
}
