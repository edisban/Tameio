package com.tameio.documents;

import com.jayway.jsonpath.JsonPath;
import com.tameio.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static com.tameio.TestUsers.businessUser;
import static com.tameio.TestUsers.freshAfm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/** Confirming and correcting what document AI read, against the real database and queue. */
@SpringBootTest(properties = {
    "spring.rabbitmq.listener.simple.retry.initial-interval=20ms",
    "spring.rabbitmq.listener.simple.retry.multiplier=1",
})
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import({TestcontainersConfiguration.class, ScriptedDocumentAi.class})
class DocumentReviewTest {

    private static final String AS_READ = """
        {"issuerAfm": "094014201", "issueDate": "2026-11-14", "netAmount": 10.00, "vatAmount": 2.40,
         "totalAmount": 12.40, "category": "FUEL"}""";

    private final MockMvcTester mvc;

    private RequestPostProcessor owner;
    private String businessId;

    DocumentReviewTest(MockMvcTester mvc) {
        this.mvc = mvc;
    }

    @BeforeEach
    void registerABusiness() throws Exception {
        owner = businessUser();
        MvcTestResult created = mvc.post().uri("/api/v1/businesses").with(owner).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"afm": "%s", "name": "Ταβέρνα Δοκιμής"}""".formatted(freshAfm()))
            .exchange();
        businessId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    private String uploadAndWait(String behaviour, String status) throws Exception {
        byte[] content = ("%PDF-1.7\n% " + behaviour + " " + UUID.randomUUID() + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
        MvcTestResult uploaded = mvc.post().uri("/api/v1/businesses/{id}/documents", businessId).with(owner).with(csrf())
            .multipart().file(new MockMultipartFile("file", "receipt.pdf", "application/pdf", content)).exchange();
        String id = JsonPath.read(uploaded.getResponse().getContentAsString(), "$.id");
        await().atMost(Duration.ofSeconds(30)).until(() -> status.equals(
            JsonPath.read(details(id).getResponse().getContentAsString(), "$.status")));
        return id;
    }

    private MvcTestResult details(String id) {
        return mvc.get().uri("/api/v1/businesses/{b}/documents/{d}", businessId, id).with(owner).exchange();
    }

    private MvcTestResult review(String id, String body) {
        return mvc.put().uri("/api/v1/businesses/{b}/documents/{d}/review", businessId, id).with(owner).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    @Test
    void confirmingWhatWasReadRecordsNoCorrections(ApplicationEvents events) throws Exception {
        String id = uploadAndWait("receipt", "READ");

        MvcTestResult reviewed = review(id, AS_READ);

        assertThat(reviewed).hasStatusOk();
        assertThat(reviewed).bodyJson().extractingPath("$.status").isEqualTo("REVIEWED");
        assertThat(reviewed).bodyJson().extractingPath("$.review.correctedFields").asArray().isEmpty();
        // What was suggested stays visible next to what was confirmed
        assertThat(reviewed).bodyJson().extractingPath("$.reading.category.code").isEqualTo("FUEL");
        assertThat(events.stream(DocumentReviewed.class))
            .singleElement()
            .satisfies(event -> {
                assertThat(event.documentId()).isEqualTo(UUID.fromString(id));
                assertThat(event.totalAmount()).isEqualByComparingTo("12.40");
                assertThat(event.category()).isEqualTo(ExpenseCategory.FUEL);
            });
    }

    @Test
    void correctionsAreKeptAndTheListShowsTheConfirmedValues() throws Exception {
        String id = uploadAndWait("receipt", "READ");

        MvcTestResult reviewed = review(id, """
            {"issuerAfm": "094014201", "issueDate": "2026-11-14", "netAmount": 20.00, "vatAmount": 4.80,
             "totalAmount": 24.80, "category": "TRANSPORT"}""");

        assertThat(reviewed).hasStatusOk();
        assertThat(reviewed).bodyJson().extractingPath("$.review.correctedFields").asArray()
            .containsExactly("netAmount", "vatAmount", "totalAmount", "category");
        MvcTestResult list = mvc.get().uri("/api/v1/businesses/{b}/documents", businessId).with(owner).exchange();
        assertThat(list).bodyJson().extractingPath("$.content[0].summary.category").isEqualTo("TRANSPORT");
        assertThat(list).bodyJson().extractingPath("$.content[0].summary.totalAmount").isEqualTo(24.80);
    }

    @Test
    void aSecondReviewReplacesTheFirst() throws Exception {
        String id = uploadAndWait("receipt", "READ");
        review(id, AS_READ.replace("FUEL", "TRANSPORT"));

        MvcTestResult again = review(id, AS_READ);

        assertThat(again).bodyJson().extractingPath("$.review.correctedFields").asArray().isEmpty();
        assertThat(again).bodyJson().extractingPath("$.review.category").isEqualTo("FUEL");
    }

    @Test
    void aDocumentThatCouldNotBeReadCanBeFilledInByHand() throws Exception {
        String id = uploadAndWait("unreadable", "FAILED");

        MvcTestResult reviewed = review(id, AS_READ);

        assertThat(reviewed).hasStatusOk();
        assertThat(reviewed).bodyJson().extractingPath("$.status").isEqualTo("REVIEWED");
        assertThat(reviewed).bodyJson().extractingPath("$.reading").isNull();
        assertThat(reviewed).bodyJson().extractingPath("$.review.correctedFields").asArray().isEmpty();
    }

    @Test
    void amountsThatDoNotAddUpAreRefused() throws Exception {
        String id = uploadAndWait("receipt", "READ");

        MvcTestResult reviewed = review(id, AS_READ.replace("12.40", "12.50"));

        assertThat(reviewed).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(reviewed).bodyJson().extractingPath("$.errors.balanced").isEqualTo("Net amount plus VAT must equal the total");
    }

    @Test
    void anInvalidAfmAndAFutureDateAreRefused() throws Exception {
        String id = uploadAndWait("receipt", "READ");

        MvcTestResult reviewed = review(id, AS_READ.replace("094014201", "123456789").replace("2026-11-14", "2999-01-01"));

        assertThat(reviewed).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(reviewed).bodyJson().extractingPath("$.errors.issuerAfm").isNotNull();
        assertThat(reviewed).bodyJson().extractingPath("$.errors.issueDate").isEqualTo("Issue date cannot be in the future");
    }

    @Test
    void anotherBusinessCannotReviewTheDocument() throws Exception {
        String id = uploadAndWait("receipt", "READ");

        MvcTestResult stranger = mvc.put().uri("/api/v1/businesses/{b}/documents/{d}/review", businessId, id)
            .with(businessUser()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(AS_READ).exchange();

        assertThat(stranger).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(details(id)).bodyJson().extractingPath("$.status").isEqualTo("READ");
    }

    @Test
    void reviewNeedsTheBusinessRole() throws Exception {
        String id = uploadAndWait("receipt", "READ");

        MvcTestResult anonymous = mvc.put().uri("/api/v1/businesses/{b}/documents/{d}/review", businessId, id)
            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(AS_READ).exchange();

        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
