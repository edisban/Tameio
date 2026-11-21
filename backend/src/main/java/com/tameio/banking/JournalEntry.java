package com.tameio.banking;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One movement of money, with the postings that make it up. Never changed once booked. */
@Entity
@Immutable
@Table(name = "journal_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class JournalEntry {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EntryKind kind;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(length = 140)
    private String reference;

    @Column(name = "initiated_by", nullable = false, length = 64)
    private String initiatedBy;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "booked_at", nullable = false)
    private Instant bookedAt;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.PERSIST)
    @OrderBy("position")
    private List<Posting> postings = new ArrayList<>();

    static JournalEntry of(EntryKind kind, String description, String reference, String initiatedBy,
                           Ledger.Idempotency idempotency, Instant bookedAt) {
        var entry = new JournalEntry();
        entry.kind = kind;
        entry.description = description;
        entry.reference = reference;
        entry.initiatedBy = initiatedBy;
        if (idempotency != null) {
            entry.idempotencyKey = idempotency.key();
            entry.requestHash = idempotency.requestHash();
        }
        entry.bookedAt = bookedAt;
        return entry;
    }

    void add(Posting posting) {
        postings.add(posting);
    }
}
