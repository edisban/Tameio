package com.tameio.documents;

import java.util.UUID;

/** Published once a document is stored and recorded. Document AI will start from this event. */
public record DocumentUploaded(UUID documentId, UUID businessId, String mediaType) {
}
