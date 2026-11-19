package com.tameio.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class RolesClaimConverterTest {

    private final RolesClaimConverter converter = new RolesClaimConverter();

    private static Jwt token(Consumer<Map<String, Object>> claims) {
        return Jwt.withTokenValue("token")
            .header("alg", "HS256")
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
    void turnsRolesIntoSpringRoles() {
        assertThat(authorities(token(claims -> claims.put("roles", List.of("business", "ANALYST")))))
            .containsExactly("ROLE_BUSINESS", "ROLE_ANALYST");
    }

    @Test
    void givesNoRolesWhenTheTokenHasNone() {
        assertThat(authorities(token(claims -> {}))).isEmpty();
    }

    @Test
    void ignoresARolesClaimOfTheWrongShape() {
        assertThat(authorities(token(claims -> claims.put("roles", "ADMIN")))).isEmpty();
    }
}
