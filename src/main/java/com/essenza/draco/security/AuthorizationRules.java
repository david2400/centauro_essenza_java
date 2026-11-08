package com.essenza.draco.security;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.essenza.draco.shared.security.Capabilities;

/**
 * Reglas configurables que deciden qué acciones ({@link Capabilities}) tiene el usuario, a partir
 * de su token (aplicación Essenza ya validada por el filtro). Sin regla para una acción, la
 * puede usar cualquier usuario del contexto. Se configuran sin tocar código:
 * <pre>
 * essenza.authorization.rules[VENDEDORES.APROBAR]=role:ADMIN_TIENDA
 * essenza.authorization.rules[COMISIONES.AJUSTAR]=perm:FINANZAS.AJUSTES, role:GERENTE
 * essenza.authorization.rules[VENTAS.GESTIONAR]=*
 * </pre>
 * Valores: {@code *} (cualquiera), {@code none} (nadie), {@code perm:NOMBRE} (permiso del
 * token), {@code role:NOMBRE} (rol del token); varios separados por coma = basta uno. Un valor
 * mal escrito no da acceso (falla cerrado) y queda en el log.
 */
@Component
@ConfigurationProperties(prefix = "essenza.authorization")
public class AuthorizationRules {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationRules.class);

    private Map<String, String> rules = new LinkedHashMap<>();

    public Map<String, String> getRules() {
        return rules;
    }

    public void setRules(Map<String, String> rules) {
        this.rules = rules == null ? new LinkedHashMap<>() : rules;
    }

    /** Acciones concedidas a un usuario con esos permisos y roles en el contexto. */
    public Set<String> granted(Set<String> permissions, Set<String> roles) {
        Set<String> granted = new LinkedHashSet<>();
        for (String capability : Capabilities.keys()) {
            if (matches(capability, rules.get(capability), permissions, roles)) {
                granted.add(capability);
            }
        }
        return granted;
    }

    static boolean matches(String capability, String rule, Set<String> permissions, Set<String> roles) {
        if (rule == null || rule.isBlank()) {
            return true;
        }
        for (String raw : rule.split(",")) {
            String option = raw.trim();
            if (option.isEmpty()) {
                continue;
            }
            if ("*".equals(option)) {
                return true;
            }
            if ("none".equalsIgnoreCase(option)) {
                continue;
            }
            int colon = option.indexOf(':');
            String kind = colon < 0 ? "" : option.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String name = colon < 0 ? "" : option.substring(colon + 1).trim();
            if (name.isEmpty() || !(kind.equals("perm") || kind.equals("role"))) {
                log.warn("Regla de autorización no válida para {}: '{}' (use *, none, perm:NOMBRE o role:NOMBRE)", capability,
                        option);
                continue;
            }
            if (kind.equals("perm") ? permissions.contains(name) : roles.contains(name)) {
                return true;
            }
        }
        return false;
    }
}
