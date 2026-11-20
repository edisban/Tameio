package com.tameio.documents;

import org.springframework.modulith.events.Externalized;

import java.util.UUID;

/** Someone asked for a document that failed to be read to be read again. Sent the same way as {@link DocumentUploaded}. */
@Externalized(DocumentMessaging.EXCHANGE + "::" + DocumentMessaging.READ_REQUESTED_KEY)
public record DocumentReadRequested(UUID documentId, UUID businessId) {
}
