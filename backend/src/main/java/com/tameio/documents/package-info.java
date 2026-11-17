/**
 * Documents a business uploads (receipts, invoices, statements) and their processing status. Files are
 * kept in object storage; this module keeps what is known about them.
 *
 * <p>Other modules react to {@link com.tameio.documents.DocumentUploaded} rather than calling in here;
 * document AI will start from that event.
 */
@ApplicationModule(displayName = "Documents")
package com.tameio.documents;

import org.springframework.modulith.ApplicationModule;
