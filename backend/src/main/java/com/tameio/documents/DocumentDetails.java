package com.tameio.documents;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything known about one document: what document AI read, how sure it was of each field, and the
 * checks it ran.
 *
 * @param reading null until the document is read
 */
public record DocumentDetails(UUID id, String filename, String mediaType, long sizeBytes, DocumentStatus status,
                              Instant uploadedAt, int readAttempts, ReadFailure failure, String failureDetail,
                              ReadingDetails reading) {

    public record Found<T>(T value, double confidence) {
    }

    public record Check(String name, boolean passed, String detail) {
    }

    public record Category(String code, double confidence, String modelVersion) {
    }

    public record ReadingDetails(Instant readAt, Found<String> issuerAfm, Found<LocalDate> issueDate,
                                 Found<BigDecimal> netAmount, Found<BigDecimal> vatAmount, Found<BigDecimal> totalAmount,
                                 Category category, List<Check> checks, int pages, String method, String text) {
    }

    static DocumentDetails of(Document document) {
        Reading r = document.getStatus() == DocumentStatus.READ ? document.getReading() : null;
        ReadingDetails reading = r == null ? null : new ReadingDetails(
            r.getReadAt(),
            found(r.getIssuerAfm(), r.getIssuerAfmConfidence()),
            found(r.getIssueDate(), r.getIssueDateConfidence()),
            found(r.getNetAmount(), r.getNetAmountConfidence()),
            found(r.getVatAmount(), r.getVatAmountConfidence()),
            found(r.getTotalAmount(), r.getTotalAmountConfidence()),
            new Category(r.getSuggestedCategory(), orZero(r.getSuggestedCategoryConfidence()), r.getModelVersion()),
            document.getChecks().stream().map(c -> new Check(c.name(), c.passed(), c.detail())).toList(),
            r.getPageCount() == null ? 0 : r.getPageCount(),
            r.getOcrMethod(),
            r.getText());
        return new DocumentDetails(document.getId(), document.getFilename(), document.getMediaType(), document.getSizeBytes(),
            document.getStatus(), document.getCreatedAt(), document.getReadAttempts(), document.getReadFailure(),
            document.getReadFailureDetail(), reading);
    }

    private static <T> Found<T> found(T value, Double confidence) {
        return value == null ? null : new Found<>(value, orZero(confidence));
    }

    private static double orZero(Double value) {
        return value == null ? 0 : value;
    }
}
