package com.tameio.platform.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * Sign-in is handled by the app itself: the identity module checks the password and issues a signed
 * session token in an HttpOnly cookie. Every request is then authenticated by Spring Security's JWT
 * support, which checks the token's signature, expiry and issuer. No session is kept on the server,
 * so any number of instances can serve any request.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(AuthProperties.class)
class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // The browser sends the session cookie by itself, so state-changing requests must also carry
            // the CSRF token, which only our own page can read from its cookie
            .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokens()))
            // After the CSRF check, so cookie requests are checked; see SessionCookieFilter for why
            .addFilterAfter(new SessionCookieFilter(), CsrfFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // With a temporary password, a user can only see who they are and choose a new password
                .requestMatchers("/api/v1/me", "/api/v1/me/password").authenticated()
                .anyRequest().access(passwordChanged()))
            .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
            .build();
    }

    private static CookieCsrfTokenRepository csrfTokens() {
        // Readable by JavaScript on purpose: the page copies it into the X-XSRF-TOKEN header
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName(SessionCookies.CSRF);
        repository.setHeaderName(SessionCookies.CSRF_HEADER);
        repository.setCookiePath("/");
        return repository;
    }

    /** Signed in, and not still on a temporary password the bank gave them. */
    private static AuthorizationManager<RequestAuthorizationContext> passwordChanged() {
        return (authentication, context) -> {
            var auth = authentication.get();
            boolean signedIn = auth != null && auth.isAuthenticated() && auth instanceof JwtAuthenticationToken;
            boolean mustChange = signedIn && Boolean.TRUE.equals(((JwtAuthenticationToken) auth).getToken().getClaim(SessionStatus.PASSWORD_CHANGE_CLAIM));
            return new AuthorizationDecision(signedIn && !mustChange);
        };
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new RolesClaimConverter());
        return converter;
    }

    @Bean
    SecretKey sessionKey(AuthProperties properties) {
        return new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey sessionKey, ObjectProvider<SessionStatus> sessions) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(sessionKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(), new JwtIssuerValidator(SessionTokens.ISSUER), stillCurrent(sessions)));
        return decoder;
    }

    /**
     * A signature and an expiry are not enough for a bank: a disabled account or a reset password must
     * end its sessions at once. Each request checks the token's session version against the account.
     */
    private static OAuth2TokenValidator<Jwt> stillCurrent(ObjectProvider<SessionStatus> sessions) {
        OAuth2Error ended = new OAuth2Error("invalid_token", "The session has ended.", null);
        return jwt -> {
            SessionStatus status = sessions.getIfAvailable();
            Object version = jwt.getClaim(SessionStatus.VERSION_CLAIM);
            if (status == null || !(version instanceof Number number)) {
                return OAuth2TokenValidatorResult.failure(ended);
            }
            return status.isCurrent(jwt.getSubject(), number.longValue())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(ended);
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordHashing.encoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
