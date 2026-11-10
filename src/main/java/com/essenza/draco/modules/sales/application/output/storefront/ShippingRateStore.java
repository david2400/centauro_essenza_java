package com.essenza.draco.modules.sales.application.output.storefront;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.essenza.draco.modules.sales.domain.model.storefront.ShippingRates.Rate;

/** Tarifas de envío de la tienda en línea, por empresa. */
public interface ShippingRateStore {

    List<Rate> rates(long companyId);

    Optional<Rate> rate(long companyId, long id);

    /** @return false si ya existe otra tarifa para el mismo departamento y ciudad */
    boolean exists(long companyId, String stateKey, String cityKey, Long exceptId);

    long insert(long companyId, Rate rate, String actorId, String actorName, Instant now);

    void update(long companyId, long id, Rate rate, String actorId, String actorName, Instant now);

    record Audit(String updatedByName, Instant updatedAt) {
    }

    java.util.Map<Long, Audit> audits(long companyId);
}
