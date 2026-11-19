package com.tameio.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHashingTest {

    private final PasswordEncoder encoder = PasswordHashing.encoder();

    @Test
    void hashesWithArgon2idAndOwaspParameters() {
        String hash = encoder.encode("correct horse battery");

        assertThat(hash).startsWith("{argon2}$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(hash.length()).isLessThanOrEqualTo(255);
        assertThat(encoder.matches("correct horse battery", hash)).isTrue();
        assertThat(encoder.matches("Correct horse battery", hash)).isFalse();
    }

    @Test
    void saltsEveryHashDifferently() {
        assertThat(encoder.encode("same password")).isNotEqualTo(encoder.encode("same password"));
    }

    @Test
    void stillAcceptsAnOlderBcryptHashAndAsksForItToBeUpgraded() {
        String bcrypt = "{bcrypt}" + new BCryptPasswordEncoder().encode("correct horse battery");

        assertThat(encoder.matches("correct horse battery", bcrypt)).isTrue();
        assertThat(encoder.upgradeEncoding(bcrypt)).isTrue();
        assertThat(encoder.upgradeEncoding(encoder.encode("correct horse battery"))).isFalse();
    }
}
