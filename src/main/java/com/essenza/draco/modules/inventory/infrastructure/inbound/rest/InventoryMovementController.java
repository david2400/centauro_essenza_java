package com.essenza.draco.modules.inventory.infrastructure.inbound.rest;

import com.essenza.draco.modules.inventory.application.dto.InventoryMovementDto;
import com.essenza.draco.modules.inventory.application.services.InventoryMovementServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory/stock/movements")
@RequiredArgsConstructor
public class InventoryMovementController {

    private final InventoryMovementServiceImpl service;

    @PostMapping("/entry")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryMovementDto entry(@Valid @RequestBody InventoryMovementDto dto) {
        return service.registerEntry(dto);
    }

    @PostMapping("/exit")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryMovementDto exit(@Valid @RequestBody InventoryMovementDto dto) {
        return service.registerExit(dto);
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryMovementDto transfer(@Valid @RequestBody InventoryMovementDto dto) {
        return service.registerTransfer(dto);
    }

    @GetMapping
    public Page<InventoryMovementDto> list(Pageable pageable) {
        return service.list(pageable);
    }
}
