package com.essenza.draco.shared.security;

import java.util.Set;

import com.essenza.draco.shared.exceptions.ForbiddenException;

/**
 * Quién hace la petición, tomado del access token de access_control ya validado por el filtro
 * de seguridad: empresa del contexto ({@code ctx.company_id}), usuario ({@code sub}) y permisos
 * ({@code permissions}). Nunca se toma la empresa ni el vendedor de lo que envía el navegador.
 *
 * @param companyId   empresa (tenant) del contexto del token
 * @param userId      sujeto del token
 * @param displayName nombre para la auditoría (username o sub)
 * @param permissions permisos del contexto, p. ej. {@code VENDEDORES.APROBAR}
 */
public record RequestActor(long companyId, String userId, String displayName, Set<String> permissions) {

    public RequestActor {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public boolean has(String permission) {
        return permissions.contains(permission);
    }

    /** 403 si no tiene el permiso. Ocultar un botón no es autorización: esto sí. */
    public RequestActor require(String permission) {
        if (!has(permission)) {
            throw new ForbiddenException("No tienes el permiso " + permission + " para esta acción.");
        }
        return this;
    }

    /** 403 si no tiene ninguno de los permisos. */
    public RequestActor requireAny(String... options) {
        for (String option : options) {
            if (has(option)) {
                return this;
            }
        }
        throw new ForbiddenException("No tienes permiso para esta acción (" + String.join(" o ", options) + ").");
    }
}
