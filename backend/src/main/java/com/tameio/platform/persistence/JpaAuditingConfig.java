package com.tameio.platform.persistence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "currentAuditor")
class JpaAuditingConfig {

    /** The signed-in user's Keycloak id, or "system" for work no user started (jobs, event handlers). */
    @Bean
    AuditorAware<String> currentAuditor() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
            .map(auth -> auth.getPrincipal() instanceof Jwt jwt ? jwt.getSubject() : null)
            .or(() -> Optional.of("system"));
    }
}
