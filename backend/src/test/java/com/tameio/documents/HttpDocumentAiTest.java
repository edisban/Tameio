package com.tameio.documents;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The HTTP client against the JSON document AI really sends, and each kind of failure. */
class HttpDocumentAiTest {

    private static final byte[] PDF = "%PDF-1.7".getBytes();

    private MockRestServiceServer server;
    private HttpDocumentAi client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://document-ai");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpDocumentAi(builder, JsonMapper.builder().build());
    }

    @Test
    void sendsTheFileAndReadsTheAnswer() {
        server.expect(requestTo("http://document-ai/v1/read"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(content().bytes(PDF))
            .andRespond(withSuccess("""
                {"text": "ΣΥΝΟΛΟ 12,40", "pages": 1, "method": "ocr",
                 "fields": {"issuerAfm": {"value": "094014201", "confidence": 0.98},
                            "issueDate": {"value": "2026-11-14", "confidence": 0.9},
                            "netAmount": null, "vatAmount": null,
                            "totalAmount": {"value": "12.40", "confidence": 0.97}},
                 "checks": [{"name": "afm_check_digit", "passed": true, "detail": "ok"}],
                 "category": {"best": {"category": "FUEL", "confidence": 0.91},
                              "alternatives": [{"category": "TRANSPORT", "confidence": 0.05}],
                              "modelVersion": "expense-classifier@1"},
                 "durationMs": 830}""", MediaType.APPLICATION_JSON));

        DocumentAi.Result result = client.read(PDF, "application/pdf");

        assertThat(result.fields().issuerAfm().value()).isEqualTo("094014201");
        assertThat(result.fields().issueDate().value()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(result.fields().netAmount()).isNull();
        assertThat(new BigDecimal(result.fields().totalAmount().value())).isEqualByComparingTo("12.40");
        assertThat(result.category().best().category()).isEqualTo("FUEL");
        assertThat(result.checks()).hasSize(1);
        assertThat(result.durationMs()).isEqualTo(830);
        server.verify();
    }

    @Test
    void anUnreadableDocumentIsADefiniteAnswer() {
        server.expect(requestTo("http://document-ai/v1/read"))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("""
                    {"status": 422, "title": "Unreadable document", "detail": "No text found in the document."}"""));

        assertThatThrownBy(() -> client.read(PDF, "application/pdf"))
            .isInstanceOfSatisfying(DocumentAi.Unreadable.class, e -> {
                assertThat(e.failure()).isEqualTo(ReadFailure.UNREADABLE);
                assertThat(e.getMessage()).isEqualTo("No text found in the document.");
            });
    }

    @Test
    void anotherRefusalIsARejection() {
        server.expect(requestTo("http://document-ai/v1/read"))
            .andRespond(withStatus(HttpStatus.CONTENT_TOO_LARGE).body("not json"));

        assertThatThrownBy(() -> client.read(PDF, "application/pdf"))
            .isInstanceOfSatisfying(DocumentAi.Unreadable.class, e -> {
                assertThat(e.failure()).isEqualTo(ReadFailure.REJECTED);
                assertThat(e.getMessage()).isEqualTo("Document AI refused the file.");
            });
    }

    @Test
    void aServerErrorMayPassSoItIsUnavailable() {
        server.expect(requestTo("http://document-ai/v1/read")).andRespond(withServerError());

        assertThatThrownBy(() -> client.read(PDF, "application/pdf")).isInstanceOf(DocumentAi.Unavailable.class);
    }

    @Test
    void aServiceThatCannotBeReachedIsUnavailable() {
        // Port 9 (discard) is closed on any normal machine, so the connection is refused at once
        var unreachable = new HttpDocumentAi(RestClient.builder().baseUrl("http://127.0.0.1:9"), JsonMapper.builder().build());

        assertThatThrownBy(() -> unreachable.read(PDF, "application/pdf"))
            .isInstanceOf(DocumentAi.Unavailable.class)
            .hasMessageContaining("could not be reached");
    }

    @Test
    void anAnswerThatIsNotJsonIsUnavailable() {
        server.expect(requestTo("http://document-ai/v1/read")).andRespond(withSuccess("<html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> client.read(PDF, "application/pdf")).isInstanceOf(DocumentAi.Unavailable.class);
    }
}
