package com.tameio.documents;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The document's life cycle rules, without a database. */
class DocumentTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    private static Document uploaded() {
        return Document.uploaded(UUID.randomUUID(), "receipt.pdf", FileType.PDF, 100, "hash", "key");
    }

    private static ReviewRequest request(String net, String vat, String total, ExpenseCategory category) {
        return new ReviewRequest("094014201", LocalDate.of(2026, 11, 14), new BigDecimal(net), new BigDecimal(vat),
            new BigDecimal(total), category);
    }

    @Test
    void goesFromUploadedThroughReadingToRead() {
        Document document = uploaded();
        assertThat(document.awaitsReading()).isTrue();

        document.startReading();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.READING);
        assertThat(document.awaitsReading()).as("a retry after a crash").isTrue();

        document.completeReading(ScriptedDocumentAi.RECEIPT, NOW);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.READ);
        assertThat(document.awaitsReading()).isFalse();
        assertThat(document.getReading().getTotalAmount()).isEqualByComparingTo("12.40");
        assertThat(document.getChecks()).hasSize(2);
    }

    @Test
    void cannotBeReviewedWhileStillBeingRead() {
        Document document = uploaded();
        document.startReading();

        assertThat(document.canBeReviewed()).isFalse();
        assertThatThrownBy(() -> document.review(request("10.00", "2.40", "12.40", ExpenseCategory.FUEL), "u", NOW))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void amountsWithADifferentScaleAreNotACorrection() {
        Document document = uploaded();
        document.startReading();
        document.completeReading(ScriptedDocumentAi.RECEIPT, NOW);

        document.review(request("10", "2.4", "12.4", ExpenseCategory.FUEL), "u", NOW);

        assertThat(document.getCorrections()).isEmpty();
    }

    @Test
    void recordsWhatWasSuggestedAndWhatWasCorrected() {
        Document document = uploaded();
        document.startReading();
        document.completeReading(ScriptedDocumentAi.RECEIPT, NOW);

        document.review(request("10.00", "2.40", "12.40", ExpenseCategory.TRANSPORT), "u", NOW);

        assertThat(document.getCorrections()).singleElement().satisfies(correction -> {
            assertThat(correction.field()).isEqualTo("category");
            assertThat(correction.suggested()).isEqualTo("FUEL");
            assertThat(correction.corrected()).isEqualTo("TRANSPORT");
            assertThat(correction.modelVersion()).isEqualTo("expense-classifier@test");
        });
    }

    @Test
    void onlyAFailedDocumentCanBeReadAgain() {
        Document document = uploaded();
        assertThatThrownBy(document::readAgain).isInstanceOf(IllegalStateException.class);

        document.startReading();
        document.failReading(ReadFailure.GAVE_UP, "x".repeat(600));
        assertThat(document.getReadFailureDetail()).hasSize(500);

        document.readAgain();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(document.getReadFailure()).isNull();
    }

    @Test
    void reviewRequestChecksThatTheAmountsAddUp() {
        assertThat(request("10.00", "2.40", "12.40", ExpenseCategory.FUEL).isBalanced()).isTrue();
        assertThat(request("10.00", "2.40", "12.41", ExpenseCategory.FUEL).isBalanced()).isFalse();
        assertThat(new ReviewRequest(" 094 014 201 ", null, null, null, null, null).issuerAfm()).isEqualTo("094014201");
    }
}
