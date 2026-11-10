package com.essenza.draco.modules.shipping_logistics.tariff.application.services;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.BracketDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.CoverageGapDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.OptionDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.PieceDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.PlaceDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.QuoteDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.QuoteRequestDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.RateCardDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.SaveRateCardRequest;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.SaveZoneRateRequest;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.SaveZoneRequest;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.ZoneDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.ZoneRateDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.output.TariffStore;
import com.essenza.draco.modules.shipping_logistics.tariff.application.output.TariffStore.Actor;
import com.essenza.draco.modules.shipping_logistics.tariff.application.output.TariffStore.Audit;
import com.essenza.draco.modules.shipping_logistics.tariff.application.output.TariffStore.LabeledPlace;
import com.essenza.draco.modules.shipping_logistics.tariff.application.output.TariffStore.ZoneView;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.Bracket;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.Destination;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.Place;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.RateCard;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.ZoneRate;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.TariffEngine.Piece;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.TariffRuleViolation;
import com.essenza.draco.shared.common.logistics.ShippingQuotes;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Administración del tarifario (tarifarios, tarifas por zona, zonas propias), simulador y destinos sin cobertura.
 * Empresa y permisos salen del token; las ediciones usan versión para que dos personas no se pisen.
 */
@Service
@Transactional
public class TariffAdminService {

    /** Permisos de logística; mientras se crean en access_control también sirven los de ventas. */
    public static final String VIEW = "LOGISTICA.VER";
    public static final String MANAGE = "LOGISTICA.GESTIONAR";
    static final String[] CAN_VIEW = {VIEW, MANAGE, "VENTAS.VER", "VENTAS.GESTIONAR"};
    static final String[] CAN_MANAGE = {MANAGE, "VENTAS.GESTIONAR"};
    static final int MAX_PLACES = 500;
    static final int MAX_PIECES = 50;

    private final TariffStore store;
    private final ShippingQuoteService quotes;
    private final ActorProvider actors;
    private final Clock clock;

