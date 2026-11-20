package com.tameio.documents;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A document as the list shows it. Storage details such as the key and the hash stay on the server.
 *
 * @param summary the confirmed values once reviewed, before that what document AI read; null until then
 * @param failure why it could not be read, when it could not
 */
public record DocumentView(UUID id, String filename, String mediaType, long sizeBytes, DocumentStatus status,
                           Instant uploadedAt, Summary summary, ReadFailure failure) {

    /** The fields worth a column in a list. */
    public record Summary(LocalDate issueDate, String issuerAfm, BigDecimal totalAmount, String category) {
    }

    static DocumentView of(Document document) {
        Summary summary = switch (document.getStatus()) {
            case REVIEWED -> {
                Review r = document.getReview();
                yield new Summary(r.getIssueDate(), r.getIssuerAfm(), r.getTotalAmount(), r.getCategory().name());
            }
            case READ -> {
                Reading r = document.getReading();
                yield new Summary(r.getIssueDate(), r.getIssuerAfm(), r.getTotalAmount(), r.getSuggestedCategory());
            }
            default -> null;
        };
        return new DocumentView(document.getId(), document.getFilename(), document.getMediaType(),
            document.getSizeBytes(), document.getStatus(), document.getCreatedAt(), summary, document.getReadFailure());
    }
}
