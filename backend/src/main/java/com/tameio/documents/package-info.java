/**
 * Documents a business uploads (receipts, invoices, statements) and their processing status. Files are
 * kept in object storage; this module keeps what is known about them.
 *
 * <p>Each upload publishes {@link com.tameio.documents.DocumentUploaded}. The event publication
 * registry stores it with the upload (transactional outbox) and sends it to RabbitMQ after commit;
 * {@code DocumentReader} takes it from the queue and has document AI read the file, with retries and a
 * dead-letter queue. Other modules react to these events rather than calling in here.
 */
@ApplicationModule(displayName = "Documents")
package com.tameio.documents;

import org.springframework.modulith.ApplicationModule;
