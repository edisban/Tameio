package com.tameio.platform.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;

/**
 * Issues the signed token that represents a signed-in user. It travels in an HttpOnly cookie and is
 * checked on every request by Spring Security's JWT support (see {@link SecurityConfig}).
 */
@Component
public class SessionTokens {

    static final String ISSUER = "tameio";
    static final String ROLES_CLAIM = "roles";

    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;

    SessionTokens(SecretKey sessionKey, AuthProperties properties, Clock clock) {
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(sessionKey));
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @param sessionVersion the account's session version now; raising it later ends this session
     * @param mustChangePassword the user signed in with a temporary password and must replace it first
     */
    public String issue(String userId, String name, String email, Collection<String> roles, long sessionVersion,
                        boolean mustChangePassword) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(userId)
            .issuedAt(now)
            .expiresAt(now.plus(properties.sessionTtl()))
            .claim("name", name)
            .claim("email", email)
            .claim(ROLES_CLAIM, roles)
            .claim(SessionStatus.VERSION_CLAIM, sessionVersion)
            .claim(SessionStatus.PASSWORD_CHANGE_CLAIM, mustChangePassword)
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
