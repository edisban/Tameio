package com.tameio.documents;

import java.io.InputStream;

/** A document's details together with an open stream of its bytes. Whoever receives it closes the stream. */
record DocumentContent(DocumentView document, InputStream stream) {
}
