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
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

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
import com.essenza.draco.shared.security.CompanyUsers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Usuarios con acceso a la aplicación de la empresa del token, según access_control:
 * {@code GET /api/user-applications/{ctx.user_application_id}} da la licencia de la empresa
 * ({@code companyApplicationId}) y {@code GET /api/user-applications/application/{companyApplicationId}} sus usuarios.
 * Se consulta con el token del usuario; si access_control lo rechaza y hay credenciales de servicio, con un token de
 * servicio (como {@link AccessControlMenuAccess}).
 *
 * <p>Solo se queda con id, usuario y nombre: el resto de la respuesta (claves, tokens) nunca sale de aquí. Se descarta
 * cualquier asignación de otra empresa aunque access_control la devuelva. Caché por licencia de 60 s; si
 * access_control no responde se usa la última lista (hasta 10 min) y sin ella, 503.
 *
 * <p>Sin token en modo desarrollo local devuelve una lista vacía: quien la usa cae al usuario de desarrollo.
 */
@Component
public class AccessControlCompanyUsers implements CompanyUsers {

    private static final Logger log = LoggerFactory.getLogger(AccessControlCompanyUsers.class);
    static final String API = "/api/access_control/api/user-applications/";
    static final Duration TIMEOUT = Duration.ofSeconds(5);
    static final Duration STALE_LIMIT = Duration.ofMinutes(10);
    private static final Pattern INACTIVE = Pattern.compile("(?i).*(inactiv|bloque|block|disabl|suspend|delet|elimin).*");

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
    /** user_application_id → licencia (company_application_id); no cambia mientras el token viva. */
    private final Map<Long, Long> licenses = new ConcurrentHashMap<>();
    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    private String serviceToken;
    private Instant serviceTokenExpiresAt = Instant.EPOCH;

    record Entry(List<CompanyUser> users, Instant loadedAt) {
    }

