package com.tameio.accounting;

import com.jayway.jsonpath.JsonPath;
import com.tameio.TestcontainersConfiguration;
import com.tameio.documents.DocumentReviewed;
import com.tameio.documents.ExpenseCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static com.tameio.TestUsers.businessUser;
import static com.tameio.TestUsers.freshAfm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * The books from the outside: the documents module publishes {@link DocumentReviewed} (here by the test,
 * inside a transaction, as the documents module does) and the expense book, summary and export follow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountingApiTest {

    private static final int YEAR = 2026;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transaction;

    private RequestPostProcessor owner;
    private UUID businessId;

    @BeforeEach
    void registerABusiness() throws Exception {
        owner = businessUser();
        MvcTestResult created = mvc.post().uri("/api/v1/businesses").with(owner).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"afm": "%s", "name": "Φούρνος Δοκιμής"}""".formatted(freshAfm()))
            .exchange();
        businessId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
    }

    private UUID review(UUID documentId, LocalDate issued, String net, String vat, ExpenseCategory category) {
        BigDecimal n = new BigDecimal(net);
        BigDecimal v = new BigDecimal(vat);
        transaction.executeWithoutResult(status -> events.publishEvent(
            new DocumentReviewed(documentId, businessId, "094014201", issued, n, v, n.add(v), category)));
        return documentId;
    }

    private MvcTestResult expenses(String query) {
        return mvc.get().uri("/api/v1/businesses/{b}/expenses?" + query, businessId).with(owner).exchange();
    }

    private int countInYear() throws Exception {
        return JsonPath.read(expenses("from=2026-01-01&to=2026-12-31").getResponse().getContentAsString(), "$.page.totalElements");
    }

    private void awaitCount(int expected) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(countInYear()).isEqualTo(expected));
    }

    @Test
    void aReviewedDocumentBecomesAnExpenseAndASecondReviewUpdatesIt() throws Exception {
        UUID document = review(UUID.randomUUID(), LocalDate.of(YEAR, 3, 10), "10.00", "2.40", ExpenseCategory.FUEL);
        awaitCount(1);

        review(document, LocalDate.of(YEAR, 3, 10), "20.00", "4.80", ExpenseCategory.TRANSPORT);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            MvcTestResult list = expenses("from=2026-01-01&to=2026-12-31");
            assertThat(list).bodyJson().extractingPath("$.content[0].category").isEqualTo("TRANSPORT");
            assertThat(list).bodyJson().extractingPath("$.content[0].totalAmount").isEqualTo(24.80);
        });
        assertThat(countInYear()).isEqualTo(1);
    }

    @Test
    void summarisesTheYearByMonthQuarterAndCategory() throws Exception {
        review(UUID.randomUUID(), LocalDate.of(YEAR, 1, 15), "100.00", "24.00", ExpenseCategory.RENT);
        review(UUID.randomUUID(), LocalDate.of(YEAR, 2, 3), "10.00", "2.40", ExpenseCategory.FUEL);
        review(UUID.randomUUID(), LocalDate.of(YEAR, 2, 20), "5.00", "1.20", ExpenseCategory.FUEL);
        review(UUID.randomUUID(), LocalDate.of(YEAR, 11, 1), "50.00", "6.50", ExpenseCategory.GROCERIES);
        // Another year stays out of this one
        review(UUID.randomUUID(), LocalDate.of(YEAR - 1, 12, 31), "999.00", "0.00", ExpenseCategory.EQUIPMENT);
        awaitCount(4);

        MvcTestResult summary = mvc.get().uri("/api/v1/businesses/{b}/accounting/summary?year=2026", businessId).with(owner).exchange();

        assertThat(summary).hasStatusOk();
        assertThat(summary).bodyJson().extractingPath("$.totals.total").isEqualTo(199.10);
        assertThat(summary).bodyJson().extractingPath("$.totals.vat").isEqualTo(34.10);
        assertThat(summary).bodyJson().extractingPath("$.totals.count").isEqualTo(4);
        assertThat(summary).bodyJson().extractingPath("$.months").asArray().hasSize(12);
        assertThat(summary).bodyJson().extractingPath("$.months[1].totals.total").isEqualTo(18.60);
        assertThat(summary).bodyJson().extractingPath("$.months[2].totals.total").isEqualTo(0.00);
        assertThat(summary).bodyJson().extractingPath("$.quarters[0].totals.vat").isEqualTo(27.60);
        assertThat(summary).bodyJson().extractingPath("$.quarters[3].totals.count").isEqualTo(1);
        assertThat(summary).bodyJson().extractingPath("$.categories[0].category").isEqualTo("RENT");
        assertThat(summary).bodyJson().extractingPath("$.categories[1].category").isEqualTo("GROCERIES");
    }

    @Test
    void filtersTheBookByCategory() throws Exception {
        review(UUID.randomUUID(), LocalDate.of(YEAR, 5, 1), "10.00", "2.40", ExpenseCategory.FUEL);
        review(UUID.randomUUID(), LocalDate.of(YEAR, 5, 2), "30.00", "7.20", ExpenseCategory.TELECOM);
        awaitCount(2);

        MvcTestResult telecom = expenses("from=2026-01-01&to=2026-12-31&category=TELECOM");

        assertThat(telecom).bodyJson().extractingPath("$.page.totalElements").isEqualTo(1);
        assertThat(telecom).bodyJson().extractingPath("$.content[0].category").isEqualTo("TELECOM");
    }

    @Test
    void exportsACsvGreekExcelOpens() throws Exception {
        review(UUID.randomUUID(), LocalDate.of(YEAR, 4, 9), "1234.50", "296.28", ExpenseCategory.EQUIPMENT);
        awaitCount(1);

        MvcTestResult csv = mvc.get().uri("/api/v1/businesses/{b}/expenses/export?from=2026-01-01&to=2026-12-31", businessId)
            .with(owner).exchange();

        assertThat(csv).hasStatusOk();
        assertThat(csv).hasHeader("Content-Disposition", "attachment; filename=\"exoda-2026-01-01-2026-12-31.csv\"");
        String body = new String(csv.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(body).startsWith("﻿Ημερομηνία;ΑΦΜ εκδότη;Κατηγορία;Καθαρό;ΦΠΑ;Σύνολο;Παραστατικό\r\n");
        assertThat(body).contains("09/04/2026;094014201;Εξοπλισμός;1234,50;296,28;1530,78;");
    }

    @Test
    void refusesAPeriodThatEndsBeforeItStartsAndAnExportLongerThanAYear() {
        assertThat(expenses("from=2026-12-31&to=2026-01-01")).hasStatus(HttpStatus.BAD_REQUEST);

        MvcTestResult tooLong = mvc.get().uri("/api/v1/businesses/{b}/expenses/export?from=2020-01-01&to=2026-12-31", businessId)
            .with(owner).exchange();
        assertThat(tooLong).hasStatus(HttpStatus.BAD_REQUEST);

        MvcTestResult badYear = mvc.get().uri("/api/v1/businesses/{b}/accounting/summary?year=1999", businessId).with(owner).exchange();
        assertThat(badYear).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anotherBusinessSeesNothing() {
        MvcTestResult stranger = mvc.get().uri("/api/v1/businesses/{b}/accounting/summary?year=2026", businessId)
            .with(businessUser()).exchange();

        assertThat(stranger).hasStatus(HttpStatus.NOT_FOUND);
    }
}
