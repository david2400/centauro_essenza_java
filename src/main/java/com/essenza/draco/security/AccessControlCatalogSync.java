package com.essenza.draco.security;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.essenza.draco.shared.security.Capabilities;
import com.essenza.draco.shared.security.Menus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Registra en access_control, al arrancar, lo que draco necesita: los permisos del catálogo {@link Capabilities}
 * (p. ej. {@code CAJA.GESTIONAR}) y los menús por URL de {@link Menus} en la aplicación configurada, y se los asigna a
 * los roles administradores configurados.
 *
 * <p>Solo crea lo que falta: nunca cambia ni borra permisos, menús ni asignaciones existentes, así que se puede correr
 * en cada arranque. Apagado por defecto ({@code essenza.access-control.sync.enabled=false}); pensado para desarrollo y
 * ambientes nuevos. Usa el token de servicio ({@code essenza.access-control.client-id/secret}); sin credenciales no
 * hace nada. Corre en segundo plano y reintenta si access_control todavía no está arriba: el arranque de draco no
 * depende de él.
 *
 * <pre>
 * essenza.access-control.sync.enabled=true
 * essenza.access-control.sync.application=ESSENZA
 * essenza.access-control.sync.admin-roles=ESSENZA_ADMIN
 * </pre>
 *
 * Un usuario ya conectado ve los permisos nuevos al volver a iniciar sesión (van en el token); los menús se ven en
 * máximo {@code essenza.menu-access.cache-seconds}.
 */
@Component
public class AccessControlCatalogSync {

    private static final Logger log = LoggerFactory.getLogger(AccessControlCatalogSync.class);
    static final String API = "/api/access_control";

    private final boolean enabled;
    private final String application;
    private final List<String> adminRoles;
    private final int maxAttempts;
    private final Api api;
    private final AtomicBoolean started = new AtomicBoolean();

    /** Lo que hizo una corrida; para el log y las pruebas. */
    record Report(int permissionsCreated, int menusCreated, int rolePermissionsAssigned, int roleMenusAssigned,
            List<String> warnings) {

        @Override
        public String toString() {
            return "permisos creados=" + permissionsCreated + ", menús creados=" + menusCreated
                    + ", permisos asignados a roles=" + rolePermissionsAssigned + ", menús asignados a roles="
                    + roleMenusAssigned + (warnings.isEmpty() ? "" : ", avisos=" + warnings);
        }
    }

    /** Llamadas a access_control (con token de servicio); separado para probar sin red. */
    interface Api {
        boolean ready();

        JsonNode get(String path) throws IOException, InterruptedException;

        /** Devuelve el cuerpo; un 409 (ya existía) no es error. */
        JsonNode post(String path, JsonNode body) throws IOException, InterruptedException;
    }

    public AccessControlCatalogSync(
            @Value("${essenza.access-control.sync.enabled:false}") boolean enabled,
            @Value("${essenza.access-control.sync.application:ESSENZA}") String application,
            @Value("${essenza.access-control.sync.admin-roles:ESSENZA_ADMIN}") String adminRoles,
            @Value("${essenza.access-control.sync.max-attempts:10}") int maxAttempts,
            @Value("${essenza.access-control.base-url:${essenza.security.issuer:http://localhost:8000}}") String baseUrl,
            @Value("${essenza.access-control.token-url:}") String tokenUrl,
            @Value("${essenza.access-control.client-id:essenza-tienda-service}") String clientId,
            @Value("${essenza.access-control.client-secret:}") String clientSecret,
            @Value("${essenza.menu-access.service-scope:}") String serviceScope) {
        this(enabled, application, adminRoles, maxAttempts,
                new HttpApi(baseUrl, tokenUrl, clientId, clientSecret, serviceScope));
    }

