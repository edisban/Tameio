package com.tameio.banking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

interface PostingRepository extends JpaRepository<Posting, UUID> {

    /** One line of a statement: a posting and the entry it belongs to. */
    record Line(UUID id, UUID entryId, EntryKind kind, String description, String reference, BigDecimal amount,
                BigDecimal balanceAfter, String counterpartyName, String counterpartyIban, Instant bookedAt) {
    }

    @Query(value = """
        select new com.tameio.banking.PostingRepository$Line(p.id, e.id, e.kind, e.description, e.reference, p.amount,
            p.balanceAfter, p.counterpartyName, p.counterpartyIban, p.bookedAt)
        from Posting p join p.entry e
        where p.accountId = :accountId
        order by p.bookedAt desc, p.id desc""",
        countQuery = "select count(p) from Posting p where p.accountId = :accountId")
    Page<Line> statement(UUID accountId, Pageable pageable);

    @Query("select count(distinct p.entry.id) from Posting p")
    long countEntries();
}
