package com.tameio.identity;

import com.tameio.platform.demo.DemoYear;
import com.tameio.platform.persistence.AdvisoryLocks;
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

    /** One owner per business; each password is the first name and 123, easy to type in a demo. */
    static final List<DemoOwner> OWNERS = List.of(
        new DemoOwner("jack@tameio.local", "jack123", "Jack Harbour", new DemoBusiness("Jack's Café", DemoYear.Profile.CAFE, 11)),
        new DemoOwner("lola@tameio.local", "lola123", "Lola Crumb", new DemoBusiness("Lola's Bakery", DemoYear.Profile.BAKERY, 23)),
        new DemoOwner("max@tameio.local", "max123", "Max Pixel", new DemoBusiness("Pixel Studio", DemoYear.Profile.STUDIO, 37)),
        // An island pub that shuts in winter
        new DemoOwner("jim@tameio.local", "jim123", "Jim Merriman", new DemoBusiness("The Grinning Fox Pub", DemoYear.Profile.TAVERNA, 41)));
    static final List<DemoStaff> STAFF = List.of(
        new DemoStaff("analyst@tameio.local", "analyst123", "Sofia Rossi", UserRole.ANALYST, null),
        new DemoStaff("manager@tameio.local", "manager123", "Daniel Novak", UserRole.MANAGER, new BigDecimal("100000.00")),
        // The administrator sees and manages everything in the back office, and may approve any loan
        new DemoStaff("admin@tameio.local", "admin123", "Olivia Grant", UserRole.ADMIN, new BigDecimal("1000000.00")));

    record DemoBusiness(String name, DemoYear.Profile profile, long seed) {
    }

    record DemoOwner(String email, String password, String name, DemoBusiness business) {
    }

    record DemoStaff(String email, String password, String name, UserRole role, BigDecimal approvalLimit) {
    }

    private final UserAccountRepository accounts;
    private final BusinessRepository businesses;
    private final MembershipRepository memberships;
    private final PasswordEncoder passwords;
    private final ApplicationEventPublisher events;
    private final AdvisoryLocks locks;
    private final TransactionTemplate transaction;
    private final Clock clock;

    DemoData(UserAccountRepository accounts, BusinessRepository businesses, MembershipRepository memberships, PasswordEncoder passwords,
             ApplicationEventPublisher events, AdvisoryLocks locks, PlatformTransactionManager transactions, Clock clock) {
        this.accounts = accounts;
        this.businesses = businesses;
        this.memberships = memberships;
        this.passwords = passwords;
        this.events = events;
        this.locks = locks;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /**
     * Every instance runs this at start-up. The first to take the lock loads the data; the others wait for it,
     * then find the data there. Without the lock, several instances could all see an empty database at once.
     */
    @Override
    public void run(ApplicationArguments args) {
        long started = System.nanoTime();
        boolean loaded = Boolean.TRUE.equals(transaction.execute(status -> {
            locks.acquire(AdvisoryLocks.Key.DEMO_DATA);
            if (accounts.existsByEmail(OWNERS.getFirst().email())) {
                return false;
            }
            seed();
            return true;
        }));
        if (loaded) {
            log.info("Loaded the demo data in {} ms", (System.nanoTime() - started) / 1_000_000);
        } else {
            log.info("Demo data is already there");
        }
    }

    private void seed() {
        LocalDate end = LocalDate.now(clock.withZone(ZoneId.of("Europe/Athens")));
        var bankers = new DemoBusinessReady.Bankers(staff(UserRole.ANALYST), staff(UserRole.MANAGER), staff(UserRole.ADMIN));
        for (DemoOwner demo : OWNERS) {
            UserAccount owner = accounts.save(
                UserAccount.create(demo.email(), passwords.encode(demo.password()), demo.name(), UserRole.BUSINESS));
            create(owner, demo.business(), end, bankers);
        }
    }

    private DemoBusinessReady.Banker staff(UserRole role) {
        DemoStaff demo = STAFF.stream().filter(member -> member.role() == role).findFirst().orElseThrow();
        UserAccount account = accounts.findByEmail(demo.email()).orElseGet(() -> {
            String hash = passwords.encode(demo.password());
            UserAccount created = UserAccount.staff(demo.email(), hash, demo.name(), demo.role(), demo.approvalLimit());
            // A demo account is ready to use: its password is printed in the README, not handed over
            created.changePassword(hash);
            return accounts.save(created);
        });
        return new DemoBusinessReady.Banker(account.getId().toString(), account.getFullName());
    }

    private void create(UserAccount owner, DemoBusiness demo, LocalDate end, DemoBusinessReady.Bankers bankers) {
        String afm = DemoYear.afm("demo business " + demo.name());
        if (businesses.existsByAfm(afm)) {
            return;
        }
        Business business = businesses.save(Business.register(afm, demo.name()));
        memberships.save(Membership.of(business, owner.getId().toString(), MembershipRole.OWNER));
        events.publishEvent(new DemoBusinessReady(business.getId(), demo.name(), demo.profile(), end, demo.seed(), bankers));
        log.info("Demo business {} ({}) ready", demo.name(), demo.profile());
    }
}
