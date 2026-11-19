package com.tameio.platform.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * How sessions are signed and stored. Validated at start-up, so a missing or short secret stops the
 * app instead of producing tokens anyone could forge.
 *
 * @param jwtSecret    HMAC key for signing session tokens; at least 32 bytes (256 bits) for HS256
 * @param sessionTtl   how long a sign-in lasts
 * @param secureCookie send cookies over HTTPS only; off for local development on http://localhost
 */
@Validated
@ConfigurationProperties("tameio.auth")
public record AuthProperties(
    @NotBlank @Size(min = 32) String jwtSecret,
    @NotNull Duration sessionTtl,
    boolean secureCookie) {
}
