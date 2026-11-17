package com.tameio.documents;

import com.tameio.platform.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** A file a business uploaded. The bytes live in object storage under {@link #storageKey}. */
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
}
