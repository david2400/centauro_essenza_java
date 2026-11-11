package com.essenza.draco.security;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import com.essenza.draco.shared.exceptions.ForbiddenException;
import com.essenza.draco.shared.exceptions.UnauthorizedException;
import com.essenza.draco.shared.security.MenuAccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Menús del usuario según access_control ({@code GET /api/access_control/menus/by-user-application/{id}}, con el
 * {@code ctx.user_application_id} del token). Se consultan con el token del propio usuario; si access_control lo
 * rechaza y hay credenciales de servicio ({@code essenza.access-control.client-id/secret}), con un token de servicio.
 *
 * <p>Se guardan en memoria por contexto (por defecto 60 s): un cambio de menús en access_control se ve en ese
 * tiempo. Si access_control no responde se usa la última lista conocida (hasta 10 min); sin ella, 503: no se
 * concede acceso sin verificarlo.
 *
 * <p>Las rutas se comparan sin idioma ({@code /es}, {@code /en}), sin dominio, sin barra final y sin mayúsculas;
 * un menú también cubre sus subrutas ({@code /logistica/dispatches} cubre {@code /logistica/dispatches/12}).
 */
@Component
public class AccessControlMenuAccess implements MenuAccess {

    private static final Logger log = LoggerFactory.getLogger(AccessControlMenuAccess.class);
    static final String MENUS_PATH = "/api/access_control/menus/by-user-application/";
    static final Duration TIMEOUT = Duration.ofSeconds(5);
    static final Duration STALE_LIMIT = Duration.ofMinutes(10);

    private final boolean enabled;
    private final boolean enforce;
    private final boolean devMode;
    private final Duration ttl;
    private final String baseUrl;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String serviceScope;
    private final Clock clock;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    private String serviceToken;
    private Instant serviceTokenExpiresAt = Instant.EPOCH;

    record Entry(Set<String> paths, Instant loadedAt) {
    }

    public AccessControlMenuAccess(
            @Value("${essenza.menu-access.enabled:true}") boolean enabled,
            @Value("${essenza.menu-access.cache-seconds:60}") long cacheSeconds,
            @Value("${essenza.security.enforce-authentication:false}") boolean enforce,
            @Value("${essenza.marketplace.dev-company-id:}") String devCompanyId,
            @Value("${essenza.access-control.base-url:${essenza.security.issuer:http://localhost:8000}}") String baseUrl,
            @Value("${essenza.access-control.token-url:}") String tokenUrl,
            @Value("${essenza.access-control.client-id:essenza-tienda-service}") String clientId,
            @Value("${essenza.access-control.client-secret:}") String clientSecret,
            @Value("${essenza.menu-access.service-scope:}") String serviceScope) {
        this.enabled = enabled;
        this.enforce = enforce;
        this.devMode = !enforce && devCompanyId != null && !devCompanyId.isBlank();
        this.ttl = Duration.ofSeconds(Math.max(5, cacheSeconds));
        this.baseUrl = trimSlash(baseUrl);
        this.tokenUrl = tokenUrl == null || tokenUrl.isBlank() ? this.baseUrl + "/oauth2/token" : tokenUrl.trim();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.serviceScope = serviceScope == null ? "" : serviceScope.trim();
        this.clock = Clock.systemUTC();
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        if (!enabled) {
            log.warn("essenza.menu-access.enabled=false: los módulos protegidos por menú no verifican el menú del usuario.");
        }
    }

    @Override
    public boolean hasAny(String... paths) {
        if (!enabled) {
            return true;
        }
        String token = bearer();
        if (token == null) {
            if (devMode && !enforce) {
                return true; // usuario de desarrollo local sin login (ver TokenActorProvider)
            }
            throw new UnauthorizedException("Inicia sesión para usar este módulo.");
        }
        JsonNode context = TokenActorProvider.claims(token).path("ctx").path("user_application_id");
        if (!context.canConvertToLong() || context.asLong() <= 0) {
            throw new ForbiddenException("El token no trae el contexto de la aplicación (user_application_id).");
        }
        Set<String> menus = menus(context.asLong(), token);
        for (String path : paths) {
            if (covers(menus, normalize(path))) {
                return true;
            }
        }
        return false;
    }

