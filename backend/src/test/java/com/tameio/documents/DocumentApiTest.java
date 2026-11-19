package com.tameio.documents;

import com.jayway.jsonpath.JsonPath;
import com.tameio.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.tameio.TestUsers.businessUser;
import static com.tameio.TestUsers.freshAfm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * The documents API end to end, with PostgreSQL and S3-compatible storage in containers: what is
 * uploaded is really stored and comes back byte for byte.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
class DocumentApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    ApplicationEvents events;

    private RequestPostProcessor owner;
    private String businessId;

    @BeforeEach
    void registerABusiness() throws Exception {
        owner = businessUser();
        MvcTestResult created = mvc.post().uri("/api/v1/businesses").with(owner).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"afm": "%s", "name": "Καφέ Το Στέκι"}""".formatted(freshAfm()))
            .exchange();
        businessId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    /** A small but genuine PDF header with unique content, so no two tests upload the same file. */
    private static byte[] pdf() {
        return ("%PDF-1.7\n% receipt " + UUID.randomUUID() + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
    }

    private MvcTestResult upload(RequestPostProcessor user, String filename, String claimedType, byte[] content) {
        return mvc.post().uri("/api/v1/businesses/{id}/documents", businessId).with(user).with(csrf())
            .multipart()
            .file(new MockMultipartFile("file", filename, claimedType, content))
            .exchange();
    }

    @Test
    void uploadsADocumentListsItAndReturnsTheSameBytes() throws Exception {
        byte[] content = pdf();

        MvcTestResult uploaded = upload(owner, "απόδειξη-μαρτίου.pdf", "application/pdf", content);

        assertThat(uploaded).hasStatus(HttpStatus.CREATED);
        assertThat(uploaded).bodyJson().extractingPath("$.filename").isEqualTo("απόδειξη-μαρτίου.pdf");
        assertThat(uploaded).bodyJson().extractingPath("$.status").isEqualTo("UPLOADED");
        String documentId = JsonPath.read(uploaded.getResponse().getContentAsString(), "$.id");

        assertThat(mvc.get().uri("/api/v1/businesses/{id}/documents", businessId).with(owner))
            .hasStatusOk()
            .bodyJson().satisfies(json -> {
                assertThat(json).extractingPath("$.content[*].id").asArray().containsExactly(documentId);
                assertThat(json).extractingPath("$.page.totalElements").isEqualTo(1);
            });

        MvcTestResult file = mvc.get().uri("/api/v1/businesses/{id}/documents/{doc}/content", businessId, documentId).with(owner).exchange();
        assertThat(file).hasStatusOk().hasContentType(MediaType.APPLICATION_PDF);
        assertThat(file.getResponse().getContentAsByteArray()).isEqualTo(content);
        ContentDisposition disposition = ContentDisposition.parse(file.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION));
        assertThat(disposition.isInline()).isTrue();
        assertThat(disposition.getFilename()).isEqualTo("απόδειξη-μαρτίου.pdf");
    }

    @Test
    void announcesEachUpload() throws Exception {
        MvcTestResult uploaded = upload(owner, "receipt.pdf", "application/pdf", pdf());
        String documentId = JsonPath.read(uploaded.getResponse().getContentAsString(), "$.id");

        assertThat(events.stream(DocumentUploaded.class))
            .containsExactly(new DocumentUploaded(UUID.fromString(documentId), UUID.fromString(businessId), "application/pdf"));
    }

    @Test
    void trustsTheFileContentNotTheNameOrClaimedType() {
        byte[] text = "Just text, renamed to look like a PDF".getBytes(StandardCharsets.US_ASCII);

        assertThat(upload(owner, "invoice.pdf", "application/pdf", text))
            .hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson().extractingPath("$.detail").isEqualTo("Upload a PDF, JPEG or PNG file.");
    }

    @Test
    void rejectsAnEmptyFile() {
        assertThat(upload(owner, "empty.pdf", "application/pdf", new byte[0])).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsTheSameFileTwice() {
        byte[] content = pdf();
        assertThat(upload(owner, "receipt.pdf", "application/pdf", content)).hasStatus(HttpStatus.CREATED);

        assertThat(upload(owner, "receipt-copy.pdf", "application/pdf", content))
            .hasStatus(HttpStatus.CONFLICT)
            .bodyJson().extractingPath("$.detail").isEqualTo("This document has already been uploaded.");
    }

    @Test
    void keepsOnlyTheFileNameFromAPath() {
        assertThat(upload(owner, "C:\\Users\\emma\\Desktop\\receipt.pdf", "application/pdf", pdf()))
            .hasStatus(HttpStatus.CREATED)
            .bodyJson().extractingPath("$.filename").isEqualTo("receipt.pdf");
    }

    @Test
    void anotherUsersDocumentsAreInvisible() throws Exception {
        MvcTestResult uploaded = upload(owner, "receipt.pdf", "application/pdf", pdf());
        String documentId = JsonPath.read(uploaded.getResponse().getContentAsString(), "$.id");
        RequestPostProcessor stranger = businessUser();

        assertThat(upload(stranger, "receipt.pdf", "application/pdf", pdf())).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/v1/businesses/{id}/documents", businessId).with(stranger)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/v1/businesses/{id}/documents/{doc}/content", businessId, documentId).with(stranger))
            .hasStatus(HttpStatus.NOT_FOUND);
    }
}
