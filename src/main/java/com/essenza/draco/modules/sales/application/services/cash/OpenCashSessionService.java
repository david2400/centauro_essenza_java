package com.essenza.draco.modules.sales.application.services.cash;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.sales.application.dto.cash.CashSessionDto;
import com.essenza.draco.modules.sales.application.dto.cash.OpenCashSessionDto;
import com.essenza.draco.modules.sales.application.input.cash.OpenCashSessionUseCase;
import com.essenza.draco.modules.sales.application.output.repository.CashSessionRepository;
import com.essenza.draco.modules.sales.domain.model.cash.CashRegister;
import com.essenza.draco.modules.sales.domain.model.cash.CashSession;
import com.essenza.draco.shared.exceptions.ConflictException;

/**
 * Abre un turno con base: un solo turno abierto por caja (también lo garantiza la base de datos). El cajero es el
 * usuario de la sesión o, si se elige otro, un usuario activo de la aplicación en la misma empresa.
 */
@Service
@Transactional
public class OpenCashSessionService implements OpenCashSessionUseCase {

    private final CashSessionRepository sessions;
    private final CashSoporte soporte;
    private final CashierService cashiers;

    public OpenCashSessionService(CashSessionRepository sessions, CashSoporte soporte, CashierService cashiers) {
        this.sessions = sessions;
        this.soporte = soporte;
        this.cashiers = cashiers;
    }

    @Override
    public CashSessionDto open(OpenCashSessionDto input) {
        CashRegister register = soporte.register(input.cashRegisterId());
        if (!register.active()) {
            throw new ConflictException("La caja " + register.name() + " está inactiva.");
        }
        sessions.findOpenByRegister(register.id()).ifPresent(open -> {
            throw new ConflictException("La caja " + register.name() + " ya tiene un turno abierto por " + open.openedBy()
                    + " (turno " + open.id() + ").");
        });
        if (register.storeId() != null && !soporte.store(register.storeId()).active()) {
            throw new ConflictException("El punto de venta de la caja " + register.name() + " está inactivo.");
        }
        // Quién atiende sale del token o de los usuarios de la empresa en access_control, no de un texto del navegador.
        String openedBy = cashiers.resolveName(input.cashierUserId());
        CashSession session = sessions.save(CashSession.open(register.id(), openedBy,
                register.settings().openingAmount(input.openingAmount()), input.notes(), Instant.now()));
        return soporte.toDto(session, true);
    }
}