    public TariffAdminService(TariffStore store, ShippingQuoteService quotes, ActorProvider actors,
                              @Qualifier("marketplaceClock") Clock clock) {
        this.store = store;
        this.quotes = quotes;
        this.actors = actors;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------------------ tarifarios

    @Transactional(readOnly = true)
    public List<RateCardDto> cards() {
        RequestActor actor = actors.current().requireAny(CAN_VIEW);
        Map<Long, Audit> audits = store.cardAudits(actor.companyId());
        Map<String, String> zoneNames = zoneNames(actor.companyId());
        return store.cards(actor.companyId()).stream().map(c -> dto(c, audits.get(c.id()), zoneNames)).toList();
    }

    public RateCardDto saveCard(Long id, SaveRateCardRequest in) {
        RequestActor actor = actors.current().requireAny(CAN_MANAGE);
        if (in == null) {
            throw new TariffRuleViolation("Faltan los datos del tarifario.");
        }
        long company = actor.companyId();
        RateCard current = id == null ? null : existingCard(company, id);
        RateCard card = Tariff.checkCard(in.carrierId(), in.name(), in.volumetricDivisor(), in.defaultWeightKg(), in.minCharge(),
                in.insurancePct(), in.insuranceMin(), in.active() == null ? current == null || current.active() : in.active());
        if (!store.carrierExists(card.carrierId())) {
            throw new TariffRuleViolation("La transportadora no existe.");
        }
        if (store.cardExistsForCarrier(company, card.carrierId(), id)) {
            throw new ConflictException("Esa transportadora ya tiene tarifario. Edítalo en vez de crear otro.");
        }
        Instant now = clock.instant();
        long saved;
        if (id == null) {
            saved = store.insertCard(company, card, who(actor), now);
        } else {
            if (!store.updateCard(company, id, requireVersion(in.version()), card, who(actor), now)) {
                throw staleCard();
            }
            saved = id;
        }
        quotes.invalidate(company);
        return dtoOf(company, saved);
    }

    public void deleteCard(long id) {
        RequestActor actor = actors.current().requireAny(CAN_MANAGE);
        existingCard(actor.companyId(), id);
        store.deleteCard(actor.companyId(), id);
        quotes.invalidate(actor.companyId());
    }

    /** Crea o reemplaza la tarifa de una zona (con todas sus franjas) en un solo paso. */
    public RateCardDto saveRate(long cardId, String zoneKey, SaveZoneRateRequest in) {
        RequestActor actor = actors.current().requireAny(CAN_MANAGE);
        if (in == null) {
            throw new TariffRuleViolation("Faltan los datos de la tarifa.");
        }
        long company = actor.companyId();
        existingCard(company, cardId);
        List<Bracket> brackets = in.brackets() == null ? List.of()
                : in.brackets().stream().map(b -> b == null ? null : new Bracket(b.minKg(), b.maxKg(), b.price(), b.perKgOver())).toList();
        ZoneRate rate = Tariff.checkRate(zoneKey, in.minDays(), in.maxDays(), in.freeFrom(), in.active() == null || in.active(), brackets);
        requireZone(company, rate.zoneKey());
        if (!store.touchCard(company, cardId, requireVersion(in.version()), who(actor), clock.instant())) {
            throw staleCard();
        }
        store.saveRate(cardId, rate);
        quotes.invalidate(company);
        return dtoOf(company, cardId);
    }

    public RateCardDto deleteRate(long cardId, String zoneKey, Long version) {
        RequestActor actor = actors.current().requireAny(CAN_MANAGE);
        long company = actor.companyId();
        existingCard(company, cardId);
        String key = Tariff.checkZoneKey(zoneKey);
        if (!store.touchCard(company, cardId, requireVersion(version), who(actor), clock.instant())) {
            throw staleCard();
        }
        if (!store.deleteRate(cardId, key)) {
            throw new NotFoundException("El tarifario no tiene tarifa para esa zona.");
        }
        quotes.invalidate(company);
        return dtoOf(company, cardId);
    }

    // ------------------------------------------------------------------------------------------ zonas

    @Transactional(readOnly = true)
    public List<ZoneDto> zones() {
        RequestActor actor = actors.current().requireAny(CAN_VIEW);
        return store.zones(actor.companyId()).stream().map(z -> dto(z, store.ratesUsingZone(actor.companyId(), z.zone().key()))).toList();
    }

    public ZoneDto saveZone(Long id, SaveZoneRequest in) {
        RequestActor actor = actors.current().requireAny(CAN_MANAGE);
        if (in == null) {
            throw new TariffRuleViolation("Faltan los datos de la zona.");
        }
        long company = actor.companyId();
        if (id != null) {
            existingZone(company, id);
        }
        String code = Tariff.code(in.code());
        String name = Tariff.text(in.name(), 120);
        if (name == null) {
            throw new TariffRuleViolation("La zona necesita un nombre.");
        }
        String description = Tariff.text(in.description(), 300);
        List<LabeledPlace> places = places(in.places());
        if (store.zoneCodeTaken(company, code, id)) {
            throw new ConflictException("Ya hay una zona con el código " + code + ".");
        }
        store.placeOwner(company, places.stream().map(p -> p.place().key()).toList(), id).ifPresent(owner -> {
            throw new ConflictException("Ese lugar ya está en la zona " + owner + ". Un lugar solo puede estar en una zona.");
        });
        boolean active = in.active() == null || in.active();
        Instant now = clock.instant();
        long saved;
        if (id == null) {
            saved = store.insertZone(company, code, name, description, active, who(actor), now);
        } else {
            if (!store.updateZone(company, id, requireVersion(in.version()), code, name, description, active, who(actor), now)) {
                throw new ConflictException("Otra persona cambió esta zona mientras la editabas. Recarga y vuelve a intentar.");
            }
            saved = id;
        }
        store.replacePlaces(company, saved, places);
        quotes.invalidate(company);
        ZoneView view = existingZone(company, saved);
        return dto(view, store.ratesUsingZone(company, view.zone().key()));
    }

    public void deleteZone(long id) {
        RequestActor actor = actors.current().requireAny(CAN_MANAGE);
        long company = actor.companyId();
        ZoneView view = existingZone(company, id);
        int used = store.ratesUsingZone(company, view.zone().key());
        if (used > 0) {
            throw new ConflictException("La zona tiene tarifas en " + used + " tarifario(s). Bórralas antes, o desactiva la zona.");
        }
        store.deleteZone(company, id);
        quotes.invalidate(company);
    }

    // ------------------------------------------------------------------------------------------ simulador y faltantes

    @Transactional(readOnly = true)
    public QuoteDto simulate(QuoteRequestDto in) {
        RequestActor actor = actors.current().requireAny(CAN_VIEW);
        if (in == null) {
            throw new TariffRuleViolation("Faltan los datos de la cotización.");
        }
        List<ShippingQuotes.Item> items = in.items() == null ? List.of() : in.items().stream()
                .filter(i -> i != null && i.quantity() != null && i.quantity() > 0)
                .map(i -> new ShippingQuotes.Item(i.skuId(), i.productId(), i.quantity())).toList();
        ShippingQuotes.QuoteRequest request = new ShippingQuotes.QuoteRequest(in.originWarehouseId(), in.countryId(), in.stateId(),
                in.cityId(), in.destinationLabel(), items, in.declaredValue(), in.orderSubtotal(), in.shipDate(), in.carrierId());
        ShippingQuotes.QuoteResult result = in.pieces() != null && !in.pieces().isEmpty()
                ? quotes.quote(actor.companyId(), request, pieces(in.pieces()))
                : quotes.quote(actor.companyId(), request);
        return dto(result);
    }

    /** Destinos cotizados sin cobertura que siguen sin tarifa (los ya cubiertos se omiten). */
    @Transactional(readOnly = true)
    public List<CoverageGapDto> gaps() {
        RequestActor actor = actors.current().requireAny(CAN_VIEW);
        long company = actor.companyId();
        return store.misses(company, 100).stream()
                .filter(m -> m.countryId() != null && m.stateId() != null && m.cityId() != null)
                .filter(m -> !quotes.covers(company, new Destination(m.countryId(), m.stateId(), m.cityId())))
                .map(m -> new CoverageGapDto(m.countryId(), m.stateId(), m.cityId(), m.label(), m.zoneKey(), m.misses(), m.lastAt()))
                .toList();
    }

    public void dismissGap(Long countryId, Long stateId, Long cityId) {
        RequestActor actor = actors.current().requireAny(CAN_MANAGE);
        store.clearMiss(actor.companyId(), new Destination(countryId, stateId, cityId));
    }

    // ------------------------------------------------------------------------------------------ apoyo

    static QuoteDto dto(ShippingQuotes.QuoteResult r) {
        return new QuoteDto(r.zoneKey(), r.zoneName(), r.originWarehouseId(), r.covered(), r.options().stream()
                .map(o -> new OptionDto(o.rateCardId(), o.carrierId(), o.carrierName(), o.zoneKey(), o.zoneName(), o.actualKg(),
                        o.volumetricKg(), o.chargeableKg(), o.weightEstimated(), o.freight(), o.insurance(), o.total(), o.free(),
                        o.minDays(), o.maxDays(), o.deliveryFrom(), o.deliveryTo()))
                .toList());
    }

    private static List<Piece> pieces(List<PieceDto> in) {
        if (in.size() > MAX_PIECES) {
            throw new TariffRuleViolation("Máximo " + MAX_PIECES + " paquetes en el simulador.");
        }
        List<Piece> pieces = new ArrayList<>();
        for (PieceDto p : in) {
            if (p == null) {
                continue;
            }
            int qty = p.quantity() == null ? 1 : p.quantity();
            if (qty < 1 || qty > 10_000) {
                throw new TariffRuleViolation("Cantidad de paquetes no válida.");
            }
            pieces.add(new Piece(p.weightKg(), p.lengthCm(), p.widthCm(), p.heightCm(), qty));
        }
        return pieces;
    }

    private static List<LabeledPlace> places(List<PlaceDto> in) {
        if (in == null || in.isEmpty()) {
            throw new TariffRuleViolation("Agrega al menos un lugar (país, departamento o ciudad) a la zona.");
        }
        if (in.size() > MAX_PLACES) {
            throw new TariffRuleViolation("Máximo " + MAX_PLACES + " lugares por zona.");
        }
        Set<String> seen = new HashSet<>();
        List<LabeledPlace> result = new ArrayList<>();
        for (PlaceDto p : in) {
            if (p == null) {
                continue;
            }
            Place place = Place.of(p.countryId(), p.stateId(), p.cityId());
            if (seen.add(place.key())) {
                result.add(new LabeledPlace(place, Tariff.text(p.label(), 250)));
            }
        }
        return result;
    }

    private void requireZone(long company, String zoneKey) {
        if (Tariff.isRoute(zoneKey)) {
            return;
        }
        long zoneId = Long.parseLong(zoneKey.substring(Tariff.ZONE_PREFIX.length()));
        existingZone(company, zoneId);
    }

    private RateCard existingCard(long company, long id) {
        return store.card(company, id).orElseThrow(() -> new NotFoundException("Tarifario no encontrado: " + id));
    }

    private ZoneView existingZone(long company, long id) {
        return store.zone(company, id).orElseThrow(() -> new NotFoundException("Zona no encontrada: " + id));
    }

    private static long requireVersion(Long version) {
        if (version == null) {
            throw new TariffRuleViolation("Falta la versión del registro que estabas editando.");
        }
        return version;
    }

    private static ConflictException staleCard() {
        return new ConflictException("Otra persona cambió este tarifario mientras lo editabas. Recarga y vuelve a intentar.");
    }

    private static Actor who(RequestActor actor) {
        return new Actor(actor.userId(), actor.displayName());
    }

    private Map<String, String> zoneNames(long company) {
        Map<String, String> names = new LinkedHashMap<>();
        store.zones(company).forEach(z -> names.put(z.zone().key(), z.zone().name()));
        return names;
    }

    private RateCardDto dtoOf(long company, long id) {
        return dto(existingCard(company, id), store.cardAudits(company).get(id), zoneNames(company));
    }

    private static RateCardDto dto(RateCard c, Audit audit, Map<String, String> zoneNames) {
        List<ZoneRateDto> rates = c.rates().stream().map(r -> new ZoneRateDto(r.zoneKey(), zoneNames.get(r.zoneKey()), r.minDays(),
                r.maxDays(), r.freeFrom(), r.active(), r.brackets().stream()
                        .map(b -> new BracketDto(b.minKg(), b.maxKg(), b.price(), b.perKgOver())).toList())).toList();
        return new RateCardDto(c.id(), c.carrierId(), c.carrierName(), c.name(), c.volumetricDivisor(), c.defaultWeightKg(),
                c.minCharge(), c.insurancePct(), c.insuranceMin(), c.active(), c.version(), rates,
                audit == null ? null : audit.updatedByName(), audit == null ? null : audit.updatedAt());
    }

    private static ZoneDto dto(ZoneView z, int ratesCount) {
        return new ZoneDto(z.zone().id(), z.zone().code(), z.zone().name(), z.description(), z.zone().active(), z.version(),
                z.zone().key(), z.places().stream().map(p -> new PlaceDto(p.place().countryId(), p.place().stateId(), p.place().cityId(),
                        p.label())).toList(), ratesCount, z.audit() == null ? null : z.audit().updatedByName(),
                z.audit() == null ? null : z.audit().updatedAt());
    }
}
