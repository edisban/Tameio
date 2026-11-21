package com.tameio.identity;

import com.tameio.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/** A local stack with demo data opens with people to sign in as and a year of balanced history. */
@SpringBootTest(properties = "tameio.demo.enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DemoDataTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DemoData demo;

    @Test
    void theDemoPeopleCanSignIn() {
        for (String[] person : new String[][] {
            {DemoData.OWNER_EMAIL, DemoData.BUSINESS_PASSWORD},
            {"manager@tameio.local", DemoData.STAFF_PASSWORD},
            {"analyst@tameio.local", DemoData.STAFF_PASSWORD}}) {
            var signedIn = mvc.post().uri("/api/v1/auth/sign-in").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "%s", "password": "%s"}""".formatted(person[0], person[1]))
                .exchange();
            assertThat(signedIn).as(person[0]).hasStatusOk();
            assertThat(signedIn).bodyJson().extractingPath("$.mustChangePassword").isEqualTo(false);
        }
    }

    @Test
    void everyDemoBusinessHasAnAccountWithAYearOfHistoryAndAnExpenseBook() {
        Long businesses = jdbc.queryForObject("""
            select count(*) from business b join membership m on m.business_id = b.id
            join user_account u on u.id::text = m.user_id where u.email in (?, ?)""", Long.class,
            DemoData.OWNER_EMAIL, DemoData.SECOND_OWNER_EMAIL);
        assertThat(businesses).isEqualTo(4);

        var accounts = jdbc.queryForList("select id, balance from bank_account where kind = 'CUSTOMER' and created_by = 'system'");
        assertThat(accounts).hasSizeGreaterThanOrEqualTo(4);
        for (var account : accounts) {
            assertThat((BigDecimal) account.get("balance")).isPositive();
            Long postings = jdbc.queryForObject("select count(*) from posting where account_id = ?", Long.class, account.get("id"));
            assertThat(postings).isGreaterThan(100);
        }
        Long spread = jdbc.queryForObject("select count(distinct date_trunc('month', booked_at)) from posting", Long.class);
        assertThat(spread).isGreaterThanOrEqualTo(12);
        assertThat(jdbc.queryForObject("select count(*) from expense", Long.class)).isGreaterThan(200);
    }

    @Test
    void theBooksStillBalanceAndRunningItAgainAddsNothing() {
        long entries = jdbc.queryForObject("select count(*) from journal_entry", Long.class);

        demo.run(new DefaultApplicationArguments());

        assertThat(jdbc.queryForObject("select count(*) from journal_entry", Long.class)).isEqualTo(entries);
        assertThat(jdbc.queryForObject("select sum(balance) from bank_account", BigDecimal.class)).isEqualByComparingTo("0");
        // Every account's balance equals the sum of its postings
        Long mismatched = jdbc.queryForObject("""
            select count(*) from bank_account a
            where a.balance <> (select coalesce(sum(p.amount), 0) from posting p where p.account_id = a.id)""", Long.class);
        assertThat(mismatched).isZero();
    }

    @Test
    void theBankHasADemoLoanApplicationInEveryStatus() {
        var statuses = jdbc.queryForList("select distinct status from loan_application", String.class);

        assertThat(statuses).containsExactlyInAnyOrder("DRAFT", "SUBMITTED", "IN_REVIEW", "RECOMMENDED", "APPROVED", "DECLINED", "DISBURSED");
    }

    @Test
    void theDisbursedDemoLoanIsInTheLedger() {
        BigDecimal loans = jdbc.queryForObject("select balance from bank_account where code = 'LOANS'", BigDecimal.class);

        assertThat(loans).isEqualByComparingTo("-25000.00");
    }
}
