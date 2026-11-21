package com.tameio.documents;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentMetricsTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    @Test
    void aDocumentCountsAsStuckOnlyOnceItHasBeenInReadingForFiveMinutes() {
        DocumentRepository documents = mock(DocumentRepository.class);
        when(documents.countByStatusAndUpdatedAtBefore(DocumentStatus.READING, Instant.parse("2026-11-21T09:55:00Z"))).thenReturn(2L);
        MeterRegistry registry = new SimpleMeterRegistry();

        new DocumentMetrics(documents, Clock.fixed(NOW, ZoneOffset.UTC)).bindTo(registry);

        assertThat(registry.get("tameio.documents.stuck.reading").gauge().value()).isEqualTo(2);
    }
}
