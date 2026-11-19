package com.tameio.platform.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The signed-in user, read from the session token. The id is the user account's id, which is what other
 * tables store; name and email are as they were at sign-in and are for display only.
 */
public record CurrentUser(String id, String name, String email) {

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(jwt.getSubject(), jwt.getClaimAsString("name"), jwt.getClaimAsString("email"));
    }
}
