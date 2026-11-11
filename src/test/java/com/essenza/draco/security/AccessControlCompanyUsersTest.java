package com.essenza.draco.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.essenza.draco.shared.security.CompanyUsers.CompanyUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class AccessControlCompanyUsersTest {

    private static final Instant NOW = Instant.parse("2026-11-10T15:00:00Z");

    @Test
    void keepsOnlyActiveCurrentAssignmentsOfTheCompanyAndNeverTheSecrets() throws Exception {
        JsonNode body = new ObjectMapper().readTree("""
                [
                 {"userId":7,"companyId":3,"isActive":true,"user":{"idUser":7,"username":"djaramillo","password":"$2a$hash",
                   "refreshToken":"rt","status":"ACTIVO","client":{"firstName":"David","firstLastName":"Jaramillo","secondLastName":"Lopez"}}},
                 {"userId":8,"companyId":3,"isActive":true,"user":{"idUser":8,"username":"ana","status":"ACTIVE"}},
                 {"userId":9,"companyId":3,"isActive":false,"user":{"idUser":9,"username":"off"}},
                 {"userId":10,"companyId":3,"isActive":true,"expiresAt":"2026-01-01T00:00:00Z","user":{"idUser":10,"username":"old"}},
                 {"userId":11,"companyId":4,"isActive":true,"user":{"idUser":11,"username":"other-company"}},
                 {"userId":12,"companyId":3,"isActive":true,"user":{"idUser":12,"username":"blocked","status":"INACTIVO"}},
                 {"userId":13,"companyId":3,"deleted":true,"user":{"idUser":13,"username":"gone"}},
                 {"userId":8,"companyId":3,"isActive":true,"user":{"idUser":8,"username":"ana"}}
                ]""");

        List<CompanyUser> users = AccessControlCompanyUsers.parse(body, 3, NOW);

        assertThat(users).containsExactly(new CompanyUser("8", "ana", "ana"),
                new CompanyUser("7", "djaramillo", "David Jaramillo Lopez"));
        assertThat(users.toString()).doesNotContain("hash").doesNotContain("rt");
    }

    @Test
    void theTokenSubjectMatchesByIdOrUsername() {
        CompanyUser user = new CompanyUser("7", "djaramillo", "David");
        assertThat(user.matches("7")).isTrue();
        assertThat(user.matches("DJaramillo")).isTrue();
        assertThat(user.matches("8")).isFalse();
        assertThat(user.matches(null)).isFalse();
    }
}