    AccessControlCatalogSync(boolean enabled, String application, String adminRoles, int maxAttempts, Api api) {
        this.enabled = enabled;
        this.application = application == null ? "ESSENZA" : application.trim();
        this.adminRoles = new ArrayList<>();
        if (adminRoles != null) {
            for (String role : adminRoles.split(",")) {
                if (!role.isBlank()) {
                    this.adminRoles.add(role.trim());
                }
            }
        }
        this.maxAttempts = Math.max(1, maxAttempts);
        this.api = api;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onStart() {
        if (!enabled || !started.compareAndSet(false, true)) {
            return;
        }
        if (!api.ready()) {
            log.warn("access_control sync: falta essenza.access-control.client-secret; no se registran permisos ni menús.");
            return;
        }
        Thread worker = new Thread(this::runWithRetries, "access-control-sync");
        worker.setDaemon(true);
        worker.start();
    }

    private void runWithRetries() {
        long waitSeconds = 10;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Report report = sync();
                log.info("access_control sync ({}): {}", application, report);
                return;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException | RuntimeException ex) {
                log.warn("access_control sync intento {}/{} falló: {}", attempt, maxAttempts, ex.getMessage());
            }
            try {
                Thread.sleep(Duration.ofSeconds(waitSeconds).toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            waitSeconds = Math.min(120, waitSeconds * 2);
        }
        log.warn("access_control sync: se agotaron los intentos; revisa que access_control esté arriba y el cliente de servicio.");
    }

    /** Una corrida completa e idempotente. */
    Report sync() throws IOException, InterruptedException {
        List<String> warnings = new ArrayList<>();
        long appId = applicationId();

        // 1. Permisos del catálogo.
        Map<String, Long> permissions = new LinkedHashMap<>();
        for (JsonNode p : list(api.get(API + "/permissions?aplicationsId=" + appId))) {
            if (!p.path("deleted").asBoolean(false) && p.path("applicationId").asLong(appId) == appId) {
                permissions.putIfAbsent(p.path("name").asText(""), p.path("idPermission").asLong());
            }
        }
        int permissionsCreated = 0;
        for (Map.Entry<String, String> capability : sortedCapabilities().entrySet()) {
            if (permissions.containsKey(capability.getKey())) {
                continue;
            }
            JsonNode created = unwrap(api.post(API + "/permissions", permissionBody(appId, capability.getKey(), capability.getValue())));
            long id = created.path("idPermission").asLong(0);
            if (id <= 0) {
                warnings.add("permiso " + capability.getKey() + " sin id en la respuesta");
                continue;
            }
            permissions.put(capability.getKey(), id);
            permissionsCreated++;
        }

        // 2. Menús por URL.
        List<JsonNode> appMenus = new ArrayList<>();
        for (JsonNode m : list(api.get(API + "/menus"))) {
            if (!m.path("deleted").asBoolean(false) && m.path("applicationId").asLong(-1) == appId) {
                appMenus.add(m);
            }
        }
        Map<String, Long> menus = new LinkedHashMap<>();
        int menusCreated = 0;
        for (Map.Entry<String, String> menu : Menus.all().entrySet()) {
            String path = AccessControlMenuAccess.normalize(menu.getKey());
            Long existing = findMenu(appMenus, path);
            if (existing != null) {
                menus.put(path, existing);
                continue;
            }
            ObjectNode body = JSON.createObjectNode().put("applicationId", appId).put("name", menu.getValue())
                    .put("path", path).put("visible", true);
            Long parent = findMenu(appMenus, parentOf(path));
            if (parent != null) {
                body.put("parentMenuId", parent);
            }
            JsonNode created = unwrap(api.post(API + "/menus", body));
            long id = created.path("idMenu").asLong(0);
            if (id <= 0) {
                warnings.add("menú " + path + " sin id en la respuesta");
                continue;
            }
            appMenus.add(created);
            menus.put(path, id);
            menusCreated++;
        }

        // 3. Roles administradores.
        int rolePermissions = 0;
        int roleMenus = 0;
        for (String roleName : adminRoles) {
            List<JsonNode> roles = new ArrayList<>();
            for (JsonNode r : list(api.get(API + "/roles?name=" + URLEncoder.encode(roleName, StandardCharsets.UTF_8)))) {
                if (!r.path("deleted").asBoolean(false) && roleName.equalsIgnoreCase(r.path("name").asText(""))) {
                    roles.add(r);
                }
            }
            if (roles.isEmpty()) {
                warnings.add("no existe el rol " + roleName);
            }
            for (JsonNode role : roles) {
                long roleId = role.path("idRole").asLong();
                for (String capability : Capabilities.keys()) {
                    Long permissionId = permissions.get(capability);
                    if (permissionId == null) {
                        continue;
                    }
                    if (!api.get(API + "/role-permissions/check/" + roleId + "/" + permissionId).asBoolean(false)) {
                        api.post(API + "/role-permissions/role/" + roleId + "/permission/" + permissionId, null);
                        rolePermissions++;
                    }
                }
                for (Long menuId : menus.values()) {
                    boolean assigned = false;
                    for (JsonNode mp : list(api.get(API + "/menu-permissions/menu/" + menuId))) {
                        if (!mp.path("deleted").asBoolean(false) && mp.path("roleId").asLong(-1) == roleId) {
                            assigned = true;
                            break;
                        }
                    }
                    if (assigned) {
                        continue;
                    }
                    ObjectNode body = JSON.createObjectNode().put("menuId", menuId).put("roleId", roleId)
                            .put("consultPermission", true).put("createPermission", true).put("updatePermission", true)
                            .put("deletePermission", true);
                    if (role.path("companyId").canConvertToLong() && role.path("companyId").asLong() > 0) {
                        body.put("companyId", role.path("companyId").asLong());
                    }
                    api.post(API + "/menu-permissions", body);
                    roleMenus++;
                }
            }
        }
        return new Report(permissionsCreated, menusCreated, rolePermissions, roleMenus, List.copyOf(warnings));
    }

    private long applicationId() throws IOException, InterruptedException {
        for (JsonNode a : list(api.get(API + "/applications?name=" + URLEncoder.encode(application, StandardCharsets.UTF_8)))) {
            if (!a.path("deleted").asBoolean(false) && application.equalsIgnoreCase(a.path("name").asText(""))) {
                return a.path("idApplication").asLong();
            }
        }
        throw new IOException("no existe la aplicación " + application + " en access_control");
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    /** VENTAS.VER → recurso "ventas", acción READ; el resto, UPDATE (como el script de permisos del marketplace). */
    static ObjectNode permissionBody(long appId, String name, String description) {
        int dot = name.indexOf('.');
        String resource = (dot < 0 ? name : name.substring(0, dot)).toLowerCase(Locale.ROOT);
        String action = name.endsWith(".VER") ? "READ" : "UPDATE";
        return JSON.createObjectNode().put("name", name).put("description", description).put("permissionType", "APPLICATION")
                .put("resource", resource).put("action", action).put("applicationId", appId);
    }

    private static Map<String, String> sortedCapabilities() {
        Map<String, String> descriptions = Capabilities.descriptions();
        Map<String, String> ordered = new LinkedHashMap<>();
        for (String key : Capabilities.keys()) {
            ordered.put(key, descriptions.get(key));
        }
        return ordered;
    }

    private static Long findMenu(List<JsonNode> menus, String path) {
        if (path == null) {
            return null;
        }
        for (JsonNode m : menus) {
            String value = m.path("path").asText("");
            if (!value.isBlank() && AccessControlMenuAccess.normalize(value).equals(path)) {
                return m.path("idMenu").asLong();
            }
        }
        return null;
    }

    /** "/logistica/dispatches" → "/logistica"; una ruta de un solo nivel no tiene padre. */
    static String parentOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? null : path.substring(0, slash);
    }

    private static List<JsonNode> list(JsonNode body) {
        JsonNode list = body == null ? null : body.isArray() ? body : body.path("data").isArray() ? body.path("data")
                : body.path("content");
        List<JsonNode> out = new ArrayList<>();
        if (list != null && list.isArray()) {
            list.forEach(out::add);
        }
        return out;
    }

    private static JsonNode unwrap(JsonNode body) {
        return body != null && body.path("data").isObject() ? body.path("data") : body == null ? JSON.createObjectNode() : body;
    }

    /** HTTP real con token de servicio (client_credentials). */
    static final class HttpApi implements Api {

        private static final Duration TIMEOUT = Duration.ofSeconds(10);
        private final String baseUrl;
        private final String tokenUrl;
        private final String clientId;
        private final String clientSecret;
        private final String scope;
        private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        private String token;
        private Instant expiresAt = Instant.EPOCH;

        HttpApi(String baseUrl, String tokenUrl, String clientId, String clientSecret, String scope) {
            String base = baseUrl == null ? "" : baseUrl.trim();
            this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
            this.tokenUrl = tokenUrl == null || tokenUrl.isBlank() ? this.baseUrl + "/oauth2/token" : tokenUrl.trim();
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.scope = scope == null ? "" : scope.trim();
        }

        @Override
        public boolean ready() {
            return clientSecret != null && !clientSecret.isBlank();
        }

        @Override
        public JsonNode get(String path) throws IOException, InterruptedException {
            return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET(), path);
        }

        @Override
        public JsonNode post(String path, JsonNode body) throws IOException, InterruptedException {
            HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body));
            return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).header("Content-Type", "application/json")
                    .POST(publisher), path);
        }

        private JsonNode send(HttpRequest.Builder builder, String path) throws IOException, InterruptedException {
            HttpResponse<String> response = http.send(builder.copy().timeout(TIMEOUT).header("Accept", "application/json")
                    .header("Authorization", "Bearer " + token(false)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                response = http.send(builder.timeout(TIMEOUT).header("Accept", "application/json")
                        .header("Authorization", "Bearer " + token(true)).build(), HttpResponse.BodyHandlers.ofString());
            }
            int status = response.statusCode();
            if ((status < 200 || status >= 300) && status != 409) {
                // Solo el estado y la ruta: el cuerpo puede traer datos internos.
                throw new IOException("access_control respondió HTTP " + status + " a " + path.replaceAll("\\?.*", ""));
            }
            String body = response.body();
            return body == null || body.isBlank() ? JSON.createObjectNode() : JSON.readTree(body);
        }

        private synchronized String token(boolean renew) throws IOException, InterruptedException {
            if (!renew && token != null && Instant.now().isBefore(expiresAt)) {
                return token;
            }
            String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
            String form = "grant_type=client_credentials"
                    + (scope.isEmpty() ? "" : "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8));
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(tokenUrl)).timeout(TIMEOUT)
                    .header("Authorization", "Basic " + basic).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("access_control no entregó el token de servicio (HTTP " + response.statusCode() + ")");
            }
            JsonNode body = JSON.readTree(response.body());
            token = body.path("access_token").asText(null);
            if (token == null || token.isBlank()) {
                throw new IOException("access_control respondió sin access_token");
            }
            expiresAt = Instant.now().plusSeconds(Math.max(30, body.path("expires_in").asLong(300) - 30));
            return token;
        }
    }
}
