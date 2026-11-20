package com.tameio.documents;

import com.tameio.platform.storage.FileStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads each document with document AI, one RabbitMQ message at a time.
 *
 * <p>The slow part, the HTTP call, runs outside any database transaction, so a long OCR never holds a
 * connection from the pool. The status changes before and after it each commit on their own.
 *
 * <p>A message can arrive more than once (RabbitMQ delivers at least once). A document that is already
 * read or has failed for good is skipped, so a duplicate does no harm.
 *
 * <p>When document AI is unavailable the exception leaves this listener and Spring AMQP retries with
 * back-off ({@code spring.rabbitmq.listener.simple.retry}). After the last attempt the recoverer in
 * {@link DocumentMessaging} calls {@link #giveUp} and the message goes to the dead-letter queue.
 */
@Slf4j
@Component
class DocumentReader {

    /** The part of {@link DocumentUploaded} and {@link DocumentReadRequested} a reading job needs. */
    record ReadJob(UUID documentId) {
    }

    private record Work(String storageKey, String mediaType, int attempt) {
    }

    private final DocumentRepository documents;
    private final FileStore files;
    private final DocumentAi ai;
    private final TransactionTemplate transaction;
    private final Clock clock;

    DocumentReader(DocumentRepository documents, FileStore files, DocumentAi ai, PlatformTransactionManager transactions) {
        this.documents = documents;
        this.files = files;
        this.ai = ai;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = Clock.systemUTC();
    }

    @RabbitListener(id = "document-reader", queues = DocumentMessaging.READ_QUEUE)
    void onReadJob(ReadJob job) {
        read(job.documentId());
    }

    void read(UUID documentId) {
        Optional<Work> work = transaction.execute(status -> documents.findById(documentId)
            .filter(Document::awaitsReading)
            .map(document -> {
                document.startReading();
                return new Work(document.getStorageKey(), document.getMediaType(), document.getReadAttempts());
            }));
        if (work == null || work.isEmpty()) {
            log.info("Skipped reading document {}: it is gone, already read or failed", documentId);
            return;
        }

        byte[] content = download(work.get().storageKey());
        try {
            DocumentAi.Result result = ai.read(content, work.get().mediaType());
            transaction.executeWithoutResult(status -> documents.findById(documentId)
                .ifPresent(document -> document.completeReading(result, clock.instant())));
            log.info("Read document {} in {} ms, attempt {}", documentId, result.durationMs(), work.get().attempt());
        } catch (DocumentAi.Unreadable e) {
            // A definite answer: retrying would get the same one, so the job ends here
            fail(documentId, e.failure(), e.getMessage());
            log.info("Document {} cannot be read: {}", documentId, e.getMessage());
        }
    }

    /** Called after the last retry failed. */
    void giveUp(UUID documentId, Throwable cause) {
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        log.warn("Gave up reading document {}: {}", documentId, root.getMessage());
        fail(documentId, ReadFailure.GAVE_UP, root.getMessage());
    }

    private void fail(UUID documentId, ReadFailure failure, String detail) {
        transaction.executeWithoutResult(status -> documents.findById(documentId)
            .filter(Document::awaitsReading)
            .ifPresent(document -> document.failReading(failure, detail)));
    }

    private byte[] download(String storageKey) {
        try (InputStream stream = files.open(storageKey)) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the stored file", e);
        }
    }
}
