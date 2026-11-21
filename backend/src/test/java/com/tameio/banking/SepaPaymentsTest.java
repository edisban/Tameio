package com.tameio.banking;

import com.jayway.jsonpath.JsonPath;
import com.tameio.TestcontainersConfiguration;
import com.tameio.platform.validation.Iban;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static com.tameio.TestUsers.freshAfm;
import static com.tameio.TestUsers.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * SEPA payments end to end through RabbitMQ and the simulated clearing house: settlement, returns with
 * ISO 20022 reason codes, incoming payments, and Verification of Payee.
 */
@SpringBootTest(properties = "tameio.clearing.settlement-delay=50ms")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SepaPaymentsTest {

    // The simulated other banks' accounts (see OtherBanks)
    private static final String POWER_COMPANY = Iban.of("GR", "9910001" + "%016d".formatted(1001));
    private static final String CLOSED_ACCOUNT = Iban.of("GR", "9920003" + "%016d".formatted(10000));
    private static final String UNKNOWN_AT_OTHER_BANK = Iban.of("GR", "9940002" + "%016d".formatted(777));

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RabbitTemplate rabbit;

    private RequestPostProcessor owner;
    private UUID businessId;
    private String accountId;
    private String iban;

    @BeforeEach
    void anAccountWithMoney() throws Exception {
        String id = UUID.randomUUID().toString();
        owner = jwt().jwt(t -> t.subject(id).claim("name", "Owner").claim("email", "o@example.com"))
            .authorities(new SimpleGrantedAuthority("ROLE_BUSINESS"));
        MvcTestResult business = mvc.post().uri("/api/v1/businesses").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"afm": "%s", "name": "Ψητοπωλείο Σέπα"}""".formatted(freshAfm()))
            .exchange();
        businessId = UUID.fromString(JsonPath.read(business.getResponse().getContentAsString(), "$.id"));
        MvcTestResult account = mvc.post().uri("/api/v1/businesses/{b}/accounts", businessId).with(owner).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{}").exchange();
        accountId = JsonPath.read(account.getResponse().getContentAsString(), "$.id");
        iban = JsonPath.read(account.getResponse().getContentAsString(), "$.iban");
        mvc.post().uri("/api/v1/businesses/{b}/accounts/{a}/demo-deposits", businessId, accountId).with(owner).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"amount": 1000}""").exchange();
    }

    private MvcTestResult pay(String creditorIban, String name, String amount, Boolean confirmed) {
        return mvc.post().uri("/api/v1/businesses/{b}/accounts/{a}/transfers", businessId, accountId).with(owner).with(csrf())
            .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"creditorIban": "%s", "creditorName": "%s", "amount": %s, "reference": "ΛΟΓ 7781", "confirmedMismatch": %s}"""
                .formatted(creditorIban, name, amount, confirmed))
            .exchange();
    }

    private BigDecimal balance() {
        return jdbc.queryForObject("select balance from bank_account where id = ?::uuid", BigDecimal.class, accountId);
    }

    private String paymentStatus(String paymentId) {
        return jdbc.queryForObject("select status from payment where id = ?::uuid", String.class, paymentId);
    }

    private MvcTestResult verify(String iban, String name) {
        return mvc.post().uri("/api/v1/payee-verification").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"iban": "%s", "name": "%s"}""".formatted(iban, name))
            .exchange();
    }

    @Test
    void aPaymentToAnotherBankIsDebitedAtOnceAndSettledByTheClearingHouse() throws Exception {
        MvcTestResult paid = pay(POWER_COMPANY, "Ηλεκτρική Ενέργεια Αιγαίου ΑΕ", "250.00", null);

        assertThat(paid).hasStatus(HttpStatus.CREATED);
        assertThat(paid).bodyJson().extractingPath("$.scheme").isEqualTo("SEPA");
        assertThat(paid).bodyJson().extractingPath("$.status").isEqualTo("PENDING");
        assertThat(paid).bodyJson().extractingPath("$.payeeCheck").isEqualTo("MATCH");
        assertThat(balance()).isEqualByComparingTo("750.00");
        String id = JsonPath.read(paid.getResponse().getContentAsString(), "$.id");

        await().atMost(Duration.ofSeconds(20)).until(() -> paymentStatus(id).equals("SETTLED"));

        // The money has left the bank: nothing is left in SEPA clearing for it, and the books still balance
        assertThat(jdbc.queryForObject("select sum(balance) from bank_account", BigDecimal.class)).isEqualByComparingTo("0");
        MvcTestResult list = mvc.get().uri("/api/v1/businesses/{b}/accounts/{a}/payments", businessId, accountId).with(owner).exchange();
        assertThat(list).bodyJson().extractingPath("$.content[0].status").isEqualTo("SETTLED");
        assertThat(list).bodyJson().extractingPath("$.content[0].endToEndId").asString().startsWith("TAMEIO");
    }

    @Test
    void aPaymentToAClosedAccountComesBackWithItsReason() throws Exception {
        MvcTestResult paid = pay(CLOSED_ACCOUNT, "Παλιό Κατάστημα ΕΠΕ", "100.00", null);
        String id = JsonPath.read(paid.getResponse().getContentAsString(), "$.id");
        assertThat(balance()).isEqualByComparingTo("900.00");

        await().atMost(Duration.ofSeconds(20)).until(() -> paymentStatus(id).equals("RETURNED"));

        assertThat(balance()).isEqualByComparingTo("1000.00");
        MvcTestResult list = mvc.get().uri("/api/v1/businesses/{b}/accounts/{a}/payments", businessId, accountId).with(owner).exchange();
        assertThat(list).bodyJson().extractingPath("$.content[0].reasonCode").isEqualTo("AC04");
        MvcTestResult statement = mvc.get().uri("/api/v1/businesses/{b}/accounts/{a}/transactions", businessId, accountId).with(owner).exchange();
        assertThat(statement).bodyJson().extractingPath("$.content[0].kind").isEqualTo("SEPA_RETURN");
        assertThat(statement).bodyJson().extractingPath("$.content[0].amount").isEqualTo(100.00);
    }

    @Test
    void aRepeatedReportChangesNothing() throws Exception {
        MvcTestResult paid = pay(POWER_COMPANY, "ΗΛΕΚΤΡΙΚΗ ΕΝΕΡΓΕΙΑ ΑΙΓΑΙΟΥ Α.Ε.", "10.00", null);
        String id = JsonPath.read(paid.getResponse().getContentAsString(), "$.id");
        String endToEnd = JsonPath.read(paid.getResponse().getContentAsString(), "$.endToEndId");
        await().atMost(Duration.ofSeconds(20)).until(() -> paymentStatus(id).equals("SETTLED"));
        long entries = jdbc.queryForObject("select count(*) from journal_entry", Long.class);

        rabbit.convertAndSend("tameio.banking", "sct.report", new PaymentEvents.SettlementReport(endToEnd, "REJECTED", "AC04", "late"));
        Thread.sleep(500);

        assertThat(paymentStatus(id)).isEqualTo("SETTLED");
        assertThat(jdbc.queryForObject("select count(*) from journal_entry", Long.class)).isEqualTo(entries);
        assertThat(balance()).isEqualByComparingTo("990.00");
    }

    @Test
    void verificationOfPayeeTellsMatchCloseMatchNoMatchAndNotPossible() {
        assertThat(verify(POWER_COMPANY, "ηλεκτρική ενέργεια αιγαίου")).bodyJson().extractingPath("$.result").isEqualTo("MATCH");
        MvcTestResult close = verify(POWER_COMPANY, "Ηλεκτρικη Ενεργια Αιγαιου");
        assertThat(close).bodyJson().extractingPath("$.result").isEqualTo("CLOSE_MATCH");
        assertThat(close).bodyJson().extractingPath("$.actualName").isEqualTo("ΗΛΕΚΤΡΙΚΗ ΕΝΕΡΓΕΙΑ ΑΙΓΑΙΟΥ Α.Ε.");
        assertThat(verify(POWER_COMPANY, "Κάποιος Άλλος")).bodyJson().extractingPath("$.result").isEqualTo("NO_MATCH");
        assertThat(verify(UNKNOWN_AT_OTHER_BANK, "Οποιοσδήποτε")).bodyJson().extractingPath("$.result").isEqualTo("NOT_POSSIBLE");
        assertThat(verify(iban, "Ψητοπωλείο Σέπα")).bodyJson().extractingPath("$.result").isEqualTo("MATCH");
    }

    @Test
    void aNameThatDoesNotMatchNeedsThePayersConfirmation() {
        assertThat(pay(POWER_COMPANY, "Κάποιος Άλλος", "5.00", null)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(balance()).isEqualByComparingTo("1000.00");

        MvcTestResult confirmed = pay(POWER_COMPANY, "Κάποιος Άλλος", "5.00", true);

        assertThat(confirmed).hasStatus(HttpStatus.CREATED);
        assertThat(confirmed).bodyJson().extractingPath("$.payeeCheck").isEqualTo("NO_MATCH");
    }

    @Test
    void anotherBankCanPayInAndAFrozenAccountSendsItBack() throws Exception {
        String incoming = """
            {"creditorIban": "%s", "debtorName": "Πελάτης Εξωτερικός", "amount": 320.40, "reference": "ΠΑΡ 15"}""".formatted(iban);

        assertThat(mvc.post().uri("/api/v1/clearing/incoming").with(user("ADMIN")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(incoming).exchange()).hasStatus(HttpStatus.ACCEPTED);
        await().atMost(Duration.ofSeconds(20)).until(() -> balance().compareTo(new BigDecimal("1320.40")) == 0);
        MvcTestResult list = mvc.get().uri("/api/v1/businesses/{b}/accounts/{a}/payments", businessId, accountId).with(owner).exchange();
        assertThat(list).bodyJson().extractingPath("$.content[0].direction").isEqualTo("INCOMING");
        assertThat(list).bodyJson().extractingPath("$.content[0].counterpartyName").isEqualTo("Πελάτης Εξωτερικός");

        mvc.post().uri("/api/v1/bank/accounts/{a}/freeze", accountId).with(user("MANAGER")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"reason": "Έλεγχος"}""").exchange();
        mvc.post().uri("/api/v1/clearing/incoming").with(user("ADMIN")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(incoming).exchange();
        Thread.sleep(1000);
        assertThat(balance()).isEqualByComparingTo("1320.40");

        assertThat(mvc.post().uri("/api/v1/clearing/incoming").with(owner).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(incoming).exchange()).hasStatus(HttpStatus.FORBIDDEN);
    }
}
