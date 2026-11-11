package com.essenza.draco.modules.sales.application.input.cash;

import java.util.List;

import com.essenza.draco.modules.sales.application.dto.cash.CashierDto;

public interface FindCashiersUseCase {
    /** Usuarios que pueden abrir un turno; el de la petición primero. */
    List<CashierDto> findAll();
}
