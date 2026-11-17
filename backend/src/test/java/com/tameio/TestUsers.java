package com.tameio;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Signed-in users and test data for API tests. Tokens are built in the test (no Keycloak needed) with
 * the claims Keycloak would send. Each call returns a new user, so tests never share data.
 */
public final class TestUsers {

    private TestUsers() {
    }

    public static RequestPostProcessor user(String... roles) {
        String id = UUID.randomUUID().toString();
        return jwt()
            .jwt(token -> token.subject(id).claim("name", "Maria Test").claim("email", "maria@example.com"))
            .authorities(Arrays.stream(roles).map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role)).toList());
    }

    public static RequestPostProcessor businessUser() {
        return user("BUSINESS");
    }

    /** A valid ΑΦΜ no other test uses: eight random digits and the matching check digit. */
    public static String freshAfm() {
        String first = String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            sum += (first.charAt(i) - '0') << (8 - i);
        }
        return first + sum % 11 % 10;
    }
}
