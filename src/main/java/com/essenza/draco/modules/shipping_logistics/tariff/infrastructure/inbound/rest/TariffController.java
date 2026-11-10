package com.essenza.draco.modules.shipping_logistics.tariff.infrastructure.inbound.rest;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.CoverageGapDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.QuoteDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.QuoteRequestDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.RateCardDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.SaveRateCardRequest;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.SaveZoneRateRequest;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.SaveZoneRequest;
import com.essenza.draco.modules.shipping_logistics.tariff.application.dto.TariffDtos.ZoneDto;
import com.essenza.draco.modules.shipping_logistics.tariff.application.services.TariffAdminService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Tarifario de envíos: tarifarios por transportadora, tarifas por zona, zonas propias y simulador. */
@RestController
@RequestMapping("/shipping_logistics/tariffs")
@Tag(name = "Shipping tariffs", description = "Tarifario de envíos por zona y peso, con ubicaciones del catálogo de parametros")
public class TariffController {

    private final TariffAdminService tariffs;

    public TariffController(TariffAdminService tariffs) {
        this.tariffs = tariffs;
    }

    @Operation(summary = "List rate cards", description = "Tarifarios con tarifas y franjas. LOGISTICA.VER.")
    @GetMapping("/cards")
    public List<RateCardDto> cards() {
        return tariffs.cards();
    }

    @Operation(summary = "Create rate card", description = "Un tarifario por transportadora. LOGISTICA.GESTIONAR.")
    @PostMapping("/cards")
    public ResponseEntity<RateCardDto> createCard(@RequestBody SaveRateCardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tariffs.saveCard(null, request));
    }

    @Operation(summary = "Update rate card", description = "Requiere la versión leída (409 si otro la cambió). LOGISTICA.GESTIONAR.")
    @PutMapping("/cards/{id}")
    public RateCardDto updateCard(@PathVariable long id, @RequestBody SaveRateCardRequest request) {
        return tariffs.saveCard(id, request);
    }

    @Operation(summary = "Delete rate card", description = "Borra el tarifario con sus tarifas. LOGISTICA.GESTIONAR.")
    @DeleteMapping("/cards/{id}")
    public ResponseEntity<Void> deleteCard(@PathVariable long id) {
        tariffs.deleteCard(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Save zone rate", description = "Crea o reemplaza la tarifa de una zona (LOCAL, REGIONAL, NATIONAL o Z<id>) "
            + "con sus franjas de peso. LOGISTICA.GESTIONAR.")
    @PutMapping("/cards/{id}/rates/{zoneKey}")
    public RateCardDto saveRate(@PathVariable long id, @PathVariable String zoneKey, @RequestBody SaveZoneRateRequest request) {
        return tariffs.saveRate(id, zoneKey, request);
    }

    @Operation(summary = "Delete zone rate", description = "LOGISTICA.GESTIONAR.")
    @DeleteMapping("/cards/{id}/rates/{zoneKey}")
    public RateCardDto deleteRate(@PathVariable long id, @PathVariable String zoneKey, @RequestParam Long version) {
        return tariffs.deleteRate(id, zoneKey, version);
    }

    @Operation(summary = "List zones", description = "Zonas propias con sus lugares. LOGISTICA.VER.")
    @GetMapping("/zones")
    public List<ZoneDto> zones() {
        return tariffs.zones();
    }

    @Operation(summary = "Create zone", description = "Un lugar solo puede estar en una zona. LOGISTICA.GESTIONAR.")
    @PostMapping("/zones")
    public ResponseEntity<ZoneDto> createZone(@RequestBody SaveZoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tariffs.saveZone(null, request));
    }

    @Operation(summary = "Update zone", description = "Reemplaza los lugares. LOGISTICA.GESTIONAR.")
    @PutMapping("/zones/{id}")
    public ZoneDto updateZone(@PathVariable long id, @RequestBody SaveZoneRequest request) {
        return tariffs.saveZone(id, request);
    }

    @Operation(summary = "Delete zone", description = "Solo si ningún tarifario la usa. LOGISTICA.GESTIONAR.")
    @DeleteMapping("/zones/{id}")
    public ResponseEntity<Void> deleteZone(@PathVariable long id) {
        tariffs.deleteZone(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Simulate quote", description = "Cotiza con artículos o paquetes escritos a mano. No guarda nada. LOGISTICA.VER.")
    @PostMapping("/quote")
    public QuoteDto quote(@RequestBody QuoteRequestDto request) {
        return tariffs.simulate(request);
    }

    @Operation(summary = "Coverage gaps", description = "Destinos cotizados que siguen sin tarifa. LOGISTICA.VER.")
    @GetMapping("/coverage-gaps")
    public List<CoverageGapDto> gaps() {
        return tariffs.gaps();
    }

    @Operation(summary = "Dismiss coverage gap", description = "LOGISTICA.GESTIONAR.")
    @DeleteMapping("/coverage-gaps")
    public ResponseEntity<Void> dismissGap(@RequestParam Long countryId, @RequestParam Long stateId, @RequestParam Long cityId) {
        tariffs.dismissGap(countryId, stateId, cityId);
        return ResponseEntity.noContent().build();
    }
}
