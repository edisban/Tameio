package com.tameio.documents;

import org.springframework.modulith.events.Externalized;

import java.util.UUID;

/**
 * Published once a document is stored and recorded. It is saved with the upload's transaction (the
 * event publication registry is the outbox) and sent to RabbitMQ only after that transaction commits,
 * so a reading job is never lost and never sent for an upload that was rolled back.
 */
@Externalized(DocumentMessaging.EXCHANGE + "::" + DocumentMessaging.UPLOADED_KEY)
public record DocumentUploaded(UUID documentId, UUID businessId, String mediaType) {
}
