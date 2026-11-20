package com.tameio.documents;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A document as the list shows it. Storage details such as the key and the hash stay on the server.
 *
 * @param summary what document AI read, once it has; null before
 * @param failure why it could not be read, when it could not
 */
public record DocumentView(UUID id, String filename, String mediaType, long sizeBytes, DocumentStatus status,
                           Instant uploadedAt, Summary summary, ReadFailure failure) {

    /** The fields worth a column in a list. */
    public record Summary(LocalDate issueDate, String issuerAfm, BigDecimal totalAmount, String category) {
    }

    static DocumentView of(Document document) {
        Reading reading = document.getStatus() == DocumentStatus.READ ? document.getReading() : null;
        Summary summary = reading == null ? null
            : new Summary(reading.getIssueDate(), reading.getIssuerAfm(), reading.getTotalAmount(), reading.getSuggestedCategory());
        return new DocumentView(document.getId(), document.getFilename(), document.getMediaType(),
            document.getSizeBytes(), document.getStatus(), document.getCreatedAt(), summary, document.getReadFailure());
    }
}
