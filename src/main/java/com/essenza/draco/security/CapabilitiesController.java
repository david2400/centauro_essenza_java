package com.essenza.draco.security;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.Capabilities;
import com.essenza.draco.shared.security.RequestActor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Acciones que el usuario actual puede hacer (para que el panel muestre u oculte botones). */
@RestController
@Tag(name = "Session", description = "Acciones permitidas al usuario del token")
public class CapabilitiesController {

    private final ActorProvider actors;

    public CapabilitiesController(ActorProvider actors) {
        this.actors = actors;
    }

    public record CapabilitiesDto(long companyId, String user, List<String> capabilities) {
    }

    @Operation(summary = "My capabilities", description = "Acciones concedidas por las reglas essenza.authorization.rules. "
            + "Mostrar u ocultar botones es solo presentación: cada operación se vuelve a validar.")
    @GetMapping("/me/capabilities")
    public CapabilitiesDto capabilities() {
        RequestActor actor = actors.current();
        return new CapabilitiesDto(actor.companyId(), actor.displayName(),
                Capabilities.keys().stream().filter(actor::has).toList());
    }
}
