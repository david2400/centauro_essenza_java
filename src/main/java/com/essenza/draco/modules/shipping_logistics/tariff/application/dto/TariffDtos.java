package com.essenza.draco.modules.shipping_logistics.tariff.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Contratos REST del tarifario de envíos (JSON en snake_case, como el resto de la API). */
public final class TariffDtos {

    private TariffDtos() {
    }

    @Schema(description = "Franja de peso cobrable (min_kg, max_kg]; max_kg vacío = sin límite")
    public record BracketDto(BigDecimal minKg, BigDecimal maxKg, BigDecimal price,
                             @Schema(description = "Valor por kg (o fracción) por encima de min_kg; vacío = 0") BigDecimal perKgOver) {
    }

    public record ZoneRateDto(@Schema(description = "LOCAL, REGIONAL, NATIONAL o Z<id> de una zona propia") String zoneKey,
                              @Schema(description = "Nombre de la zona propia (vacío en los trayectos)") String zoneName,
                              int minDays, int maxDays, BigDecimal freeFrom, boolean active, List<BracketDto> brackets) {
    }

    public record RateCardDto(Long id, Long carrierId, String carrierName, String name,
                              @Schema(description = "cm³ por kg para el peso volumétrico") int volumetricDivisor,
                              BigDecimal defaultWeightKg, BigDecimal minCharge, BigDecimal insurancePct, BigDecimal insuranceMin,
                              boolean active, long version, List<ZoneRateDto> rates, String updatedByName, Instant updatedAt) {
    }

    public record SaveRateCardRequest(Long carrierId, String name, Integer volumetricDivisor, BigDecimal defaultWeightKg,
                                      BigDecimal minCharge, BigDecimal insurancePct, BigDecimal insuranceMin, Boolean active,
                                      @Schema(description = "Versión leída (obligatoria al editar)") Long version) {
    }

    public record SaveZoneRateRequest(@Schema(description = "Versión del tarifario leída") Long version, Integer minDays,
                                      Integer maxDays, BigDecimal freeFrom, Boolean active, List<BracketDto> brackets) {
    }

    public record PlaceDto(Long countryId, Long stateId, Long cityId,
                           @Schema(description = "Nombre para mostrar (p. ej. 'San Andrés, San Andrés y Providencia')") String label) {
    }

    public record ZoneDto(Long id, String code, String name, String description, boolean active, long version,
                          @Schema(description = "Clave para las tarifas: Z<id>") String zoneKey, List<PlaceDto> places,
                          int ratesCount, String updatedByName, Instant updatedAt) {
    }

    public record SaveZoneRequest(String code, String name, String description, Boolean active, Long version,
                                  List<PlaceDto> places) {
    }

    public record ItemDto(Long skuId, Long productId, Integer quantity) {
    }

    @Schema(description = "Paquete escrito a mano en el simulador (kg y cm)")
    public record PieceDto(BigDecimal weightKg, BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm, Integer quantity) {
    }

    public record QuoteRequestDto(Long originWarehouseId, Long countryId, Long stateId, Long cityId, String destinationLabel,
                                  List<ItemDto> items, List<PieceDto> pieces, BigDecimal declaredValue, BigDecimal orderSubtotal,
                                  LocalDate shipDate, Long carrierId) {
    }

    public record OptionDto(Long rateCardId, Long carrierId, String carrierName, String zoneKey, String zoneName,
                            BigDecimal actualKg, BigDecimal volumetricKg, BigDecimal chargeableKg, boolean weightEstimated,
                            BigDecimal freight, BigDecimal insurance, BigDecimal total, boolean free, int minDays, int maxDays,
                            LocalDate deliveryFrom, LocalDate deliveryTo) {
    }

    public record QuoteDto(String zoneKey, String zoneName, Long originWarehouseId, boolean covered, List<OptionDto> options) {
    }

    public record CoverageGapDto(Long countryId, Long stateId, Long cityId, String label, String zoneKey, int misses,
                                 Instant lastAt) {
    }
}
