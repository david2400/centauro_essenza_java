package com.essenza.draco.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class AccessControlMenuAccessTest {

    @Test
    void pathsAreComparedWithoutLocaleHostSlashOrCase() {
        assertThat(AccessControlMenuAccess.normalize("/es/Logistica/shipping-costs/")).isEqualTo("/logistica/shipping-costs");
        assertThat(AccessControlMenuAccess.normalize("http://localhost:3000/en/logistica/shipping-costs?tab=1#x"))
                .isEqualTo("/logistica/shipping-costs");
        assertThat(AccessControlMenuAccess.normalize("logistica//dispatches")).isEqualTo("/logistica/dispatches");
        assertThat(AccessControlMenuAccess.normalize("/estados")).isEqualTo("/estados"); // no es el idioma "es"
        assertThat(AccessControlMenuAccess.normalize("/es")).isEqualTo("/");
    }

    @Test
    void aMenuCoversItsSubroutesButNotSiblings() {
        Set<String> menus = Set.of("/logistica/dispatches", "/");
        assertThat(AccessControlMenuAccess.covers(menus, "/logistica/dispatches")).isTrue();
        assertThat(AccessControlMenuAccess.covers(menus, "/logistica/dispatches/12")).isTrue();
        assertThat(AccessControlMenuAccess.covers(menus, "/logistica/dispatches-old")).isFalse();
        assertThat(AccessControlMenuAccess.covers(menus, "/logistica/shipping-costs")).isFalse(); // "/" no cubre todo
    }
}
