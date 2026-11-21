package com.tameio.banking;

import com.jayway.jsonpath.JsonPath;
import com.tameio.TestcontainersConfiguration;
import com.tameio.platform.security.CurrentUser;
import com.tameio.platform.validation.Iban;
import com.tameio.platform.web.UnprocessableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.tameio.TestUsers.freshAfm;
import static com.tameio.TestUsers.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Accounts and the ledger against a real PostgreSQL: balances, statements, concurrency, and the
 * database's own refusal to store unbalanced or altered entries.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BankingApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    BankingService banking;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    Ledger ledger;

    /** A business owner whose id the test knows, so it can also call the service directly. */
    private record Owner(CurrentUser user, RequestPostProcessor auth, UUID businessId) {
    }

    private Owner alpha;
    private Owner beta;

    @BeforeEach
    void twoBusinesses() throws Exception {
        alpha = owner("Αλφα Καφέ");
        beta = owner("Βήτα Αρτοποιία");
    }

    private Owner owner(String name) throws Exception {
        String id = UUID.randomUUID().toString();
        RequestPostProcessor auth = jwt().jwt(t -> t.subject(id).claim("name", "Owner").claim("email", "o@example.com"))
            .authorities(new SimpleGrantedAuthority("ROLE_BUSINESS"));
        MvcTestResult created = mvc.post().uri("/api/v1/businesses").with(auth).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"afm": "%s", "name": "%s"}""".formatted(freshAfm(), name))
            .exchange();
        return new Owner(new CurrentUser(id, "Owner", "o@example.com"), auth,
            UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id")));
    }

    private Account open(Owner owner) throws Exception {
        MvcTestResult opened = mvc.post().uri("/api/v1/businesses/{b}/accounts", owner.businessId()).with(owner.auth()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{}").exchange();
        assertThat(opened).hasStatus(HttpStatus.CREATED);
        String body = opened.getResponse().getContentAsString();
        return new Account(owner, UUID.fromString(JsonPath.read(body, "$.id")), JsonPath.read(body, "$.iban"));
    }

    private record Account(Owner owner, UUID id, String iban) {
    }

    private void deposit(Account account, String amount) {
        MvcTestResult deposited = mvc.post().uri("/api/v1/businesses/{b}/accounts/{a}/demo-deposits", account.owner().businessId(), account.id())
            .with(account.owner().auth()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"amount": %s}""".formatted(amount))
            .exchange();
        assertThat(deposited).hasStatus(HttpStatus.CREATED);
    }

    /** Moves money between two customer accounts through the ledger. */
    private void move(Account from, Account to, String amount) {
        BigDecimal value = new BigDecimal(amount);
        transaction.executeWithoutResult(status -> ledger.book(EntryKind.OUTGOING_PAYMENT, "test", null, "test", null, List.of(
            new Ledger.Leg(from.id(), value.negate(), "to", to.iban()),
            new Ledger.Leg(to.id(), value, "from", from.iban()))));
    }

    private BigDecimal balance(Account account) {
        return jdbc.queryForObject("select balance from bank_account where id = ?", BigDecimal.class, account.id());
    }

    private BigDecimal ledgerTotal() {
        return jdbc.queryForObject("select sum(balance) from bank_account", BigDecimal.class);
    }

    @Test
    void opensAnAccountWithAValidGreekIbanOfTameio() throws Exception {
        Account account = open(alpha);

        // Recomputing the check digits from the rest gives the same IBAN
        assertThat(Iban.of("GR", account.iban().substring(4))).isEqualTo(account.iban());
        assertThat(account.iban()).hasSize(27).startsWith("GR").satisfies(iban -> assertThat(iban.substring(4, 11)).isEqualTo("9990001"));
        assertThat(balance(account)).isEqualByComparingTo("0");
        MvcTestResult list = mvc.get().uri("/api/v1/businesses/{b}/accounts", alpha.businessId()).with(alpha.auth()).exchange();
        assertThat(list).bodyJson().extractingPath("$[0].holderName").isEqualTo("Αλφα Καφέ");
        assertThat(list).bodyJson().extractingPath("$[0].name").isEqualTo("Λογαριασμός όψεως");
    }

    @Test
    void aDepositShowsInTheStatementWithTheBalanceAfterIt() throws Exception {
        Account account = open(alpha);
        deposit(account, "500.00");
        deposit(account, "23.45");

        MvcTestResult statement = mvc.get().uri("/api/v1/businesses/{b}/accounts/{a}/transactions", alpha.businessId(), account.id())
            .with(alpha.auth()).exchange();

        assertThat(statement).bodyJson().extractingPath("$.content[0].amount").isEqualTo(23.45);
        assertThat(statement).bodyJson().extractingPath("$.content[0].balanceAfter").isEqualTo(523.45);
        assertThat(statement).bodyJson().extractingPath("$.content[0].kind").isEqualTo("INCOMING_PAYMENT");
        assertThat(statement).bodyJson().extractingPath("$.content[1].balanceAfter").isEqualTo(500.00);
        assertThat(balance(account)).isEqualByComparingTo("523.45");
        assertThat(ledgerTotal()).isEqualByComparingTo("0");
    }

    @Test
    void theLedgerRefusesToOverdrawACustomerAccount() throws Exception {
        Account from = open(alpha);
        Account to = open(beta);
        deposit(from, "10.00");

        assertThatThrownBy(() -> move(from, to, "10.01")).isInstanceOf(UnprocessableException.class);

        assertThat(balance(from)).isEqualByComparingTo("10.00");
        assertThat(balance(to)).isEqualByComparingTo("0.00");
    }

    @Test
    void anotherBusinessCanNeitherSeeNorUseTheAccount() throws Exception {
        Account account = open(alpha);
        deposit(account, "50.00");

        assertThat(mvc.get().uri("/api/v1/businesses/{b}/accounts/{a}/transactions", alpha.businessId(), account.id())
            .with(beta.auth()).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.post().uri("/api/v1/businesses/{b}/accounts/{a}/demo-deposits", beta.businessId(), account.id())
            .with(beta.auth()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"amount": 1}""").exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(balance(account)).isEqualByComparingTo("50.00");
    }

    @Test
    void aFrozenAccountTakesNoMoneyAndOnlyManagersFreeze() throws Exception {
        Account account = open(alpha);
        String freeze = """
            {"reason": "Έλεγχος AML"}""";

        assertThat(mvc.post().uri("/api/v1/bank/accounts/{a}/freeze", account.id()).with(user("ANALYST")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(freeze).exchange()).hasStatus(HttpStatus.FORBIDDEN);
        MvcTestResult frozen = mvc.post().uri("/api/v1/bank/accounts/{a}/freeze", account.id()).with(user("MANAGER")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(freeze).exchange();
        assertThat(frozen).bodyJson().extractingPath("$.status").isEqualTo("FROZEN");

        assertThat(mvc.post().uri("/api/v1/businesses/{b}/accounts/{a}/demo-deposits", alpha.businessId(), account.id())
            .with(alpha.auth()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"amount": 5}""").exchange()).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);

        mvc.post().uri("/api/v1/bank/accounts/{a}/unfreeze", account.id()).with(user("MANAGER")).with(csrf()).exchange();
        deposit(account, "5.00");
        assertThat(balance(account)).isEqualByComparingTo("5.00");
    }

    @Test
    void concurrentEntriesOnTheSameAccountsNeverOverdrawLoseAnUpdateOrDeadlock() throws Exception {
        Account a = open(alpha);
        Account b = open(beta);
        deposit(a, "100.00");
        deposit(b, "100.00");

        List<Callable<Boolean>> work = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            // A pays B fifteen times (only ten can succeed at first) while B pays A ten times
            work.add(() -> attempt(() -> move(a, b, "10.00")));
            if (i < 10) {
                work.add(() -> attempt(() -> move(b, a, "10.00")));
            }
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        long succeeded = 0;
        for (Future<Boolean> result : pool.invokeAll(work)) {
            succeeded += result.get() ? 1 : 0;
        }
        pool.shutdown();

        // Money is conserved whatever the order, and neither account ever went below zero
        assertThat(balance(a).add(balance(b))).isEqualByComparingTo("200.00");
        assertThat(succeeded).isBetween(20L, 25L);
        assertThat(ledgerTotal()).isEqualByComparingTo("0");
        // No lost update: each posting's balance is the one before it plus its amount
        for (Account account : List.of(a, b)) {
            List<BigDecimal[]> chain = jdbc.query("select amount, balance_after from posting where account_id = ? order by booked_at, id",
                (rs, n) -> new BigDecimal[] {rs.getBigDecimal(1), rs.getBigDecimal(2)}, account.id());
            BigDecimal running = BigDecimal.ZERO;
            for (BigDecimal[] posting : chain) {
                running = running.add(posting[0]);
                assertThat(posting[1]).isEqualByComparingTo(running).isNotNegative();
            }
            assertThat(running).isEqualByComparingTo(balance(account));
        }
    }

    private static boolean attempt(Runnable movement) {
        try {
            movement.run();
            return true;
        } catch (UnprocessableException e) {
            return false;
        }
    }

    @Test
    void theDatabaseRefusesAnUnbalancedEntryAndAnyChangeToTheLedger() throws Exception {
        Account account = open(alpha);
        deposit(account, "30.00");

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            UUID entry = UUID.randomUUID();
            jdbc.update("insert into journal_entry (id, kind, description, initiated_by, booked_at) values (?, 'OUTGOING_PAYMENT', 'forged', 'x', now())", entry);
            jdbc.update("insert into posting (id, journal_entry_id, position, account_id, amount, balance_after, booked_at) values (?, ?, 0, ?, 1000, 1030, now())",
                UUID.randomUUID(), entry, account.id());
        })).hasMessageContaining("does not balance");

        assertThatThrownBy(() -> jdbc.update("update posting set amount = 1000000 where account_id = ?", account.id()))
            .rootCause().hasMessageContaining("posting is append-only");
        assertThatThrownBy(() -> jdbc.update("delete from journal_entry")).rootCause().hasMessageContaining("journal_entry is append-only");
    }

    @Test
    void severalEntriesInOneTransactionEachSeeTheBalanceTheOneBeforeLeft() throws Exception {
        Account account = open(alpha);
        var settlement = ledger.internal(InternalAccount.SETTLEMENT);

        transaction.executeWithoutResult(status -> {
            for (int i = 0; i < 3; i++) {
                ledger.book(EntryKind.INCOMING_PAYMENT, "in", null, "test", null, List.of(
                    new Ledger.Leg(settlement, new BigDecimal("-10.00"), null, null),
                    new Ledger.Leg(account.id(), new BigDecimal("10.00"), null, null)));
            }
            // Only possible if the three credits above all counted
            ledger.book(EntryKind.OUTGOING_PAYMENT, "out", null, "test", null, List.of(
                new Ledger.Leg(account.id(), new BigDecimal("-30.00"), null, null),
                new Ledger.Leg(settlement, new BigDecimal("30.00"), null, null)));
        });

        assertThat(balance(account)).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForList("select balance_after from posting where account_id = ? order by booked_at, id", BigDecimal.class, account.id()))
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactly(new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("30"), new BigDecimal("0"));
    }

    @Test
    void theTrialBalanceAlwaysSumsToZero() throws Exception {
        Account account = open(alpha);
        deposit(account, "75.50");

        MvcTestResult trial = mvc.get().uri("/api/v1/bank/ledger/trial-balance").with(user("MANAGER")).exchange();

        assertThat(trial).hasStatusOk();
        assertThat(trial).bodyJson().extractingPath("$.balanced").isEqualTo(true);
        assertThat(trial).bodyJson().extractingPath("$.total").isEqualTo(0.00);
        assertThat(trial).bodyJson().extractingPath("$.internal[*].code").asArray().contains("SETTLEMENT", "LOANS", "FEE_INCOME");
        assertThat(mvc.get().uri("/api/v1/bank/ledger/trial-balance").with(user("ANALYST")).exchange()).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/v1/bank/accounts?search=Αλφα").with(user("ANALYST")).exchange()).hasStatusOk();
    }
}
