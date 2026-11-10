package com.essenza.draco.modules.billing.infrastructure.outbound.repositories;

import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.billing.application.output.repository.BillingSettingsRepository;
import com.essenza.draco.modules.billing.domain.model.BillingEnvironment;
import com.essenza.draco.modules.billing.domain.model.BillingSettings;
import com.essenza.draco.modules.billing.infrastructure.outbound.persistence.BillingSettingsEntity;

@Repository
public class BillingSettingsRepositoryAdapter implements BillingSettingsRepository {

    private final JpaBillingSettingsRepository jpa;

    public BillingSettingsRepositoryAdapter(JpaBillingSettingsRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public BillingSettings get() {
        return toDomain(jpa.findById(BillingSettingsEntity.SINGLETON_ID).orElseGet(BillingSettingsEntity::new));
    }

    @Override
    public BillingSettings save(BillingSettings s) {
        BillingSettingsEntity e = jpa.findById(BillingSettingsEntity.SINGLETON_ID).orElseGet(BillingSettingsEntity::new);
        e.setNit(s.nit());
        e.setLegalName(s.legalName());
        e.setTradeName(s.tradeName());
        e.setTaxRegime(s.taxRegime());
        e.setResponsibilities(s.responsibilities());
        e.setAddress(s.address());
        e.setCity(s.city());
        e.setDepartment(s.department());
        e.setCountryId(s.countryId());
        e.setStateId(s.stateId());
        e.setCityId(s.cityId());
        e.setEmail(s.email());
        e.setPhone(s.phone());
        e.setEnvironment(s.environment().name());
        e.setProvider(s.provider());
        e.setSoftwareId(s.softwareId());
        e.setSoftwarePin(s.softwarePin());
        e.setAutoInvoice(s.autoInvoice());
        e.setInvoiceFooter(s.invoiceFooter());
        return toDomain(jpa.saveAndFlush(e));
    }

    static BillingSettings toDomain(BillingSettingsEntity e) {
        return new BillingSettings(e.getNit(), e.getLegalName(), e.getTradeName(), e.getTaxRegime(), e.getResponsibilities(),
                e.getAddress(), e.getCity(), e.getDepartment(), e.getEmail(), e.getPhone(),
                BillingEnvironment.parse(e.getEnvironment()), e.getProvider(), e.getSoftwareId(), e.getSoftwarePin(),
                Boolean.TRUE.equals(e.getAutoInvoice()), e.getInvoiceFooter(), e.getCountryId(), e.getStateId(), e.getCityId());
    }
}