    Set<String> menus(long userApplicationId, String userToken) {
        Instant now = clock.instant();
        Entry cached = cache.get(userApplicationId);
        if (cached != null && cached.loadedAt().plus(ttl).isAfter(now)) {
            return cached.paths();
        }
        try {
            Set<String> fresh = fetch(userApplicationId, userToken);
            cache.put(userApplicationId, new Entry(fresh, now));
            return fresh;
        } catch (IOException | RuntimeException ex) {
            if (cached != null && cached.loadedAt().plus(STALE_LIMIT).isAfter(now)) {
                log.warn("menu_access stale user_application={} reason={}", userApplicationId, ex.getMessage());
                return cached.paths();
            }
            log.warn("menu_access unavailable user_application={} reason={}", userApplicationId, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudieron consultar tus menús en access_control. Intenta de nuevo en un momento.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Consulta de menús interrumpida.");
        }
    }

    private Set<String> fetch(long userApplicationId, String userToken) throws IOException, InterruptedException {
        String url = baseUrl + MENUS_PATH + userApplicationId;
        HttpResponse<String> response = get(url, userToken);
        if ((response.statusCode() == 401 || response.statusCode() == 403) && hasServiceCredentials()) {
            response = get(url, serviceToken(false));
            if (response.statusCode() == 401) {
                response = get(url, serviceToken(true));
            }
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("access_control respondió HTTP " + response.statusCode() + " a los menús");
        }
        Set<String> paths = new LinkedHashSet<>();
        JsonNode body = json.readTree(response.body());
        JsonNode list = body.isArray() ? body : body.path("data");
        list.forEach(menu -> {
            for (String field : new String[] {"path", "url", "href"}) {
                String value = menu.path(field).asText("");
                if (!value.isBlank()) {
                    paths.add(normalize(value));
                }
            }
        });
        paths.remove("");
        return Set.copyOf(paths);
    }

    private HttpResponse<String> get(String url, String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
                .header("Accept", "application/json").header("Authorization", "Bearer " + token).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private boolean hasServiceCredentials() {
        return clientSecret != null && !clientSecret.isBlank();
    }

    private synchronized String serviceToken(boolean renew) throws IOException, InterruptedException {
        if (!renew && serviceToken != null && Instant.now().isBefore(serviceTokenExpiresAt)) {
            return serviceToken;
        }
        String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        String form = "grant_type=client_credentials"
                + (serviceScope.isEmpty() ? "" : "&scope=" + URLEncoder.encode(serviceScope, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl)).timeout(TIMEOUT)
                .header("Authorization", "Basic " + basic).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("access_control no entregó el token de servicio (HTTP " + response.statusCode() + ")");
        }
        JsonNode body = json.readTree(response.body());
        serviceToken = body.path("access_token").asText(null);
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IOException("access_control respondió sin access_token");
        }
        serviceTokenExpiresAt = Instant.now().plusSeconds(Math.max(30, body.path("expires_in").asLong(300) - 30));
        return serviceToken;
    }

    /** "/es/Logistica/shipping-costs/" o "http://x/en/logistica/shipping-costs?a=1" → "/logistica/shipping-costs". */
    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String path = value.trim();
        int scheme = path.indexOf("://");
        if (scheme >= 0) {
            int slash = path.indexOf('/', scheme + 3);
            path = slash < 0 ? "/" : path.substring(slash);
        }
        int cut = indexOfAny(path, '?', '#');
        if (cut >= 0) {
            path = path.substring(0, cut);
        }
        path = path.toLowerCase(Locale.ROOT).replaceAll("/+", "/");
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.matches("/(es|en)(/.*)?")) {
            path = path.substring(3);
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path.isEmpty() ? "/" : path;
    }

    /** Un menú cubre su ruta y sus subrutas; el menú raíz "/" no cubre nada más. */
    static boolean covers(Set<String> menus, String required) {
        for (String menu : menus) {
            if (menu.equals(required) || (!menu.equals("/") && required.startsWith(menu + "/"))) {
                return true;
            }
        }
        return false;
    }

    private static int indexOfAny(String s, char a, char b) {
        int i = s.indexOf(a);
        int j = s.indexOf(b);
        return i < 0 ? j : j < 0 ? i : Math.min(i, j);
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

    private static String trimSlash(String url) {
        String value = url == null ? "" : url.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
