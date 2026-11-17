package com.tameio.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRolesConverterTest {

    private final KeycloakRolesConverter converter = new KeycloakRolesConverter();

    private static Jwt token(Consumer<Map<String, Object>> claims) {
        return Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user-1")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claims(claims)
            .build();
    }

    private List<String> authorities(Jwt jwt) {
        return converter.convert(jwt).stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void turnsRealmRolesIntoSpringRoles() {
        Jwt jwt = token(claims -> claims.put("realm_access", Map.of("roles", List.of("business", "offline_access"))));

        assertThat(authorities(jwt)).containsExactly("ROLE_BUSINESS", "ROLE_OFFLINE_ACCESS");
    }

    @Test
    void givesNoRolesWhenTheTokenHasNone() {
        assertThat(authorities(token(claims -> {}))).isEmpty();
        assertThat(authorities(token(claims -> claims.put("realm_access", Map.of())))).isEmpty();
    }

    @Test
    void ignoresARolesClaimOfTheWrongShape() {
        Jwt jwt = token(claims -> claims.put("realm_access", Map.of("roles", "admin")));

        assertThat(authorities(jwt)).isEmpty();
    }

    @Test
    void doesNotReadClientRolesAsRealmRoles() {
        Jwt jwt = token(claims -> claims.put("resource_access", Map.of("account", Map.of("roles", List.of("manage-account")))));

        assertThat(authorities(jwt)).isEmpty();
    }
}
