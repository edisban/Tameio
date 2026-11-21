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

import java.math.BigDecimal;
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

    /** A disabled account cannot sign in, and its sessions end at once. */
    @Column(nullable = false)
    private boolean active = true;

    /** Raised to end every session issued before; tokens carry the version they were issued with. */
    @Column(name = "session_version", nullable = false)
    private long sessionVersion;

    /** Set while the user holds a temporary password the bank gave them. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    /** The largest loan a member of bank staff may approve; null for no approval authority. */
    @Column(name = "approval_limit", precision = 14, scale = 2)
    private BigDecimal approvalLimit;

    static UserAccount create(String email, String passwordHash, String fullName, UserRole role) {
        var account = new UserAccount();
        account.email = normaliseEmail(email);
        account.passwordHash = passwordHash;
        account.fullName = fullName;
        account.role = role;
        return account;
    }

    /** A member of bank staff, with a temporary password to replace at first sign-in. */
    static UserAccount staff(String email, String temporaryPasswordHash, String fullName, UserRole role, BigDecimal approvalLimit) {
        if (role == UserRole.BUSINESS) {
            throw new IllegalArgumentException("Staff accounts have a bank role");
        }
        UserAccount account = create(email, temporaryPasswordHash, fullName, role);
        account.mustChangePassword = true;
        account.approvalLimit = approvalLimit;
        return account;
    }

    boolean isStaff() {
        return role != UserRole.BUSINESS;
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

    /** The user chose a new password: it replaces any temporary one, and every other session ends. */
    void changePassword(String newHash) {
        passwordHash = newHash;
        mustChangePassword = false;
        endSessions();
    }

    /** The bank set a temporary password: the user must replace it, and every session ends. */
    void resetPassword(String temporaryHash) {
        passwordHash = temporaryHash;
        mustChangePassword = true;
        failedSignIns = 0;
        lockedUntil = null;
        endSessions();
    }

    void deactivate() {
        active = false;
        endSessions();
    }

    void activate() {
        active = true;
    }

    void changeRole(UserRole newRole, BigDecimal newApprovalLimit) {
        if (newRole == UserRole.BUSINESS || role == UserRole.BUSINESS) {
            throw new IllegalArgumentException("Business and staff accounts stay what they are");
        }
        if (newRole != role) {
            role = newRole;
            // The role is in the token: sessions with the old one must not go on
            endSessions();
        }
        approvalLimit = newApprovalLimit;
    }

    void endSessions() {
        sessionVersion++;
    }
}
