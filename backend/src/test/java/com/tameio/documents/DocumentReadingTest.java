package com.tameio.documents;

import com.jayway.jsonpath.JsonPath;
import com.tameio.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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

/**
 * The whole reading pipeline with a real PostgreSQL, RabbitMQ and S3 storage: upload, outbox, queue,
 * listener, document AI (scripted), retries and the dead-letter queue.
 */
@SpringBootTest(properties = {
    // Fast retries so a test that exhausts them takes milliseconds, not seconds
    "spring.rabbitmq.listener.simple.retry.initial-interval=20ms",
    "spring.rabbitmq.listener.simple.retry.multiplier=1",
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ScriptedDocumentAi.class})
class DocumentReadingTest {

    private static final Duration PATIENCE = Duration.ofSeconds(30);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    ScriptedDocumentAi ai;

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    DocumentReader reader;

    private RequestPostProcessor owner;
    private String businessId;

    @BeforeEach
    void registerABusiness() throws Exception {
        owner = businessUser();
        MvcTestResult created = mvc.post().uri("/api/v1/businesses").with(owner).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"afm": "%s", "name": "Πρατήριο Δοκιμής"}""".formatted(freshAfm()))
            .exchange();
        businessId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    /** A PDF whose content tells the scripted document AI how to answer. */
    private static byte[] pdf(String behaviour) {
        return ("%PDF-1.7\n% " + behaviour + " " + UUID.randomUUID() + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
    }

    private String upload(byte[] content) throws Exception {
        MvcTestResult uploaded = mvc.post().uri("/api/v1/businesses/{id}/documents", businessId).with(owner).with(csrf())
            .multipart()
            .file(new MockMultipartFile("file", "απόδειξη.pdf", "application/pdf", content))
            .exchange();
        assertThat(uploaded).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(uploaded.getResponse().getContentAsString(), "$.id");
    }

    private MvcTestResult details(String documentId) {
        return mvc.get().uri("/api/v1/businesses/{b}/documents/{d}", businessId, documentId).with(owner).exchange();
    }

    private String statusOf(String documentId) throws Exception {
        return JsonPath.read(details(documentId).getResponse().getContentAsString(), "$.status");
    }

    private void awaitStatus(String documentId, String status) {
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(statusOf(documentId)).isEqualTo(status));
    }

    @Test
    void readsAnUploadAndShowsTheFieldsChecksAndCategory() throws Exception {
        String id = upload(pdf("receipt"));

        awaitStatus(id, "READ");

        MvcTestResult result = details(id);
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.reading.issuerAfm.value").isEqualTo(ScriptedDocumentAi.ISSUER_AFM);
        assertThat(result).bodyJson().extractingPath("$.reading.issuerAfm.confidence").isEqualTo(0.98);
        assertThat(result).bodyJson().extractingPath("$.reading.issueDate.value").isEqualTo("2026-11-14");
        assertThat(result).bodyJson().extractingPath("$.reading.totalAmount.value").isEqualTo(12.40);
        assertThat(result).bodyJson().extractingPath("$.reading.category.code").isEqualTo("FUEL");
        assertThat(result).bodyJson().extractingPath("$.reading.checks[1].name").isEqualTo("totals_add_up");
        assertThat(result).bodyJson().extractingPath("$.reading.checks[1].passed").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.reading.text").asString().contains("ΠΡΑΤΗΡΙΟ ΑΙΓΑΙΟΥ");
        assertThat(result).bodyJson().extractingPath("$.readAttempts").isEqualTo(1);

        MvcTestResult list = mvc.get().uri("/api/v1/businesses/{b}/documents", businessId).with(owner).exchange();
        assertThat(list).bodyJson().extractingPath("$.content[0].summary.totalAmount").isEqualTo(12.40);
        assertThat(list).bodyJson().extractingPath("$.content[0].summary.category").isEqualTo("FUEL");
    }

    @Test
    void retriesWhenDocumentAiDoesNotAnswerTheFirstTime() throws Exception {
        byte[] content = pdf("flaky");
        String id = upload(content);

        awaitStatus(id, "READ");

        assertThat(ai.callsFor(content)).isEqualTo(2);
        assertThat(details(id)).bodyJson().extractingPath("$.readAttempts").isEqualTo(2);
    }

    @Test
    void marksADocumentDocumentAiCannotReadAsFailedWithoutRetrying() throws Exception {
        byte[] content = pdf("unreadable");
        String id = upload(content);

        awaitStatus(id, "FAILED");

        assertThat(details(id)).bodyJson().extractingPath("$.failure").isEqualTo("UNREADABLE");
        assertThat(details(id)).bodyJson().extractingPath("$.failureDetail").isEqualTo("No text found in the document.");
        assertThat(ai.callsFor(content)).isEqualTo(1);
    }

    @Test
    void givesUpAfterTheLastRetryAndKeepsTheJobInTheDeadLetterQueue() throws Exception {
        byte[] content = pdf("down");
        String id = upload(content);

        awaitStatus(id, "FAILED");

        assertThat(details(id)).bodyJson().extractingPath("$.failure").isEqualTo("GAVE_UP");
        assertThat(ai.callsFor(content)).isEqualTo(3);
        // Other tests dead-letter their own jobs too, so look for this document's one
        await().atMost(PATIENCE).until(() -> {
            Message message;
            while ((message = rabbit.receive(DocumentMessaging.DEAD_LETTER_QUEUE)) != null) {
                if (new String(message.getBody(), StandardCharsets.UTF_8).contains(id)) {
                    return true;
                }
            }
            return false;
        });
    }

    @Test
    void readsAFailedDocumentAgainWhenAsked() throws Exception {
        String id = upload(pdf("unreadable-once"));
        awaitStatus(id, "FAILED");

        MvcTestResult again = mvc.post().uri("/api/v1/businesses/{b}/documents/{d}/read", businessId, id)
            .with(owner).with(csrf()).exchange();

        assertThat(again).hasStatus(HttpStatus.ACCEPTED);
        assertThat(again).bodyJson().extractingPath("$.status").isEqualTo("UPLOADED");
        awaitStatus(id, "READ");
        assertThat(details(id)).bodyJson().extractingPath("$.failure").isNull();
    }

    @Test
    void refusesToReadAgainADocumentThatWasRead() throws Exception {
        String id = upload(pdf("receipt"));
        awaitStatus(id, "READ");

        MvcTestResult again = mvc.post().uri("/api/v1/businesses/{b}/documents/{d}/read", businessId, id)
            .with(owner).with(csrf()).exchange();

        assertThat(again).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void aDuplicateMessageDoesNotReadTheDocumentTwice() throws Exception {
        byte[] content = pdf("receipt");
        String id = upload(content);
        awaitStatus(id, "READ");

        // RabbitMQ delivers at least once; a second delivery of the same job must change nothing
        reader.read(UUID.fromString(id));

        assertThat(ai.callsFor(content)).isEqualTo(1);
        assertThat(details(id)).bodyJson().extractingPath("$.readAttempts").isEqualTo(1);
    }

    @Test
    void showsNothingOfAnotherBusinesssDocument() throws Exception {
        String id = upload(pdf("receipt"));

        MvcTestResult stranger = mvc.get().uri("/api/v1/businesses/{b}/documents/{d}", businessId, id)
            .with(businessUser()).exchange();

        assertThat(stranger).hasStatus(HttpStatus.NOT_FOUND);
    }
}
