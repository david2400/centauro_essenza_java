package com.essenza.draco.shared.security;

import java.util.List;
import java.util.Optional;

/**
 * Usuarios de la empresa del token que tienen acceso a esta aplicación en access_control (asignación
 * usuario-aplicación activa y vigente). Sirve para elegir a una persona (p. ej. el cajero de un turno) sin aceptar
 * nombres escritos en el navegador.
 *
 * <p>Se pide después de {@link ActorProvider#current()} (que ya validó el token y la empresa).
 */
public interface CompanyUsers {

    /** Usuarios activos, ordenados por nombre. */
    List<CompanyUser> active();

    /** El usuario con ese id si está activo en la aplicación de esta empresa. */
    default Optional<CompanyUser> find(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        String key = userId.trim();
        return active().stream().filter(u -> u.id().equals(key)).findFirst();
    }

    /**
     * @param id       id del usuario en access_control
     * @param username usuario de inicio de sesión
     * @param name     nombre completo, o el usuario si no tiene datos personales
     */
    record CompanyUser(String id, String username, String name) {

        /** El sujeto del token puede ser el id o el usuario, según cómo emita access_control. */
        public boolean matches(String subject) {
            return subject != null && (subject.equals(id) || subject.equalsIgnoreCase(username));
        }
    }
}
