package com.essenza.draco.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.essenza.draco.shared.exceptions.ForbiddenException;
import com.essenza.draco.shared.exceptions.UnauthorizedException;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Actor desde el access token de access_control.
 *
 * <p>La firma, el emisor, la audiencia y la vigencia los valida antes el filtro de
 * essenza-security ({@code AccessControlAuthenticationFilter}); aquí solo se acepta el token si
 * ese filtro dejó una autenticación en el contexto, y entonces se leen sus claims
 * ({@code sub}, {@code ctx.company_id}, {@code permissions}). Sin autenticación: 401, aunque el
 * servicio esté en modo observación. Un token sin empresa (p. ej. de servicio): 403.
 *
 * <p>Solo para desarrollo local sin login: con {@code essenza.security.enforce-authentication=false}
 * y {@code essenza.marketplace.dev-company-id} definido, una petición SIN token actúa como un
 * usuario de desarrollo con todos los permisos del marketplace en esa empresa. Por defecto está
 * apagado y nunca aplica si la autenticación es obligatoria.
 */
@Component
public class TokenActorProvider implements ActorProvider {

    private static final Logger log = LoggerFactory.getLogger(TokenActorProvider.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final boolean enforce;
    private final Long devCompanyId;
    private final Set<String> devPermissions;
    private final AuthorizationRules rules;

    public TokenActorProvider(
            @Value("${essenza.security.enforce-authentication:false}") boolean enforce,
            @Value("${essenza.marketplace.dev-company-id:}") String devCompanyId,
            @Value("${essenza.marketplace.dev-permissions:}") String devPermissions,
            AuthorizationRules rules) {
        this.enforce = enforce;
        this.devCompanyId = devCompanyId == null || devCompanyId.isBlank() || enforce ? null : Long.valueOf(devCompanyId.trim());
        // Usuario de desarrollo: todas las acciones, salvo que se limite con dev-permissions.
        this.devPermissions = devPermissions == null || devPermissions.isBlank()
                ? Set.copyOf(com.essenza.draco.shared.security.Capabilities.keys())
                : Set.of(devPermissions.trim().split("\\s*,\\s*"));
        this.rules = rules;
        if (this.devCompanyId != null) {
            log.warn("Marketplace en modo desarrollo: peticiones sin token actúan como 'dev' en la empresa {}."
                    + " No usar fuera de un equipo local.", this.devCompanyId);
        }
    }

    @Override
    public RequestActor current() {
        // Actor fijado por el servidor (tienda pública); nunca viene del navegador.
        java.util.Optional<RequestActor> scoped = com.essenza.draco.shared.security.ActorScope.current();
        if (scoped.isPresent()) {
            return scoped.get();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String header = bearer();
        boolean authenticated = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        if (!authenticated || header == null) {
            if (header == null && devCompanyId != null && !enforce) {
                return new RequestActor(devCompanyId, "dev", "dev (local)", devPermissions);
            }
            if (header != null) {
                // Llegó un token pero essenza-security no lo validó: se registra el porqué probable
                // (sin el token) para diagnosticar emisor, audiencia o vigencia.
                log.warn("Token rechazado en {}: el filtro de seguridad no lo autenticó. {}", path(), describe(header));
            }
            throw new UnauthorizedException("Inicia sesión para usar este módulo.");
        }
        JsonNode claims = claims(header);
        JsonNode company = claims.path("ctx").path("company_id");
        if (!company.canConvertToLong() || company.asLong() <= 0) {
            throw new ForbiddenException("El token no tiene una empresa elegida.");
        }
        String sub = claims.path("sub").asText("");
        if (sub.isBlank()) {
            throw new UnauthorizedException("Token sin sujeto.");
        }
        Set<String> permissions = new LinkedHashSet<>();
        claims.path("permissions").forEach(p -> permissions.add(p.asText()));
        Set<String> roles = new LinkedHashSet<>();
        claims.path("roles").forEach(r -> roles.add(r.asText()));
        String name = firstText(claims, "preferred_username", "username", "name");
        // Las acciones salen de las reglas configuradas (essenza.authorization.rules), no de nombres fijos.
        return new RequestActor(company.asLong(), sub, name == null ? sub : name, rules.granted(permissions, roles));
    }

    private static String bearer() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return null;
        }
        String value = servlet.getRequest().getHeader("Authorization");
        if (value == null || !value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = value.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private static String path() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest().getRequestURI() : "?";
    }

    /** Emisor, audiencia, vencimiento y empresa del token (nunca el token ni la firma). */
    static String describe(String jwt) {
        try {
            JsonNode claims = claims(jwt);
            long exp = claims.path("exp").asLong(0);
            long now = System.currentTimeMillis() / 1000;
            return "iss=" + claims.path("iss").asText("?") + ", aud=" + claims.path("aud")
                    + ", exp=" + (exp == 0 ? "?" : (exp < now ? "vencido hace " + (now - exp) + " s" : "vigente " + (exp - now) + " s"))
                    + ", ctx.company_id=" + claims.path("ctx").path("company_id").asText("-")
                    + ", alg=" + header(jwt);
        } catch (RuntimeException ex) {
            return "no se pudo leer el token: " + ex.getMessage();
        }
    }

    private static String header(String jwt) {
        try {
            JsonNode node = JSON.readTree(new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[0]), StandardCharsets.UTF_8));
            return node.path("alg").asText("?") + "/" + node.path("kid").asText("-");
        } catch (Exception ex) {
            return "?";
        }
    }

    /** Claims del JWT ya validado (la firma la verificó el filtro). */
    static JsonNode claims(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new UnauthorizedException("Token con formato inválido.");
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            return JSON.readTree(new String(payload, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new UnauthorizedException("Token con formato inválido.");
        }
    }

    private static String firstText(JsonNode node, String... names) {
        for (String name : names) {
            String value = node.path(name).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
