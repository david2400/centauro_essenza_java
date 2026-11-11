package com.essenza.draco.modules.sales.application.services.cash;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.essenza.draco.modules.sales.application.dto.cash.CashierDto;
import com.essenza.draco.modules.sales.application.input.cash.FindCashiersUseCase;
import com.essenza.draco.shared.exceptions.ForbiddenException;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.CompanyUsers;
import com.essenza.draco.shared.security.CompanyUsers.CompanyUser;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Cajeros: el usuario de la sesión y los demás usuarios de la empresa con acceso a la aplicación en access_control.
 * El nombre del turno sale de aquí, nunca de un texto del navegador.
 */
@Service
public class CashierService implements FindCashiersUseCase {

    private final ActorProvider actors;
    private final CompanyUsers users;

    public CashierService(ActorProvider actors, CompanyUsers users) {
        this.actors = actors;
        this.users = users;
    }

    @Override
    public List<CashierDto> findAll() {
        RequestActor actor = actors.current();
        List<CompanyUser> all = users.active();
        List<CashierDto> list = new ArrayList<>();
        CompanyUser self = all.stream().filter(u -> u.matches(actor.userId())).findFirst().orElse(null);
        list.add(self != null ? new CashierDto(self.id(), self.username(), self.name(), true)
                : new CashierDto(actor.userId(), actor.displayName(), actor.displayName(), true));
        all.stream().filter(u -> u != self).forEach(u -> list.add(new CashierDto(u.id(), u.username(), u.name(), false)));
        return List.copyOf(list);
    }

    /**
     * Nombre con el que queda el turno. Sin {@code cashierUserId} (o con el propio) es el usuario de la sesión; con
     * otro, ese usuario tiene que estar activo en la aplicación de esta empresa.
     */
    public String resolveName(String cashierUserId) {
        RequestActor actor = actors.current();
        if (cashierUserId == null || cashierUserId.isBlank() || cashierUserId.trim().equals(actor.userId())) {
            return fit(self(actor));
        }
        Optional<CompanyUser> chosen = users.find(cashierUserId);
        if (chosen.isEmpty()) {
            throw new ForbiddenException("El cajero elegido no tiene acceso a esta aplicación en tu empresa.");
        }
        return fit(chosen.get().matches(actor.userId()) ? self(actor) : chosen.get().name());
    }

    /** La columna del turno guarda hasta 120 caracteres. */
    private static String fit(String name) {
        String value = name == null ? "" : name.trim();
        return value.length() <= 120 ? value : value.substring(0, 120);
    }

    /** Si access_control no responde, el usuario de la sesión igual puede abrir con el nombre del token. */
    private String self(RequestActor actor) {
        try {
            return users.active().stream().filter(u -> u.matches(actor.userId())).map(CompanyUser::name).findFirst()
                    .orElse(actor.displayName());
        } catch (RuntimeException ex) {
            if (ex instanceof ForbiddenException forbidden) {
                throw forbidden;
            }
            return actor.displayName();
        }
    }
}
