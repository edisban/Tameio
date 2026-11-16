package com.tameio.platform.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The signed-in user as this service sees them. The id is Keycloak's stable subject, which is what the
 * database stores; name and email are for display only and may change in Keycloak at any time.
 */
public record CurrentUser(String id, String name, String email) {

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(jwt.getSubject(), jwt.getClaimAsString("name"), jwt.getClaimAsString("email"));
    }
}
