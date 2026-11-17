package com.tameio.platform.storage;

import java.io.InputStream;

/**
 * Where uploaded files live. Modules depend on this interface, never on S3 directly, so tests and other
 * storage backends can stand in for it.
 */
public interface FileStore {

    void put(String key, byte[] content, String mediaType);

    /** Opens the file for reading. The caller must close the stream. */
    InputStream open(String key);

    void delete(String key);
}
