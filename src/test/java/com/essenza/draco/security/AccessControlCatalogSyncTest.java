package com.essenza.draco.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.essenza.draco.shared.security.Capabilities;
import com.essenza.draco.shared.security.Menus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

class AccessControlCatalogSyncTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** access_control en memoria: aplicaciones, permisos, menús, roles y sus asignaciones. */
    static final class FakeAccessControl implements AccessControlCatalogSync.Api {
        final ArrayNode permissions = JSON.createArrayNode();
        final ArrayNode menus = JSON.createArrayNode();
        final ArrayNode roles = JSON.createArrayNode();
        final List<String> rolePermissions = new ArrayList<>();
        final ArrayNode menuPermissions = JSON.createArrayNode();
        final List<String> calls = new ArrayList<>();
        long nextId = 100;

        @Override
        public boolean ready() {
            return true;
        }

        @Override
        public JsonNode get(String path) {
            calls.add("GET " + path);
            String p = path.substring(AccessControlCatalogSync.API.length());
            if (p.startsWith("/applications")) {
                return JSON.createArrayNode().add(JSON.createObjectNode().put("idApplication", 5).put("name", "ESSENZA"));
            }
            if (p.startsWith("/permissions")) {
                return permissions;
            }
            if (p.equals("/menus")) {
                return menus;
            }
            if (p.startsWith("/roles")) {
                return roles;
            }
            if (p.startsWith("/role-permissions/check/")) {
                String[] parts = p.substring("/role-permissions/check/".length()).split("/");
                return JSON.getNodeFactory().booleanNode(rolePermissions.contains(parts[0] + ":" + parts[1]));
            }
            if (p.startsWith("/menu-permissions/menu/")) {
                long menuId = Long.parseLong(p.substring("/menu-permissions/menu/".length()));
                ArrayNode out = JSON.createArrayNode();
                menuPermissions.forEach(mp -> {
                    if (mp.path("menuId").asLong() == menuId) {
                        out.add(mp);
                    }
                });
                return out;
            }
            throw new IllegalStateException(path);
        }

        @Override
        public JsonNode post(String path, JsonNode body) {
            calls.add("POST " + path);
            String p = path.substring(AccessControlCatalogSync.API.length());
            if (p.equals("/permissions")) {
                ObjectNode created = ((ObjectNode) body).deepCopy().put("idPermission", nextId++);
                permissions.add(created);
                return created;
            }
            if (p.equals("/menus")) {
                ObjectNode created = ((ObjectNode) body).deepCopy().put("idMenu", nextId++);
                menus.add(created);
                return created;
            }
            if (p.startsWith("/role-permissions/role/")) {
                String[] parts = p.substring("/role-permissions/role/".length()).split("/permission/");
                rolePermissions.add(parts[0] + ":" + parts[1]);
                return JSON.createObjectNode();
            }
            if (p.equals("/menu-permissions")) {
                menuPermissions.add(body);
                return body;
            }
            throw new IllegalStateException(path);
        }
    }

    private FakeAccessControl seeded() {
        FakeAccessControl ac = new FakeAccessControl();
        ac.permissions.add(JSON.createObjectNode().put("idPermission", 1).put("name", "CAJA.VER").put("applicationId", 5));
        ac.permissions.add(JSON.createObjectNode().put("idPermission", 2).put("name", "CAJA.GESTIONAR").put("applicationId", 9));
        ac.menus.add(JSON.createObjectNode().put("idMenu", 10).put("applicationId", 5).put("name", "Logística").put("path", "/logistica"));
        ac.menus.add(JSON.createObjectNode().put("idMenu", 11).put("applicationId", 5).put("name", "Órdenes").put("path", "/es/Ventas/orders/"));
        ac.menus.add(JSON.createObjectNode().put("idMenu", 12).put("applicationId", 7).put("name", "Otra app").put("path", "/logistica/dispatches"));
        ac.roles.add(JSON.createObjectNode().put("idRole", 3).put("name", "ESSENZA_ADMIN").put("companyId", 1));
        ac.roles.add(JSON.createObjectNode().put("idRole", 4).put("name", "ESSENZA_ADMIN_LECTOR"));
        ac.rolePermissions.add("3:1");
        return ac;
    }

    @Test
    void createsOnlyWhatIsMissingAndAssignsItToTheAdminRole() throws Exception {
        FakeAccessControl ac = seeded();
        AccessControlCatalogSync sync = new AccessControlCatalogSync(true, "ESSENZA", "ESSENZA_ADMIN", 1, ac);

        AccessControlCatalogSync.Report report = sync.sync();

        int capabilities = Capabilities.keys().size();
        // CAJA.VER ya estaba en ESSENZA; el CAJA.GESTIONAR de otra aplicación no cuenta.
        assertThat(report.permissionsCreated()).isEqualTo(capabilities - 1);
        // /ventas/orders ya estaba (otra forma de escribirla); el despacho de otra aplicación no cuenta.
        assertThat(report.menusCreated()).isEqualTo(Menus.all().size() - 1);
        assertThat(report.rolePermissionsAssigned()).isEqualTo(capabilities - 1);
        assertThat(report.roleMenusAssigned()).isEqualTo(Menus.all().size());
        assertThat(report.warnings()).isEmpty();

        JsonNode dispatches = null;
        for (JsonNode m : ac.menus) {
            if (m.path("path").asText().equals("/logistica/dispatches") && m.path("applicationId").asLong() == 5) {
                dispatches = m;
            }
        }
        assertThat(dispatches).isNotNull();
        assertThat(dispatches.path("parentMenuId").asLong()).isEqualTo(10); // bajo "Logística"
        ac.menuPermissions.forEach(mp -> {
            assertThat(mp.path("roleId").asLong()).isEqualTo(3); // el rol de nombre parecido no recibe nada
            assertThat(mp.path("companyId").asLong()).isEqualTo(1);
        });
        assertThat(ac.calls).noneMatch(c -> c.startsWith("PUT") || c.startsWith("DELETE"));
    }

    @Test
    void aSecondRunChangesNothing() throws Exception {
        FakeAccessControl ac = seeded();
        AccessControlCatalogSync sync = new AccessControlCatalogSync(true, "ESSENZA", "ESSENZA_ADMIN", 1, ac);
        sync.sync();
        ac.calls.clear();

        AccessControlCatalogSync.Report again = sync.sync();

        assertThat(again.permissionsCreated()).isZero();
        assertThat(again.menusCreated()).isZero();
        assertThat(again.rolePermissionsAssigned()).isZero();
        assertThat(again.roleMenusAssigned()).isZero();
        assertThat(ac.calls).noneMatch(c -> c.startsWith("POST"));
    }

    @Test
    void aMissingRoleIsReportedNotInvented() throws Exception {
        FakeAccessControl ac = seeded();
        AccessControlCatalogSync.Report report = new AccessControlCatalogSync(true, "ESSENZA", "GERENTE", 1, ac).sync();
        assertThat(report.warnings()).containsExactly("no existe el rol GERENTE");
        assertThat(report.rolePermissionsAssigned()).isZero();
        assertThat(ac.calls).noneMatch(c -> c.startsWith("POST " + AccessControlCatalogSync.API + "/roles"));
    }

    @Test
    void permissionsFollowTheMarketplaceScriptConvention() {
        JsonNode view = AccessControlCatalogSync.permissionBody(5, "CAJA.VER", "Consultar");
        assertThat(view.path("resource").asText()).isEqualTo("caja");
        assertThat(view.path("action").asText()).isEqualTo("READ");
        assertThat(view.path("permissionType").asText()).isEqualTo("APPLICATION");
        assertThat(AccessControlCatalogSync.permissionBody(5, "CAJA.GESTIONAR", "x").path("action").asText()).isEqualTo("UPDATE");
        assertThat(AccessControlCatalogSync.parentOf("/logistica/dispatches")).isEqualTo("/logistica");
        assertThat(AccessControlCatalogSync.parentOf("/ventas")).isNull();
    }

    @Test
    void offByDefaultItNeverCallsAccessControl() {
        FakeAccessControl ac = seeded();
        new AccessControlCatalogSync(false, "ESSENZA", "ESSENZA_ADMIN", 1, ac).onStart();
        assertThat(ac.calls).isEmpty();
    }
}
