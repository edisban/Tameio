package com.tameio.identity;

import com.tameio.platform.demo.DemoYear;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Demo people and businesses for a local stack, so the app opens with a year of history to look at.
 * Runs once at start-up when tameio.demo.enabled is set and the demo owner does not exist yet; every
 * business it creates is announced with {@link DemoBusinessReady}, and the other modules build its
 * accounts and books in the same transaction. Never enabled in production.
 */
@Slf4j
@Component
@Order(10)
@ConditionalOnProperty(name = "tameio.demo.enabled", havingValue = "true")
class DemoData implements ApplicationRunner {

    static final String OWNER_EMAIL = "demo@tameio.local";
    static final String SECOND_OWNER_EMAIL = "nikos@tameio.local";
    static final String BUSINESS_PASSWORD = "demo-password-2026";
    static final String STAFF_PASSWORD = "demo-staff-password-2026";

    private record DemoBusiness(String name, DemoYear.Profile profile, long seed) {
    }

    private final UserAccountRepository accounts;
    private final BusinessRepository businesses;
    private final MembershipRepository memberships;
    private final PasswordEncoder passwords;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final Clock clock;

    DemoData(UserAccountRepository accounts, BusinessRepository businesses, MembershipRepository memberships, PasswordEncoder passwords,
             ApplicationEventPublisher events, PlatformTransactionManager transactions, Clock clock) {
        this.accounts = accounts;
        this.businesses = businesses;
        this.memberships = memberships;
        this.passwords = passwords;
        this.events = events;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (accounts.existsByEmail(OWNER_EMAIL)) {
            log.info("Demo data is already there");
            return;
        }
        long started = System.nanoTime();
        transaction.executeWithoutResult(status -> seed());
        log.info("Loaded the demo data in {} ms", (System.nanoTime() - started) / 1_000_000);
    }

    private void seed() {
        LocalDate end = LocalDate.now(clock.withZone(ZoneId.of("Europe/Athens")));
        String businessHash = passwords.encode(BUSINESS_PASSWORD);
        String staffHash = passwords.encode(STAFF_PASSWORD);

        UserAccount maria = accounts.save(UserAccount.create(OWNER_EMAIL, businessHash, "Μαρία Παπαδοπούλου", UserRole.BUSINESS));
        UserAccount nikos = accounts.save(UserAccount.create(SECOND_OWNER_EMAIL, businessHash, "Νίκος Γεωργίου", UserRole.BUSINESS));
        staff("analyst@tameio.local", "Ελένη Μαρκοπούλου", UserRole.ANALYST, null, staffHash);
        staff("manager@tameio.local", "Κώστας Δημητρίου", UserRole.MANAGER, new BigDecimal("100000.00"), staffHash);

        create(maria, List.of(
            new DemoBusiness("Καφέ Το Στέκι", DemoYear.Profile.CAFE, 11),
            new DemoBusiness("Αρτοποιία Αιγαίο", DemoYear.Profile.BAKERY, 23),
            new DemoBusiness("Studio Λευκό", DemoYear.Profile.STUDIO, 37)), end);
        create(nikos, List.of(new DemoBusiness("Ταβέρνα Ο Γιάννης", DemoYear.Profile.TAVERNA, 41)), end);
    }

    private void staff(String email, String name, UserRole role, BigDecimal limit, String hash) {
        if (accounts.existsByEmail(email)) {
            return;
        }
        UserAccount account = UserAccount.staff(email, hash, name, role, limit);
        // A demo account is ready to use: its password is printed in the README, not handed over
        account.changePassword(hash);
        accounts.save(account);
    }

    private void create(UserAccount owner, List<DemoBusiness> list, LocalDate end) {
        for (DemoBusiness demo : list) {
            String afm = DemoYear.afm("demo business " + demo.name());
            if (businesses.existsByAfm(afm)) {
                continue;
            }
            Business business = businesses.save(Business.register(afm, demo.name()));
            memberships.save(Membership.of(business, owner.getId().toString(), MembershipRole.OWNER));
            events.publishEvent(new DemoBusinessReady(business.getId(), demo.name(), demo.profile(), end, demo.seed()));
            log.info("Demo business {} ({}) ready", demo.name(), demo.profile());
        }
    }
}
