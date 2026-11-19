package com.tameio.platform.security;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

/**
 * How passwords are hashed: Argon2id, the algorithm OWASP recommends first. It needs a lot of memory
 * per guess, which makes cracking stolen hashes on graphics cards slow and expensive.
 *
 * <p>Each stored hash starts with its algorithm, e.g. {@code {argon2}$argon2id$v=19$m=19456,t=2,p=1$...}.
 * Older BCrypt hashes still verify, and {@link PasswordEncoder#upgradeEncoding} reports them, so they
 * are replaced with Argon2id the next time their owner signs in.
 */
final class PasswordHashing {

    static final String CURRENT = "argon2";

    // OWASP's minimum for Argon2id: 19 MiB of memory, 2 iterations, 1 degree of parallelism;
    // a 16-byte random salt and a 32-byte hash
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 19 * 1024;
    private static final int ITERATIONS = 2;

    private PasswordHashing() {
    }

    static PasswordEncoder encoder() {
        PasswordEncoder argon2id = new Argon2PasswordEncoder(SALT_BYTES, HASH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS);
        return new DelegatingPasswordEncoder(CURRENT, Map.of(
            CURRENT, argon2id,
            // Verifies hashes made before the switch to Argon2id
            "bcrypt", new BCryptPasswordEncoder()));
    }
}
