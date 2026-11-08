package com.essenza.draco.modules.product_details.infrastructure.inbound.rest;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

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

import com.essenza.draco.modules.product_details.application.dto.unit.SaveUnitDto;
import com.essenza.draco.modules.product_details.application.dto.unit.UnitConversionDto;
import com.essenza.draco.modules.product_details.application.dto.unit.UnitDto;
import com.essenza.draco.modules.product_details.application.input.unit.ManageUnitsUseCase;
import com.essenza.draco.shared.common.lookup.LookupRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Unidades de medida con magnitud y conversión. {@code /product_details/unit_measurements}
 * se reenvía aquí (ver LegacyApiRoutesFilter).
 */
@RestController
@RequestMapping("/catalog/units")
@Tag(name = "Units of measure")
public class UnitController {

    private final ManageUnitsUseCase units;

    public UnitController(ManageUnitsUseCase units) {
        this.units = units;
    }

    @Operation(summary = "Listar unidades", description = "Por magnitud y factor. dimension filtra; include_inactive incluye las desactivadas")
    @GetMapping
    public List<UnitDto> list(@RequestParam(value = "dimension", required = false) String dimension,
                              @RequestParam(value = "include_inactive", required = false, defaultValue = "false") boolean includeInactive) {
        return units.list(dimension, includeInactive);
    }

    @Operation(summary = "Buscar unidades", description = "q (código, símbolo o nombre), ids, limit (≤ 50), dimensions=MASS,VOLUME")
    @GetMapping("/lookup")
    public List<UnitDto> lookup(@RequestParam(value = "q", required = false) String q,
                                @RequestParam(value = "ids", required = false) String ids,
                                @RequestParam(value = "limit", required = false) Integer limit,
                                @RequestParam(value = "dimensions", required = false) String dimensions) {
        List<String> parsed = dimensions == null || dimensions.isBlank() ? List.of() : Arrays.asList(dimensions.split(","));
        return units.lookup(LookupRequest.of(q, ids, limit), parsed);
    }

    @Operation(summary = "Convertir", description = "value de from a to (código o id). Solo entre unidades de la misma magnitud")
    @GetMapping("/convert")
    public UnitConversionDto convert(@RequestParam("value") BigDecimal value,
                                     @RequestParam("from") String from,
                                     @RequestParam("to") String to) {
        return units.convert(value, from, to);
    }

    @GetMapping("/{id}")
    public UnitDto get(@PathVariable Long id) {
        return units.get(id);
    }

    @Operation(summary = "Crear unidad", description = "La primera unidad de una magnitud es su base. base=true la convierte en base y reescala las demás")
    @PostMapping
    public ResponseEntity<UnitDto> create(@Valid @RequestBody SaveUnitDto input) {
        UnitDto created = units.create(input);
        return ResponseEntity.created(URI.create("/catalog/units/" + created.id())).body(created);
    }

    @Operation(summary = "Editar unidad", description = "No cambia la magnitud de una unidad en uso. base=true la convierte en base")
    @PutMapping("/{id}")
    public UnitDto update(@PathVariable Long id, @Valid @RequestBody SaveUnitDto input) {
        return units.update(id, input);
    }

    @Operation(summary = "Eliminar unidad", description = "409 si está en uso o si es la base y quedan otras en su magnitud")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        units.delete(id);
        return ResponseEntity.noContent().build();
    }
}
