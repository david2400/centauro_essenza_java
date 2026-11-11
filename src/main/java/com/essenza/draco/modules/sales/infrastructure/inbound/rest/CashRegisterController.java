package com.essenza.draco.modules.sales.infrastructure.inbound.rest;

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

import com.essenza.draco.modules.sales.application.dto.cash.CashRegisterDto;
import com.essenza.draco.modules.sales.application.dto.cash.CashierDto;
import com.essenza.draco.modules.sales.application.dto.cash.SaveCashRegisterDto;
import com.essenza.draco.modules.sales.application.input.cash.CreateCashRegisterUseCase;
import com.essenza.draco.modules.sales.application.input.cash.DeleteCashRegisterUseCase;
import com.essenza.draco.modules.sales.application.input.cash.FindCashRegistersUseCase;
import com.essenza.draco.modules.sales.application.input.cash.FindCashiersUseCase;
import com.essenza.draco.modules.sales.application.input.cash.UpdateCashRegisterUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/sales/cash_registers")
@Tag(name = "Cash registers", description = "Cajas (puntos de cobro) con su turno abierto")
@RequiredArgsConstructor
public class CashRegisterController {

    private final FindCashRegistersUseCase findRegisters;
    private final CreateCashRegisterUseCase createRegister;
    private final UpdateCashRegisterUseCase updateRegister;
    private final DeleteCashRegisterUseCase deleteRegister;
    private final FindCashiersUseCase findCashiers;

    @Operation(summary = "List", description = "Cajas con su turno abierto y el efectivo esperado.")
    @GetMapping
    public List<CashRegisterDto> list(@RequestParam(value = "includeInactive", defaultValue = "true") boolean includeInactive) {
        return findRegisters.findAll(includeInactive);
    }

    @Operation(summary = "Cashiers", description = "Quién puede abrir un turno: el usuario de la sesión (primero) y los "
            + "usuarios activos de la aplicación en esta empresa según access_control.")
    @GetMapping("/cashiers")
    public List<CashierDto> cashiers() {
        return findCashiers.findAll();
    }

    @Operation(summary = "Create")
    @PostMapping
    public ResponseEntity<CashRegisterDto> create(@Valid @RequestBody SaveCashRegisterDto input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createRegister.create(input));
    }

    @Operation(summary = "Update")
    @PutMapping("/{id}")
    public CashRegisterDto update(@PathVariable Long id, @Valid @RequestBody SaveCashRegisterDto input) {
        return updateRegister.update(id, input);
    }

    @Operation(summary = "Delete", description = "409 si tiene turnos (desactívala).")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        deleteRegister.delete(id);
        return ResponseEntity.noContent().build();
    }
}
