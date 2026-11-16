package com.tameio.platform.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * What every table row carries:
 * <ul>
 *   <li>a UUID id, time-ordered (v7) so new rows still land at the end of the primary-key index;</li>
 *   <li>a version for optimistic locking, so two people editing the same row cannot silently overwrite
 *       each other;</li>
 *   <li>who created it and when, and when it last changed, filled in by Spring Data auditing.</li>
 * </ul>
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditedEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Version
    private long version;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(nullable = false, updatable = false, length = 64)
    private String createdBy;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
