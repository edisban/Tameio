package com.tameio.identity;

import com.tameio.platform.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/** Someone who can sign in. The password is kept only as a one-way hash. */
@Entity
@Table(name = "user_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class UserAccount extends AuditedEntity {

    static final int MAX_FAILED_SIGN_INS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(name = "failed_sign_ins", nullable = false)
    private int failedSignIns;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    static UserAccount create(String email, String passwordHash, String fullName, UserRole role) {
        var account = new UserAccount();
        account.email = normaliseEmail(email);
        account.passwordHash = passwordHash;
        account.fullName = fullName;
        account.role = role;
        return account;
    }

    static String normaliseEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** After too many wrong passwords in a row the account is locked for a while, which stops guessing. */
    void recordFailedSignIn(Instant now) {
        failedSignIns++;
        if (failedSignIns >= MAX_FAILED_SIGN_INS) {
            lockedUntil = now.plus(LOCK_DURATION);
            failedSignIns = 0;
        }
    }

    /** Replaces the hash with one made by the current algorithm; the password itself is unchanged. */
    void rehashPassword(String newHash) {
        passwordHash = newHash;
    }

    void recordSuccessfulSignIn() {
        failedSignIns = 0;
        lockedUntil = null;
    }
}
