package com.essenza.draco.modules.shipping_logistics.tariff.application.output;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.Destination;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.Place;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.RateCard;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.Zone;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.Tariff.ZoneRate;
import com.essenza.draco.modules.shipping_logistics.tariff.domain.TariffEngine.Piece;

/** Persistencia del tarifario (siempre filtrada por empresa) y datos de apoyo para cotizar. */
public interface TariffStore {

    record Actor(String id, String name) {
    }

    record Audit(String updatedByName, Instant updatedAt) {
    }

    record LabeledPlace(Place place, String label) {
    }

    record ZoneView(Zone zone, String description, long version, List<LabeledPlace> places, Audit audit) {
    }

    record Miss(Long countryId, Long stateId, Long cityId, String label, String zoneKey, int misses, Instant lastAt) {
    }

    // ------------------------------------------------------------------------------------------ tarifarios

    /** Tarifarios con sus tarifas y franjas, y el nombre de la transportadora. */
    List<RateCard> cards(long companyId);

    Optional<RateCard> card(long companyId, long id);

    Map<Long, Audit> cardAudits(long companyId);

    boolean carrierExists(long carrierId);

    boolean cardExistsForCarrier(long companyId, long carrierId, Long exceptId);

    long insertCard(long companyId, RateCard card, Actor actor, Instant now);

    /** {@code false} si otro usuario lo cambió antes (versión distinta). */
    boolean updateCard(long companyId, long id, long expectedVersion, RateCard card, Actor actor, Instant now);

    /** Marca el tarifario como cambiado (sube la versión); {@code false} si la versión no coincide. */
    boolean touchCard(long companyId, long id, long expectedVersion, Actor actor, Instant now);

    void deleteCard(long companyId, long id);

    /** Crea o reemplaza la tarifa de la zona con sus franjas. */
    void saveRate(long cardId, ZoneRate rate);

    boolean deleteRate(long cardId, String zoneKey);

    // ------------------------------------------------------------------------------------------ zonas

    List<ZoneView> zones(long companyId);

    Optional<ZoneView> zone(long companyId, long id);

    boolean zoneCodeTaken(long companyId, String code, Long exceptId);

    /** Zona que ya tiene alguno de esos lugares (un lugar pertenece a una sola zona). */
    Optional<String> placeOwner(long companyId, Collection<String> placeKeys, Long exceptZoneId);

    long insertZone(long companyId, String code, String name, String description, boolean active, Actor actor, Instant now);

    boolean updateZone(long companyId, long id, long expectedVersion, String code, String name, String description,
                       boolean active, Actor actor, Instant now);

    void replacePlaces(long companyId, long zoneId, List<LabeledPlace> places);

    /** Tarifarios que tienen tarifa para la zona (no se puede borrar mientras se use). */
    int ratesUsingZone(long companyId, String zoneKey);

    void deleteZone(long companyId, long id);

    // ------------------------------------------------------------------------------------------ cotizar

    /** Ubicación de la bodega (vacía = la principal activa). */
    Optional<Origin> origin(Long warehouseId);

    record Origin(long warehouseId, Destination location) {
    }

    /** Peso y medidas por SKU (o por producto si el SKU no las tiene). */
    Map<Long, Piece> skuPieces(Collection<Long> skuIds);

    Map<Long, Piece> productPieces(Collection<Long> productIds);

    void recordMiss(long companyId, Destination destination, String label, String zoneKey, Instant now);

    List<Miss> misses(long companyId, int limit);

    void clearMiss(long companyId, Destination destination);
}
