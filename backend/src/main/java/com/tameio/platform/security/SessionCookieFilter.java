package com.tameio.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

/**
 * Presents the session cookie to Spring Security's JWT support as an Authorization header.
 *
 * <p>It runs after the CSRF check on purpose. Spring Security skips the CSRF check for requests that
 * carry a bearer token, which is right for a header (a browser never adds one by itself) and wrong for
 * a cookie (a browser sends it on any request, including one another site makes it send). Turning the
 * cookie into a header only after the check keeps every cookie-authenticated request protected.
 *
 * <p>Signing up, in or out needs no session, so there the cookie is left out: a session that has ended
 * (an account disabled, a database reset) would otherwise be refused before the password is even checked.
 */
class SessionCookieFilter extends OncePerRequestFilter {

    static final String AUTH_PATHS = "/api/v1/auth/";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(AUTH_PATHS);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            chain.doFilter(request, response);
            return;
        }
        Optional<String> token = sessionToken(request);
        chain.doFilter(token.isPresent() ? new WithAuthorization(request, "Bearer " + token.get()) : request, response);
    }

    private static Optional<String> sessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
            .filter(cookie -> SessionCookies.SESSION.equals(cookie.getName()) && !cookie.getValue().isBlank())
            .map(Cookie::getValue)
            .findFirst();
    }

    private static final class WithAuthorization extends HttpServletRequestWrapper {

        private final String authorization;

        WithAuthorization(HttpServletRequest request, String authorization) {
            super(request);
            this.authorization = authorization;
        }

        @Override
        public String getHeader(String name) {
            return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name) ? authorization : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)
                ? Collections.enumeration(List.of(authorization))
                : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>(Collections.list(super.getHeaderNames()));
            names.add(HttpHeaders.AUTHORIZATION);
            return Collections.enumeration(names);
        }
    }
}
