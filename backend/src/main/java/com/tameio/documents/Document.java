package com.tameio.documents;

import com.tameio.platform.persistence.AuditedEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A file a business uploaded, and what document AI read from it. The bytes live in object storage
 * under {@link #storageKey}.
 */
@Entity
@Table(name = "document")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Document extends AuditedEntity {

    // Referenced by id, not as an entity: the identity module owns businesses
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "media_type", nullable = false, length = 100)
    private String mediaType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "storage_key", nullable = false, unique = true, length = 300)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "read_attempts", nullable = false)
    private int readAttempts;

    @Enumerated(EnumType.STRING)
    @Column(name = "read_failure", length = 20)
    private ReadFailure readFailure;

    @Column(name = "read_failure_detail", length = 500)
    private String readFailureDetail;

    /** Null until the document is read. */
    @Embedded
    private Reading reading;

    @ElementCollection
    @CollectionTable(name = "document_check", joinColumns = @JoinColumn(name = "document_id"))
    @OrderColumn(name = "position")
    private List<ReadingCheck> checks = new ArrayList<>();

    static Document uploaded(UUID businessId, String filename, FileType type, long sizeBytes, String sha256, String storageKey) {
        var document = new Document();
        document.businessId = businessId;
        document.filename = filename;
        document.mediaType = type.mediaType();
        document.sizeBytes = sizeBytes;
        document.sha256 = sha256;
        document.storageKey = storageKey;
        document.status = DocumentStatus.UPLOADED;
        return document;
    }

    /** A reading job for this document should do its work: it is waiting, or a previous attempt did not finish. */
    boolean awaitsReading() {
        return status == DocumentStatus.UPLOADED || status == DocumentStatus.READING;
    }

    void startReading() {
        status = DocumentStatus.READING;
        readAttempts++;
    }

    void completeReading(DocumentAi.Result result, Instant readAt) {
        status = DocumentStatus.READ;
        reading = Reading.of(result, readAt);
        checks.clear();
        result.checks().stream().map(ReadingCheck::of).forEach(checks::add);
        readFailure = null;
        readFailureDetail = null;
    }

    void failReading(ReadFailure failure, String detail) {
        status = DocumentStatus.FAILED;
        readFailure = failure;
        readFailureDetail = detail == null || detail.length() <= 500 ? detail : detail.substring(0, 500);
    }

    /** Puts a failed document back in the queue. */
    void readAgain() {
        if (status != DocumentStatus.FAILED) {
            throw new IllegalStateException("Only a document that failed to be read can be read again");
        }
        status = DocumentStatus.UPLOADED;
        readFailure = null;
        readFailureDetail = null;
    }
}
