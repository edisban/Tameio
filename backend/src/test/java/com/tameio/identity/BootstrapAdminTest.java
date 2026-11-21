package com.tameio.identity;

import com.tameio.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** A new bank's first start creates its first administrator, once. */
@SpringBootTest(properties = {
    "tameio.bootstrap.admin-email=First.Admin@Bank.example",
    "tameio.bootstrap.admin-password=bootstrap-password",
})
@Import(TestcontainersConfiguration.class)
class BootstrapAdminTest {

    private final UserAccountRepository accounts;
    private final BootstrapAdmin bootstrap;

    BootstrapAdminTest(UserAccountRepository accounts, BootstrapAdmin bootstrap) {
        this.accounts = accounts;
        this.bootstrap = bootstrap;
    }

    @Test
    void createsTheFirstAdministratorWithAPasswordToChange() {
        UserAccount admin = accounts.findByEmail("first.admin@bank.example").orElseThrow();

        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.isMustChangePassword()).isTrue();
        assertThat(admin.getPasswordHash()).startsWith("{argon2}");
    }

    @Test
    void doesNothingOnceAnAdministratorExists() {
        long before = accounts.count();
        ApplicationArguments none = new DefaultApplicationArguments();

        bootstrap.run(none);

        assertThat(accounts.count()).isEqualTo(before);
    }
}
