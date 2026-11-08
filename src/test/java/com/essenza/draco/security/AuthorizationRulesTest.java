package com.essenza.draco.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.essenza.draco.shared.security.Capabilities;

class AuthorizationRulesTest {

    @Test
    void withoutRulesEveryContextUserHasEveryAction() {
        AuthorizationRules rules = new AuthorizationRules();
        assertThat(rules.granted(Set.of(), Set.of())).containsExactlyElementsOf(Capabilities.keys());
    }

    @Test
    void rulesByPermissionRoleOrNobody() {
        AuthorizationRules rules = new AuthorizationRules();
        rules.setRules(Map.of(
                "VENDEDORES.APROBAR", "role:ADMIN_TIENDA",
                "COMISIONES.AJUSTAR", "perm:FINANZAS.AJUSTES, role:GERENTE",
                "PLANES.EXCEPCIONES", "none",
                "OFERTAS.MODERAR", "rol:ADMIN",
                "VENTAS.GESTIONAR", "*"));
        Set<String> vendedor = rules.granted(Set.of(), Set.of("VENDEDOR"));
        assertThat(vendedor).doesNotContain("VENDEDORES.APROBAR", "COMISIONES.AJUSTAR", "PLANES.EXCEPCIONES", "OFERTAS.MODERAR")
                .contains("VENDEDORES.VER", "VENTAS.GESTIONAR");
        assertThat(rules.granted(Set.of(), Set.of("ADMIN_TIENDA"))).contains("VENDEDORES.APROBAR").doesNotContain("COMISIONES.AJUSTAR");
        assertThat(rules.granted(Set.of("FINANZAS.AJUSTES"), Set.of())).contains("COMISIONES.AJUSTAR");
        assertThat(rules.granted(Set.of(), Set.of("GERENTE"))).contains("COMISIONES.AJUSTAR");
        // Regla mal escrita ("rol:") falla cerrado; "none" no lo da nadie.
        assertThat(rules.granted(Set.of("OFERTAS.MODERAR"), Set.of("ADMIN"))).doesNotContain("OFERTAS.MODERAR", "PLANES.EXCEPCIONES");
    }
}
