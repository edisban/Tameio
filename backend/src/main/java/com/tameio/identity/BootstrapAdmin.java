package com.tameio.identity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The first administrator. Staff are only ever created by an administrator, so someone has to exist
 * first: when the bank has no administrator and both settings are given, one is created at start-up
 * with that password as a temporary one, to be replaced at first sign-in. Without the settings,
 * nothing happens.
 */
@Slf4j
@Component
class BootstrapAdmin implements ApplicationRunner {

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwords;
    private final String email;
    private final String password;

    BootstrapAdmin(UserAccountRepository accounts, PasswordEncoder passwords,
                   @Value("${tameio.bootstrap.admin-email:}") String email,
                   @Value("${tameio.bootstrap.admin-password:}") String password) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password) || accounts.existsByRole(UserRole.ADMIN)) {
            return;
        }
        if (accounts.existsByEmail(UserAccount.normaliseEmail(email))) {
            log.warn("No administrator exists, but {} is taken by another account; none was created", email);
            return;
        }
        UserAccount admin = accounts.save(UserAccount.staff(email, passwords.encode(password), "Administrator", UserRole.ADMIN, null));
        log.info("Created the first administrator {}; the password must be changed at first sign-in", admin.getId());
    }
}
