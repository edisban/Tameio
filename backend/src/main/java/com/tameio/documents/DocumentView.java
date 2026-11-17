package com.tameio.documents;

import java.time.Instant;
import java.util.UUID;

/** A document as the API shows it. Storage details such as the key and the hash stay on the server. */
public record DocumentView(UUID id, String filename, String mediaType, long sizeBytes, DocumentStatus status, Instant uploadedAt) {

    static DocumentView of(Document document) {
        return new DocumentView(document.getId(), document.getFilename(), document.getMediaType(),
            document.getSizeBytes(), document.getStatus(), document.getCreatedAt());
    }
}
