package com.essenza.draco.shared.security;

/**
 * Actor de la petición en curso. Lanza {@link com.essenza.draco.shared.exceptions.UnauthorizedException}
 * (401) si no hay un token válido y {@link com.essenza.draco.shared.exceptions.ForbiddenException}
 * (403) si el token no trae empresa (p. ej. tokens de servicio).
 */
public interface ActorProvider {

    RequestActor current();
}
