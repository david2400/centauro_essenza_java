package com.essenza.draco.shared.security;

import java.util.Arrays;

import com.essenza.draco.shared.exceptions.ForbiddenException;

/**
 * Acceso por menú: el usuario puede usar un módulo si en access_control tiene asignado el menú cuya ruta es la de
 * la pantalla (p. ej. {@code /logistica/shipping-costs}). No hay permisos con nombre: la ruta del menú es el permiso,
 * y se asigna en access_control por rol, permiso o usuario como cualquier menú.
 *
 * <p>Se pide después de {@link ActorProvider#current()} (que ya validó el token y la empresa).
 */
public interface MenuAccess {

    /** El usuario de la petición tiene al menos uno de esos menús. */
    boolean hasAny(String... paths);

    /** 403 si no tiene ninguno. Ocultar la opción en el panel no es autorización: esto sí. */
    default void requireAny(String... paths) {
        if (!hasAny(paths)) {
            throw new ForbiddenException("No tienes asignado el menú " + String.join(" o ", Arrays.asList(paths))
                    + " en access_control.");
        }
    }
}
