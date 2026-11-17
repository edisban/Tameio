package com.tameio.documents;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The file types accepted for upload, recognised by their first bytes. The type the browser claims is
 * ignored: it comes from the file name, and anyone can rename a file.
 */
enum FileType {

    PDF("application/pdf", new byte[] {'%', 'P', 'D', 'F', '-'}),
    JPEG("image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});

    private final String mediaType;
    private final byte[] signature;

    FileType(String mediaType, byte[] signature) {
        this.mediaType = mediaType;
        this.signature = signature;
    }

    String mediaType() {
        return mediaType;
    }

    /** A file name extension for files uploaded without a usable name. */
    String extension() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<FileType> detect(byte[] content) {
        return Arrays.stream(values()).filter(type -> type.matches(content)).findFirst();
    }

    private boolean matches(byte[] content) {
        return content.length >= signature.length
            && Arrays.equals(content, 0, signature.length, signature, 0, signature.length);
    }
}
