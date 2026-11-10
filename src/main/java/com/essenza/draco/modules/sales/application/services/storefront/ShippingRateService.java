package com.essenza.draco.modules.sales.application.services.storefront;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.SaveShippingRateRequest;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontDtos.ShippingRateDto;
import com.essenza.draco.modules.sales.application.output.storefront.ShippingRateStore;
import com.essenza.draco.modules.sales.application.services.purchase.PurchaseService;
import com.essenza.draco.modules.sales.domain.model.order.OrderRuleViolation;
import com.essenza.draco.modules.sales.domain.model.storefront.ShippingRates;
import com.essenza.draco.modules.sales.domain.model.storefront.ShippingRates.Rate;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/** Tarifas de envío de la tienda en línea (panel). Empresa y permisos del token: VENTAS.VER y VENTAS.GESTIONAR. */
@Service
@Transactional
public class ShippingRateService {

    private final ShippingRateStore store;
    private final ActorProvider actors;
    private final Clock clock;

    @Autowired
    public ShippingRateService(ShippingRateStore store, ActorProvider actors) {
        this(store, actors, Clock.systemUTC());
    }

    public ShippingRateService(ShippingRateStore store, ActorProvider actors, Clock clock) {
        this.store = store;
        this.actors = actors;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ShippingRateDto> list() {
        RequestActor actor = actors.current().requireAny(PurchaseService.SALES_VIEW, PurchaseService.SALES_MANAGE);
        Map<Long, ShippingRateStore.Audit> audits = store.audits(actor.companyId());
        return store.rates(actor.companyId()).stream().map(r -> dto(r, audits.get(r.id()))).toList();
    }

    public ShippingRateDto save(Long id, SaveShippingRateRequest request) {
        RequestActor actor = actors.current().require(PurchaseService.SALES_MANAGE);
        if (request == null) {
            throw new OrderRuleViolation("Faltan los datos de la tarifa.");
        }
        Rate current = id == null ? null
                : store.rate(actor.companyId(), id).orElseThrow(() -> new NotFoundException("Tarifa no encontrada: " + id));
        Rate rate = ShippingRates.check(request.state(), request.city(), request.amount(), request.freeFrom(), request.minDays(),
                request.maxDays(), request.active() == null ? current == null || current.active() : request.active());
        if (store.exists(actor.companyId(), rate.stateKey(), rate.cityKey(), id)) {
            throw new ConflictException("Ya hay una tarifa para " + place(rate) + ".");
        }
        long saved;
        if (id == null) {
            saved = store.insert(actor.companyId(), rate, actor.userId(), actor.displayName(), clock.instant());
        } else {
            store.update(actor.companyId(), id, rate, actor.userId(), actor.displayName(), clock.instant());
            saved = id;
        }
        Rate stored = store.rate(actor.companyId(), saved).orElseThrow();
        return dto(stored, store.audits(actor.companyId()).get(saved));
    }

    static String place(Rate r) {
        if (r.city() == null && r.state() == null) {
            return "todo el país (tarifa general)";
        }
        return r.city() == null ? r.state() : r.state() == null ? r.city() : r.city() + ", " + r.state();
    }

    private static ShippingRateDto dto(Rate r, ShippingRateStore.Audit audit) {
        return new ShippingRateDto(r.id(), r.state(), r.city(), r.amount(), r.freeFrom(), r.minDays(), r.maxDays(), r.active(),
                audit == null ? null : audit.updatedByName(), audit == null ? null : audit.updatedAt());
    }
}
