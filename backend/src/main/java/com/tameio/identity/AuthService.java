package com.tameio.identity;

import com.tameio.platform.security.SessionTokens;
import com.tameio.platform.web.ConflictException;
import com.tameio.platform.web.TooManyRequestsException;
import com.tameio.platform.web.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Signing up and signing in. Returns a signed session token; the controller puts it in a cookie. */
@Slf4j
@Service
class AuthService {

    // The same answer whether the email or the password is wrong, so it cannot be used to find accounts
    private static final String WRONG_CREDENTIALS = "Wrong email or password.";

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwords;
    private final SessionTokens tokens;
    private final Clock clock;
    // The hash of a random password nobody knows, checked when the email is unknown so the answer
    // takes as long as a real check and response times do not reveal which emails have accounts
    private final String dummyHash;

    AuthService(UserAccountRepository accounts, PasswordEncoder passwords, SessionTokens tokens, Clock clock) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.tokens = tokens;
        this.clock = clock;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    record SignedIn(String token, MeResponse me) {
    }

    /** Anyone can sign up as a business; bank staff accounts are created by the bank. */
    @Transactional
    public SignedIn signUp(AuthRequests.SignUp request) {
        String email = UserAccount.normaliseEmail(request.email());
        if (accounts.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }
        UserAccount account = accounts.save(
            UserAccount.create(email, passwords.encode(request.password()), request.fullName(), UserRole.BUSINESS));
        log.info("User {} signed up", account.getId());
        return signedIn(account);
    }

    // noRollbackFor: a wrong password must still save the failed attempt that counts towards the lock
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public SignedIn signIn(AuthRequests.SignIn request) {
        Instant now = clock.instant();
        Optional<UserAccount> found = accounts.findByEmail(UserAccount.normaliseEmail(request.email()));
        if (found.isEmpty()) {
            passwords.matches(request.password(), dummyHash);
            throw new UnauthorizedException(WRONG_CREDENTIALS);
        }
        UserAccount account = found.get();
        if (account.isLocked(now)) {
            throw new TooManyRequestsException("Too many failed sign-ins. Try again in 15 minutes.");
        }
        if (!passwords.matches(request.password(), account.getPasswordHash())) {
            account.recordFailedSignIn(now);
            log.info("Failed sign-in for user {}", account.getId());
            throw new UnauthorizedException(WRONG_CREDENTIALS);
        }
        account.recordSuccessfulSignIn();
        // The only moment the plain password is at hand: move an older hash to the current algorithm
        if (passwords.upgradeEncoding(account.getPasswordHash())) {
            account.rehashPassword(passwords.encode(request.password()));
            log.info("Upgraded the password hash of user {}", account.getId());
        }
        return signedIn(account);
    }

    private SignedIn signedIn(UserAccount account) {
        String id = account.getId().toString();
        List<String> roles = List.of(account.getRole().name());
        String token = tokens.issue(id, account.getFullName(), account.getEmail(), roles);
        return new SignedIn(token, new MeResponse(id, account.getFullName(), account.getEmail(), roles));
    }
}
