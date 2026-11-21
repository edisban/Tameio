package com.tameio.lending;

import com.jayway.jsonpath.JsonPath;
import com.tameio.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

import static com.tameio.TestUsers.freshAfm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/** A loan from the business's draft to the money in its account, against a real database and ledger. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LendingApiTest {

    private final MockMvcTester mvc;
    private final JdbcTemplate jdbc;

    private RequestPostProcessor owner;
    private String businessId;
    private String accountId;
    private RequestPostProcessor analyst;
    private RequestPostProcessor manager;

    LendingApiTest(MockMvcTester mvc, JdbcTemplate jdbc) {
        this.mvc = mvc;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void aBusinessWithMoneyAndTwoBankers() throws Exception {
        owner = signedIn(UUID.randomUUID().toString(), "Owner", "BUSINESS");
        businessId = read(post("/api/v1/businesses", owner, """
            {"afm": "%s", "name": "Φούρνος Δανείου"}""".formatted(freshAfm())), "$.id");
        accountId = read(post("/api/v1/businesses/" + businessId + "/accounts", owner, "{}"), "$.id");
        post("/api/v1/businesses/" + businessId + "/accounts/" + accountId + "/demo-deposits", owner, """
            {"amount": 9000}""");
        analyst = staff("ANALYST", null);
        manager = staff("MANAGER", "50000.00");
    }

    /** A member of staff as the database knows them, with an approval limit, signed in with that role. */
    private RequestPostProcessor staff(String role, String approvalLimit) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into user_account (id, email, password_hash, full_name, role, failed_sign_ins, active, session_version,
                must_change_password, approval_limit, version, created_at, created_by, updated_at)
            values (?, ?, 'x', ?, ?, 0, true, 0, false, ?, 0, now(), 'test', now())""",
            id, role.toLowerCase() + "-" + id + "@bank.example", role + " Test", role,
            approvalLimit == null ? null : new BigDecimal(approvalLimit));
        return signedIn(id.toString(), role + " Test", role);
    }

    private static RequestPostProcessor signedIn(String id, String name, String... roles) {
        return jwt().jwt(t -> t.subject(id).claim("name", name).claim("email", "x@example.com"))
            .authorities(Arrays.stream(roles).map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }

    private MvcTestResult post(String uri, RequestPostProcessor who, String body) {
        return mvc.post().uri(uri).with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private static String read(MvcTestResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private String draft(String amount) throws Exception {
        return read(post("/api/v1/businesses/" + businessId + "/loan-applications", owner, """
            {"accountId": "%s", "amount": %s, "termMonths": 24, "purpose": "EQUIPMENT", "description": "Νέος φούρνος"}"""
            .formatted(accountId, amount)), "$.id");
    }

    private String submitted(String amount) throws Exception {
        String id = draft(amount);
        post("/api/v1/businesses/" + businessId + "/loan-applications/" + id + "/submit", owner, "");
        return id;
    }

    private String recommended(String amount) throws Exception {
        String id = submitted(amount);
        post("/api/v1/bank/loan-applications/" + id + "/review", analyst, "");
        post("/api/v1/bank/loan-applications/" + id + "/recommendation", analyst, """
            {"outcome": "APPROVE", "note": "Steady takings"}""");
        return id;
    }

    private MvcTestResult decide(String id, RequestPostProcessor who) {
        return post("/api/v1/bank/loan-applications/" + id + "/decision", who, """
            {"outcome": "APPROVE", "note": "Agreed"}""");
    }

    private BigDecimal balance() {
        return jdbc.queryForObject("select balance from bank_account where id = ?::uuid", BigDecimal.class, accountId);
    }

    @Test
    void aDraftIsFilledInFromTheAccountAndQuotesTheInstalment() throws Exception {
        MvcTestResult created = post("/api/v1/businesses/" + businessId + "/loan-applications", owner, """
            {"accountId": "%s", "amount": 10000, "termMonths": 36, "purpose": "EQUIPMENT"}""".formatted(accountId));

        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).bodyJson().extractingPath("$.status").isEqualTo("DRAFT");
        assertThat(created).bodyJson().extractingPath("$.monthlyInstalment").isEqualTo(308.77);
        assertThat(created).bodyJson().extractingPath("$.figures.balance").isEqualTo(9000.00);
        assertThat(created).bodyJson().extractingPath("$.figures.averageMonthlyInflow").isEqualTo(750.00);
    }

    @Test
    void theBusinessDoesNotSeeTheBanksRiskScore() throws Exception {
        String id = draft("5000");

        MvcTestResult seen = mvc.get().uri("/api/v1/businesses/{b}/loan-applications/{id}", businessId, id).with(owner).exchange();

        assertThat(seen).bodyJson().doesNotHavePath("$.riskScore");
        assertThat(seen).bodyJson().doesNotHavePath("$.riskFactors");
    }

    @Test
    void termsOutsideTheProductAreRefused() {
        MvcTestResult tooSmall = post("/api/v1/businesses/" + businessId + "/loan-applications", owner, """
            {"accountId": "%s", "amount": 50, "termMonths": 3, "purpose": "EQUIPMENT"}""".formatted(accountId));

        assertThat(tooSmall).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(tooSmall).bodyJson().extractingPath("$.errors.amount").isEqualTo("At least 1,000");
        assertThat(tooSmall).bodyJson().extractingPath("$.errors.termMonths").isEqualTo("At least 6 months");
    }

    @Test
    void anotherBusinessCannotSeeTheApplication() throws Exception {
        String id = draft("5000");

        MvcTestResult stranger = mvc.get().uri("/api/v1/businesses/{b}/loan-applications/{id}", businessId, id)
            .with(signedIn(UUID.randomUUID().toString(), "Other", "BUSINESS")).exchange();

        assertThat(stranger).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void theBankNeverSeesADraft() throws Exception {
        String id = draft("5000");

        assertThat(mvc.get().uri("/api/v1/bank/loan-applications/{id}", id).with(analyst).exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void theCaseShowsTheRiskScoreWithItsFactors() throws Exception {
        String id = submitted("5000");

        MvcTestResult opened = mvc.get().uri("/api/v1/bank/loan-applications/{id}", id).with(analyst).exchange();

        assertThat(opened).hasStatusOk();
        assertThat(opened).bodyJson().extractingPath("$.riskFactors").asArray().hasSize(4);
        assertThat(opened).bodyJson().extractingPath("$.riskFactors[0].code").isEqualTo("REPAYMENT_CAPACITY");
        assertThat(opened).bodyJson().extractingPath("$.riskBand").isNotNull();
    }

    @Test
    void anAnalystRecommendsAfterTakingTheCaseIntoReview() throws Exception {
        String id = recommended("5000");

        MvcTestResult opened = mvc.get().uri("/api/v1/bank/loan-applications/{id}", id).with(analyst).exchange();

        assertThat(opened).bodyJson().extractingPath("$.status").isEqualTo("RECOMMENDED");
        assertThat(opened).bodyJson().extractingPath("$.recommended.name").isEqualTo("ANALYST Test");
        assertThat(opened).bodyJson().extractingPath("$.recommendationNote").isEqualTo("Steady takings");
    }

    @Test
    void aManagerWithinTheirLimitApproves() throws Exception {
        String id = recommended("5000");

        MvcTestResult approved = decide(id, manager);

        assertThat(approved).hasStatusOk();
        assertThat(approved).bodyJson().extractingPath("$.status").isEqualTo("APPROVED");
    }

    @Test
    void managerCannotApproveAboveTheirLimit() throws Exception {
        String id = recommended("60000");

        assertThat(decide(id, manager)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void thePersonWhoRecommendedCannotDecide() throws Exception {
        String id = submitted("5000");
        UUID both = UUID.randomUUID();
        jdbc.update("""
            insert into user_account (id, email, password_hash, full_name, role, failed_sign_ins, active, session_version,
                must_change_password, approval_limit, version, created_at, created_by, updated_at)
            values (?, ?, 'x', 'Both Hats', 'MANAGER', 0, true, 0, false, 100000, 0, now(), 'test', now())""", both, both + "@bank.example");
        RequestPostProcessor twoHats = signedIn(both.toString(), "Both Hats", "ANALYST", "MANAGER");
        post("/api/v1/bank/loan-applications/" + id + "/review", twoHats, "");
        post("/api/v1/bank/loan-applications/" + id + "/recommendation", twoHats, """
            {"outcome": "APPROVE", "note": "Mine"}""");

        assertThat(decide(id, twoHats)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void anAnalystCannotDecide() throws Exception {
        String id = recommended("5000");

        assertThat(decide(id, analyst)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void aStepOutOfOrderIsAConflict() throws Exception {
        String id = submitted("5000");

        assertThat(decide(id, manager)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void disbursementPaysTheLoanIntoTheAccountThroughTheLedger() throws Exception {
        String id = recommended("5000");
        decide(id, manager);

        MvcTestResult paid = post("/api/v1/bank/loan-applications/" + id + "/disbursement", manager, "");

        assertThat(paid).bodyJson().extractingPath("$.status").isEqualTo("DISBURSED");
        assertThat(balance()).isEqualByComparingTo("14000.00");
        assertThat(jdbc.queryForObject("select balance from bank_account where code = 'LOANS'", BigDecimal.class)).isNegative();
        assertThat(jdbc.queryForObject("select sum(balance) from bank_account", BigDecimal.class)).isEqualByComparingTo("0");
    }

    @Test
    void aLoanIsDisbursedOnlyOnce() throws Exception {
        String id = recommended("5000");
        decide(id, manager);
        post("/api/v1/bank/loan-applications/" + id + "/disbursement", manager, "");

        MvcTestResult again = post("/api/v1/bank/loan-applications/" + id + "/disbursement", manager, "");

        assertThat(again).hasStatus(HttpStatus.CONFLICT);
        assertThat(balance()).isEqualByComparingTo("14000.00");
    }

    @Test
    void theBusinessFollowsItsApplicationAndSeesTheDecision() throws Exception {
        String id = recommended("5000");
        decide(id, manager);

        MvcTestResult seen = mvc.get().uri("/api/v1/businesses/{b}/loan-applications/{id}", businessId, id).with(owner).exchange();

        assertThat(seen).bodyJson().extractingPath("$.status").isEqualTo("APPROVED");
        assertThat(seen).bodyJson().extractingPath("$.decisionNote").isEqualTo("Agreed");
    }
}
