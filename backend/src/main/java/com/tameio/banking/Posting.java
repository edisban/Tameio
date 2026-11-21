package com.tameio.banking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One leg of a journal entry: a signed amount on one account, and the balance it left. */
@Entity
@Immutable
@Table(name = "posting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Posting {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry entry;

    @Column(nullable = false)
    private int position;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "counterparty_name", length = 140)
    private String counterpartyName;

    @Column(name = "counterparty_iban", length = 34)
    private String counterpartyIban;

    @Column(name = "booked_at", nullable = false)
    private Instant bookedAt;

    static Posting of(JournalEntry entry, int position, UUID accountId, BigDecimal amount, BigDecimal balanceAfter,
                      String counterpartyName, String counterpartyIban) {
        var posting = new Posting();
        posting.entry = entry;
        posting.position = position;
        posting.accountId = accountId;
        posting.amount = amount;
        posting.balanceAfter = balanceAfter;
        posting.counterpartyName = counterpartyName;
        posting.counterpartyIban = counterpartyIban;
        posting.bookedAt = entry.getBookedAt();
        return posting;
    }
}
