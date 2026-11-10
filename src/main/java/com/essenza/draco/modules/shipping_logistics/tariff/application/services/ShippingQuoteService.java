package com.essenza.draco.modules.shipping_logistics.tariff.application.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.essenza.draco.modules.shipping_logistics.tariff.application.output.TariffStore;
import com.essenza.draco.modules.shipping_logistics.tariff.application.output.TariffStore.Origin;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.Destination;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.RateCard;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.Zone;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.TariffEngine;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.TariffEngine.Piece;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.TariffRuleViolation;
import com.essenza.draco.shared.common.logistics.ShippingQuotes;

/**
 * Cotizador del tarifario para la orden, el despacho, la tienda en línea y el simulador del panel.
 *
 * <p>Lee el tarifario de una caché por empresa (se recarga cada minuto y al instante cuando se edita en esta
 * instancia): cotizar es una lectura frecuente y el tarifario cambia poco. Si el destino no tiene cobertura, lo
 * registra en {@code shipping_quote_misses} para que logística vea qué tarifas faltan; ese registro nunca hace
 * fallar la cotización.
 */
@Service
public class ShippingQuoteService implements ShippingQuotes {

    private static final Logger log = LoggerFactory.getLogger(ShippingQuoteService.class);
    static final Duration CACHE_TTL = Duration.ofSeconds(60);
    static final int MAX_ITEMS = 200;

    private final TariffStore store;
    private final Clock clock;
    private final TransactionTemplate missTx;
    private final Map<Long, Snapshot> cache = new ConcurrentHashMap<>();

    record Snapshot(List<RateCard> cards, List<Zone> zones, Instant loadedAt) {
    }

    public ShippingQuoteService(TariffStore store, @Qualifier("marketplaceClock") Clock clock, PlatformTransactionManager tx) {
        this.store = store;
        this.clock = clock;
        // Transacción propia: quien cotiza puede estar en una de solo lectura, y el registro no debe afectarla.
        this.missTx = new TransactionTemplate(tx);
        this.missTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Cotización con los artículos (pesos y medidas del catálogo). */
    @Override
    public QuoteResult quote(long companyId, QuoteRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.items().size() > MAX_ITEMS) {
            throw new TariffRuleViolation("Demasiados artículos para cotizar (máximo " + MAX_ITEMS + ").");
        }
        return quote(companyId, request, piecesOf(request.items()));
    }

    /** Cotización con paquetes escritos a mano (simulador). */
    public QuoteResult quote(long companyId, QuoteRequest request, List<Piece> pieces) {
        long started = System.nanoTime();
        Destination dest = new Destination(request.countryId(), request.stateId(), request.cityId());
        Origin origin = store.origin(request.originWarehouseId()).orElse(null);
        if (request.originWarehouseId() != null && origin == null) {
            throw new TariffRuleViolation("La bodega de origen no existe: " + request.originWarehouseId());
        }
        Snapshot snapshot = snapshot(companyId);
        List<RateCard> cards = request.carrierId() == null ? snapshot.cards()
                : snapshot.cards().stream().filter(c -> c.carrierId() == request.carrierId()).toList();
        LocalDate shipDate = request.shipDate() != null ? request.shipDate() : LocalDate.now(clock);
        TariffEngine.Result result = TariffEngine.quote(cards, snapshot.zones(), new TariffEngine.Input(
                origin == null ? null : origin.location(), dest, pieces, request.declaredValue(), request.orderSubtotal(), shipDate));
        long ms = (System.nanoTime() - started) / 1_000_000;
        if (!result.covered()) {
            log.info("shipping_quote result=no_coverage company={} dest={}:{}:{} zone={} cards={} ms={}", companyId,
                    dest.countryId(), dest.stateId(), dest.cityId(), result.zoneKey(), cards.size(), ms);
            recordMiss(companyId, dest, request.destinationLabel(), result.zoneKey());
        } else {
            log.debug("shipping_quote result=ok company={} dest={}:{}:{} zone={} options={} ms={}", companyId, dest.countryId(),
                    dest.stateId(), dest.cityId(), result.zoneKey(), result.options().size(), ms);
        }
        List<Option> options = result.options().stream().map(o -> new Option(o.card().id(), o.card().carrierId(),
                o.card().carrierName(), o.zoneKey(), o.zoneName(), o.actualKg(), o.volumetricKg(), o.chargeableKg(),
                o.weightEstimated(), o.freight(), o.insurance(), o.total(), o.free(), o.minDays(), o.maxDays(), o.deliveryFrom(),
                o.deliveryTo())).toList();
        return new QuoteResult(result.zoneKey(), result.zoneName(), origin == null ? null : origin.warehouseId(), options);
    }

    @Override
    public boolean hasTariff(long companyId) {
        return snapshot(companyId).cards().stream().anyMatch(RateCard::active);
    }

    /** Cotización sin registrar destinos sin cobertura (para revisar la lista de faltantes). */
    public boolean covers(long companyId, Destination dest) {
        Snapshot snapshot = snapshot(companyId);
        Origin origin = store.origin(null).orElse(null);
        return TariffEngine.quote(snapshot.cards(), snapshot.zones(), new TariffEngine.Input(origin == null ? null : origin.location(),
                dest, List.of(), null, null, LocalDate.now(clock))).covered();
    }

    /** Llamar después de cualquier cambio del tarifario de la empresa. */
    public void invalidate(long companyId) {
        cache.remove(companyId);
    }

    Snapshot snapshot(long companyId) {
        Instant now = clock.instant();
        Snapshot current = cache.get(companyId);
        if (current != null && current.loadedAt().plus(CACHE_TTL).isAfter(now)) {
            return current;
        }
        Snapshot fresh = new Snapshot(store.cards(companyId), store.zones(companyId).stream().map(TariffStore.ZoneView::zone).toList(), now);
        cache.put(companyId, fresh);
        return fresh;
    }

    private List<Piece> piecesOf(List<Item> items) {
        List<Long> skuIds = items.stream().map(Item::skuId).filter(Objects::nonNull).distinct().toList();
        List<Long> productIds = items.stream().filter(i -> i.skuId() == null).map(Item::productId).filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, Piece> bySku = store.skuPieces(skuIds);
        Map<Long, Piece> byProduct = store.productPieces(productIds);
        List<Piece> pieces = new ArrayList<>();
        for (Item item : items) {
            if (item.quantity() <= 0) {
                continue;
            }
            Piece unit = item.skuId() != null ? bySku.get(item.skuId()) : byProduct.get(item.productId());
            pieces.add(unit == null ? new Piece(null, null, null, null, item.quantity())
                    : new Piece(unit.weightKg(), unit.lengthCm(), unit.widthCm(), unit.heightCm(), item.quantity()));
        }
        return pieces;
    }

    private void recordMiss(long companyId, Destination dest, String label, String zoneKey) {
        try {
            String text = label == null || label.isBlank() ? null : label.trim().substring(0, Math.min(label.trim().length(), 250));
            missTx.execute(status -> {
                store.recordMiss(companyId, dest, text, zoneKey, clock.instant());
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("shipping_quote miss_not_recorded company={} reason={}", companyId, e.toString());
        }
    }
}
