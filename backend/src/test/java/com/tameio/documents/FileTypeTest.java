package com.tameio.documents;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FileTypeTest {

    @Test
    void recognisesAPdfByItsHeader() {
        assertThat(FileType.detect("%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII))).contains(FileType.PDF);
    }

    @Test
    void recognisesAJpegByItsHeader() {
        assertThat(FileType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10})).contains(FileType.JPEG);
    }

    @Test
    void recognisesAPngByItsHeader() {
        assertThat(FileType.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0})).contains(FileType.PNG);
    }

    @Test
    void rejectsATextFileEvenIfItIsNamedLikeAPdf() {
        assertThat(FileType.detect("Hello, this is not a PDF".getBytes(StandardCharsets.US_ASCII))).isEmpty();
    }

    @Test
    void rejectsContentShorterThanAnyHeader() {
        assertThat(FileType.detect(new byte[] {'%', 'P'})).isEmpty();
        assertThat(FileType.detect(new byte[0])).isEmpty();
    }
}