    public AccessControlCompanyUsers(
            @Value("${essenza.company-users.cache-seconds:60}") long cacheSeconds,
            @Value("${essenza.security.enforce-authentication:false}") boolean enforce,
            @Value("${essenza.marketplace.dev-company-id:}") String devCompanyId,
            @Value("${essenza.access-control.base-url:${essenza.security.issuer:http://localhost:8000}}") String baseUrl,
            @Value("${essenza.access-control.token-url:}") String tokenUrl,
            @Value("${essenza.access-control.client-id:essenza-tienda-service}") String clientId,
            @Value("${essenza.access-control.client-secret:}") String clientSecret,
            @Value("${essenza.menu-access.service-scope:}") String serviceScope) {
        this.devMode = !enforce && devCompanyId != null && !devCompanyId.isBlank();
        this.ttl = Duration.ofSeconds(Math.max(5, cacheSeconds));
        String base = baseUrl == null ? "" : baseUrl.trim();
        this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.tokenUrl = tokenUrl == null || tokenUrl.isBlank() ? this.baseUrl + "/oauth2/token" : tokenUrl.trim();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.serviceScope = serviceScope == null ? "" : serviceScope.trim();
        this.clock = Clock.systemUTC();
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public List<CompanyUser> active() {
        String token = bearer();
        if (token == null) {
            if (devMode) {
                return List.of();
            }
            throw new com.essenza.draco.shared.exceptions.UnauthorizedException("Inicia sesión para usar este módulo.");
        }
        JsonNode ctx = TokenActorProvider.claims(token).path("ctx");
        JsonNode context = ctx.path("user_application_id");
        JsonNode company = ctx.path("company_id");
        if (!context.canConvertToLong() || context.asLong() <= 0 || !company.canConvertToLong()) {
            throw new ForbiddenException("El token no trae el contexto de la aplicación (user_application_id).");
        }
        return users(context.asLong(), company.asLong(), token);
    }

    List<CompanyUser> users(long userApplicationId, long companyId, String userToken) {
        Instant now = clock.instant();
        Long license = licenses.get(userApplicationId);
        Entry cached = license == null ? null : cache.get(license);
        if (cached != null && cached.loadedAt().plus(ttl).isAfter(now)) {
            return cached.users();
        }
        try {
            if (license == null) {
                license = license(userApplicationId, companyId, userToken);
                licenses.put(userApplicationId, license);
            }
            List<CompanyUser> fresh = fetch(license, companyId, userToken);
            cache.put(license, new Entry(fresh, now));
            return fresh;
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof ForbiddenException forbidden) {
                throw forbidden;
            }
            if (cached != null && cached.loadedAt().plus(STALE_LIMIT).isAfter(now)) {
                log.warn("company_users stale user_application={} reason={}", userApplicationId, ex.getMessage());
                return cached.users();
            }
            log.warn("company_users unavailable user_application={} reason={}", userApplicationId, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudieron consultar los usuarios en access_control. Intenta de nuevo en un momento.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Consulta de usuarios interrumpida.");
        }
    }

    private long license(long userApplicationId, long companyId, String userToken) throws IOException, InterruptedException {
        JsonNode own = unwrap(read(baseUrl + API + userApplicationId, userToken));
        if (own.path("companyId").canConvertToLong() && own.path("companyId").asLong() != companyId) {
            throw new ForbiddenException("El contexto del token no corresponde a la empresa.");
        }
        JsonNode license = own.path("companyApplicationId");
        if (!license.canConvertToLong() || license.asLong() <= 0) {
            throw new IOException("access_control no devolvió la licencia de la aplicación");
        }
        return license.asLong();
    }

    private List<CompanyUser> fetch(long license, long companyId, String userToken) throws IOException, InterruptedException {
        JsonNode body = read(baseUrl + API + "application/" + license, userToken);
        return parse(body.isArray() ? body : body.path("data"), companyId, clock.instant());
    }

    /** Asignaciones activas, vigentes y de esa empresa; un usuario una sola vez. */
    static List<CompanyUser> parse(JsonNode list, long companyId, Instant now) {
        Map<String, CompanyUser> users = new LinkedHashMap<>();
        list.forEach(row -> {
            if (row.path("deleted").asBoolean(false) || !row.path("isActive").asBoolean(true)) {
                return;
            }
            if (row.path("companyId").canConvertToLong() && row.path("companyId").asLong() != companyId) {
                return;
            }
            if (expired(row.path("expiresAt").asText(""), now)) {
                return;
            }
            JsonNode user = row.path("user");
            if (user.path("deleted").asBoolean(false) || INACTIVE.matcher(user.path("status").asText("")).matches()) {
                return;
            }
            String id = text(user, "idUser");
            if (id == null) {
                id = text(row, "userId");
            }
            if (id == null) {
                return;
            }
            String username = text(user, "username");
            String name = fullName(user.path("client"));
            users.putIfAbsent(id, new CompanyUser(id, username == null ? id : username,
                    name != null ? name : username != null ? username : id));
        });
        List<CompanyUser> sorted = new ArrayList<>(users.values());
        sorted.sort(Comparator.comparing((CompanyUser u) -> u.name().toLowerCase(Locale.ROOT)));
        return List.copyOf(sorted);
    }

    private static boolean expired(String value, Instant now) {
        if (value.isBlank()) {
            return false;
        }
        try {
            return OffsetDateTime.parse(value).toInstant().isBefore(now);
        } catch (DateTimeParseException ex) {
            try {
                return java.time.LocalDateTime.parse(value).toInstant(java.time.ZoneOffset.UTC).isBefore(now);
            } catch (DateTimeParseException ignored) {
                return false;
            }
        }
    }

    private static String fullName(JsonNode client) {
        List<String> parts = new ArrayList<>();
        for (String field : new String[] {"firstName", "secondName", "firstLastName", "secondLastName"}) {
            String value = text(client, field);
            if (value != null) {
                parts.add(value);
            }
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText("").trim();
        return text.isEmpty() ? null : text;
    }

    private static JsonNode unwrap(JsonNode body) {
        return body.has("data") && body.path("data").isObject() ? body.path("data") : body;
    }

    private JsonNode read(String url, String userToken) throws IOException, InterruptedException {
        HttpResponse<String> response = get(url, userToken);
        if ((response.statusCode() == 401 || response.statusCode() == 403) && hasServiceCredentials()) {
            response = get(url, serviceToken(false));
            if (response.statusCode() == 401) {
                response = get(url, serviceToken(true));
            }
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("access_control respondió HTTP " + response.statusCode() + " a los usuarios");
        }
        return json.readTree(response.body());
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
}
