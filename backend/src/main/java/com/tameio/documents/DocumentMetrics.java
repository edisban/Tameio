package com.tameio.documents;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Document figures for the Grafana dashboard, counted in the database at each scrape. Every instance
 * reports the same numbers, so the dashboard takes the maximum across instances, not the sum.
 */
@Component
@RequiredArgsConstructor
class DocumentMetrics implements MeterBinder {

    /** Reading takes seconds; this long in READING means the job was lost or document AI hangs. */
    static final Duration STUCK_AFTER = Duration.ofMinutes(5);

    private final DocumentRepository documents;
    private final Clock clock;

    @Override
    public void bindTo(MeterRegistry registry) {
        for (DocumentStatus status : DocumentStatus.values()) {
            Gauge.builder("tameio.documents", documents, repository -> repository.countByStatus(status))
                .tag("status", status.name())
                .description("Documents by status")
                .register(registry);
        }
        Gauge.builder("tameio.documents.stuck.reading", documents,
                repository -> repository.countByStatusAndUpdatedAtBefore(DocumentStatus.READING, clock.instant().minus(STUCK_AFTER)))
            .description("Documents in READING for more than five minutes")
            .register(registry);
    }
}
