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
 * Accounts and the ledger against a real PostgreSQL: balances, statements, idempotency, concurrency,
 * and the database's own refusal to store unbalanced or altered entries.
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

    private MvcTestResult transfer(Account from, String toIban, String amount, String key) {
        var request = mvc.post().uri("/api/v1/businesses/{b}/accounts/{a}/transfers", from.owner().businessId(), from.id())
            .with(from.owner().auth()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"creditorIban": "%s", "creditorName": "Βήτα Αρτοποιία", "amount": %s, "reference": "Τιμολόγιο 42"}""".formatted(toIban, amount));
        return key == null ? request.exchange() : request.header("Idempotency-Key", key).exchange();
    }

    private BigDecimal balance(Account account) {
        return jdbc.queryForObject("select balance from bank_account where id = ?", BigDecimal.class, account.id());
    }

    private BigDecimal ledgerTotal() {
        return jdbc.queryForObject("select sum(balance) from bank_account", BigDecimal.class);
    }

    @Test
    void opensAnAccountWithAValidGreekIbanOfKymaBank() throws Exception {
        Account account = open(alpha);

        assertThat(Iban.isValid(account.iban())).isTrue();
        assertThat(account.iban()).hasSize(27).startsWith("GR").satisfies(iban -> assertThat(iban.substring(4, 11)).isEqualTo("9990001"));
        assertThat(balance(account)).isEqualByComparingTo("0");
        MvcTestResult list = mvc.get().uri("/api/v1/businesses/{b}/accounts", alpha.businessId()).with(alpha.auth()).exchange();
        assertThat(list).bodyJson().extractingPath("$[0].holderName").isEqualTo("Αλφα Καφέ");
        assertThat(list).bodyJson().extractingPath("$[0].name").isEqualTo("Λογαριασμός όψεως");
    }

    @Test
    void movesMoneyBetweenBusinessesAndBothStatementsShowIt() throws Exception {
        Account from = open(alpha);
        Account to = open(beta);
        deposit(from, "500.00");

        MvcTestResult paid = transfer(from, Iban.format(to.iban()), "123.45", UUID.randomUUID().toString());

        assertThat(paid).hasStatus(HttpStatus.CREATED);
        assertThat(paid).bodyJson().extractingPath("$.status").isEqualTo("COMPLETED");
        assertThat(paid).bodyJson().extractingPath("$.balanceAfter").isEqualTo(376.55);
        assertThat(balance(from)).isEqualByComparingTo("376.55");
        assertThat(balance(to)).isEqualByComparingTo("123.45");

        MvcTestResult received = mvc.get().uri("/api/v1/businesses/{b}/accounts/{a}/transactions", beta.businessId(), to.id())
            .with(beta.auth()).exchange();
        assertThat(received).bodyJson().extractingPath("$.content[0].amount").isEqualTo(123.45);
        assertThat(received).bodyJson().extractingPath("$.content[0].counterpartyName").isEqualTo("Αλφα Καφέ");
        assertThat(received).bodyJson().extractingPath("$.content[0].reference").isEqualTo("Τιμολόγιο 42");
        MvcTestResult sent = mvc.get().uri("/api/v1/businesses/{b}/accounts/{a}/transactions", alpha.businessId(), from.id())
            .with(alpha.auth()).exchange();
        assertThat(sent).bodyJson().extractingPath("$.content[0].amount").isEqualTo(-123.45);
        assertThat(sent).bodyJson().extractingPath("$.content[1].kind").isEqualTo("INCOMING_PAYMENT");
        assertThat(ledgerTotal()).isEqualByComparingTo("0");
    }

    @Test
    void aRetryWithTheSameKeyPaysOnceAndADifferentRequestWithItIsRefused() throws Exception {
        Account from = open(alpha);
        Account to = open(beta);
        deposit(from, "100.00");
        String key = UUID.randomUUID().toString();

        MvcTestResult first = transfer(from, to.iban(), "40.00", key);
        MvcTestResult retry = transfer(from, to.iban(), "40.00", key);

        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(retry).hasStatusOk();
        assertThat(retry).hasHeader("Idempotent-Replayed", "true");
        assertThat(retry).bodyJson().extractingPath("$.id").isEqualTo(JsonPath.read(first.getResponse().getContentAsString(), "$.id"));
        assertThat(balance(from)).isEqualByComparingTo("60.00");

        assertThat(transfer(from, to.iban(), "41.00", key)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(transfer(from, to.iban(), "1.00", null)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(balance(from)).isEqualByComparingTo("60.00");
    }

    @Test
    void refusesWhatCannotBePaid() throws Exception {
        Account from = open(alpha);
        Account to = open(beta);
        deposit(from, "10.00");

        assertThat(transfer(from, to.iban(), "10.01", UUID.randomUUID().toString())).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(transfer(from, Iban.of("GR", "9990001" + "0000009999999999"), "1.00", UUID.randomUUID().toString()))
            .as("no such account here").hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(transfer(from, "GR1601101250000000012300696", "1.00", UUID.randomUUID().toString()))
            .as("wrong check digits").hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(transfer(from, from.iban(), "1.00", UUID.randomUUID().toString())).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(balance(from)).isEqualByComparingTo("10.00");
    }

    @Test
    void anotherBusinessCanNeitherSeeNorUseTheAccount() throws Exception {
        Account account = open(alpha);
        deposit(account, "50.00");

        MvcTestResult stolen = mvc.post().uri("/api/v1/businesses/{b}/accounts/{a}/transfers", beta.businessId(), account.id())
            .with(beta.auth()).with(csrf()).header("Idempotency-Key", "x").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"creditorIban": "%s", "creditorName": "x", "amount": 1}""".formatted(open(beta).iban()))
            .exchange();

        assertThat(stolen).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/v1/businesses/{b}/accounts/{a}/transactions", alpha.businessId(), account.id())
            .with(beta.auth()).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(balance(account)).isEqualByComparingTo("50.00");
    }

    @Test
    void aFrozenAccountNeitherPaysNorReceivesAndOnlyManagersFreeze() throws Exception {
        Account from = open(alpha);
        Account to = open(beta);
        deposit(from, "20.00");
        String freeze = """
            {"reason": "Έλεγχος AML"}""";

        assertThat(mvc.post().uri("/api/v1/bank/accounts/{a}/freeze", to.id()).with(user("ANALYST")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(freeze).exchange()).hasStatus(HttpStatus.FORBIDDEN);
        MvcTestResult frozen = mvc.post().uri("/api/v1/bank/accounts/{a}/freeze", to.id()).with(user("MANAGER")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(freeze).exchange();
        assertThat(frozen).bodyJson().extractingPath("$.status").isEqualTo("FROZEN");

        assertThat(transfer(from, to.iban(), "5.00", UUID.randomUUID().toString())).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);

        mvc.post().uri("/api/v1/bank/accounts/{a}/unfreeze", to.id()).with(user("MANAGER")).with(csrf()).exchange();
        assertThat(transfer(from, to.iban(), "5.00", UUID.randomUUID().toString())).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void concurrentTransfersNeverOverdrawAndNeverDeadlock() throws Exception {
        Account a = open(alpha);
        Account b = open(beta);
        deposit(a, "100.00");
        deposit(b, "100.00");
        var request = new BankingViews.TransferRequest(b.iban(), "Βήτα Αρτοποιία", new BigDecimal("10.00"), null, null);
        var back = new BankingViews.TransferRequest(a.iban(), "Αλφα Καφέ", new BigDecimal("10.00"), null, null);

        List<Callable<Boolean>> work = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            // A pays B fifteen times (only ten can succeed at first) while B pays A ten times
            work.add(() -> attempt(() -> banking.transfer(alpha.user(), alpha.businessId(), a.id(), UUID.randomUUID().toString(), request)));
            if (i < 10) {
                work.add(() -> attempt(() -> banking.transfer(beta.user(), beta.businessId(), b.id(), UUID.randomUUID().toString(), back)));
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
        assertThat(balance(a)).isNotNegative();
        assertThat(balance(b)).isNotNegative();
        assertThat(succeeded).isBetween(20L, 25L);
        assertThat(ledgerTotal()).isEqualByComparingTo("0");
        Long negative = jdbc.queryForObject("select count(*) from posting where account_id in (?, ?) and balance_after < 0", Long.class, a.id(), b.id());
        assertThat(negative).isZero();
    }

    private static boolean attempt(Runnable transfer) {
        try {
            transfer.run();
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
            jdbc.update("insert into journal_entry (id, kind, description, initiated_by, booked_at) values (?, 'TRANSFER', 'forged', 'x', now())", entry);
            jdbc.update("insert into posting (id, journal_entry_id, position, account_id, amount, balance_after, booked_at) values (?, ?, 0, ?, 1000, 1030, now())",
                UUID.randomUUID(), entry, account.id());
        })).hasMessageContaining("does not balance");

        assertThatThrownBy(() -> jdbc.update("update posting set amount = 1000000 where account_id = ?", account.id()))
            .rootCause().hasMessageContaining("posting is append-only");
        assertThatThrownBy(() -> jdbc.update("delete from journal_entry")).rootCause().hasMessageContaining("journal_entry is append-only");
    }

    @Autowired
    Ledger ledger;

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
            ledger.book(EntryKind.TRANSFER, "out", null, "test", null, List.of(
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
        assertThat(trial).bodyJson().extractingPath("$.internal[*].code").asArray().contains("SETTLEMENT", "SEPA_OUT", "LOANS");
        assertThat(mvc.get().uri("/api/v1/bank/ledger/trial-balance").with(user("ANALYST")).exchange()).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/v1/bank/accounts?search=Αλφα").with(user("ANALYST")).exchange()).hasStatusOk();
    }
}
