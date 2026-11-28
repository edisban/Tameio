package com.tameio.banking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

    /** Money in and out of an account since a date, leaving out loans paid into it. */
    record Flows(BigDecimal inflow, BigDecimal outflow) {
    }

    @Query("""
        select new com.tameio.banking.PostingRepository$Flows(
            coalesce(sum(case when p.amount > 0 then p.amount else 0 end), 0),
            coalesce(sum(case when p.amount < 0 then -p.amount else 0 end), 0))
        from Posting p join p.entry e
        where p.accountId = :accountId and p.bookedAt >= :since
          and e.kind <> com.tameio.banking.EntryKind.LOAN_DISBURSEMENT""")
    Flows flowsSince(UUID accountId, Instant since);

    /** One movement's amount and time, for working out money in and out month by month. */
    record Movement(BigDecimal amount, Instant bookedAt) {
    }

    @Query("""
        select new com.tameio.banking.PostingRepository$Movement(p.amount, p.bookedAt)
        from Posting p join p.entry e
        where p.accountId = :accountId and p.bookedAt >= :since
          and e.kind <> com.tameio.banking.EntryKind.LOAN_DISBURSEMENT""")
    List<Movement> movementsSince(UUID accountId, Instant since);

    @Query("select min(p.bookedAt) from Posting p where p.accountId = :accountId")
    Optional<Instant> firstBooking(UUID accountId);

    @Query("select count(distinct p.entry.id) from Posting p")
    long countEntries();
}
