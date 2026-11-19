package com.tameio.platform.security;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The session cookie. HttpOnly, so JavaScript (and therefore any injected script) cannot read the
 * token; SameSite=Lax, so other sites cannot send it with their own form posts.
 */
@Component
public class SessionCookies {

    // App-specific names: on localhost every port shares cookies, so a generic name set by another
    // local app would be sent here too
    public static final String SESSION = "tameio_session";
    public static final String CSRF = "tameio_xsrf";
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private final AuthProperties properties;

    SessionCookies(AuthProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie issue(String token) {
        return base(token).maxAge(properties.sessionTtl()).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(SESSION, value)
            .httpOnly(true)
            .secure(properties.secureCookie())
            .sameSite("Lax")
            .path("/");
    }
}
