package com.tameio.banking;

import com.tameio.platform.web.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The only way money moves. Books a journal entry whose legs add up to zero, after locking every account
 * involved (in id order, so concurrent entries never deadlock) and applying each leg to its balance.
 * Runs inside the caller's transaction: the entry, the postings and the new balances commit together or
 * not at all.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class Ledger {

    /** One side of an entry: a signed amount on an account, and who is on the other side. */
    record Leg(UUID accountId, BigDecimal amount, String counterpartyName, String counterpartyIban) {
    }

    private final BankAccountRepository accounts;
    private final JournalEntryRepository entries;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    JournalEntry book(EntryKind kind, String description, String reference, String initiatedBy, List<Leg> legs) {
        return book(kind, description, reference, initiatedBy, legs, null);
    }

    /** As {@link #book}, dated in the past (only for loading a year of demo history); null means now. */
    @Transactional(propagation = Propagation.MANDATORY)
    JournalEntry book(EntryKind kind, String description, String reference, String initiatedBy, List<Leg> legs,
                      Instant bookedAt) {
        requireBalanced(legs);
        Map<UUID, BankAccount> locked = lockInOrder(legs.stream().map(Leg::accountId).distinct().toList());
        // Read after the locks: an entry that waited for one is dated after the entry it waited for
        Instant now = bookedAt != null ? bookedAt : clock.instant();
        JournalEntry entry = JournalEntry.of(kind, description, reference, initiatedBy, now);
        post(entry, legs, locked);
        entries.save(entry);
        announce(entry, locked);
        log.info("Booked {} entry {} moving {} across {} accounts", kind, entry.getId(), moved(legs), locked.size());
        return entry;
    }

    /** Two legs or more, each a non-zero amount in cents, adding up to zero: the database checks the same. */
    private static void requireBalanced(List<Leg> legs) {
        if (legs.size() < 2) {
            throw new IllegalArgumentException("An entry needs at least two legs");
        }
        BigDecimal total = legs.stream().map(Leg::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() != 0 || legs.stream().anyMatch(leg -> leg.amount().signum() == 0 || leg.amount().scale() > 2)) {
            throw new IllegalArgumentException("Legs must be non-zero amounts in cents that add up to zero, not " + total);
        }
    }

    /** Applies each leg to its locked account and records the balance after it. */
    private static void post(JournalEntry entry, List<Leg> legs, Map<UUID, BankAccount> locked) {
        int position = 0;
        for (Leg leg : legs) {
            BankAccount account = locked.get(leg.accountId());
            BigDecimal balanceAfter = account.apply(leg.amount());
            entry.add(Posting.of(entry, position++, account.getId(), leg.amount(), balanceAfter,
                leg.counterpartyName(), leg.counterpartyIban()));
        }
    }

    /** Tells other modules about each movement on a customer account; the bank's own accounts stay internal. */
    private void announce(JournalEntry entry, Map<UUID, BankAccount> locked) {
        for (Posting posting : entry.getPostings()) {
            BankAccount account = locked.get(posting.getAccountId());
            if (account.isCustomer()) {
                events.publishEvent(new AccountTransactionBooked(account.getId(), account.getBusinessId(), entry.getId(),
                    entry.getKind(), posting.getAmount(), posting.getBalanceAfter(), posting.getCounterpartyName(),
                    entry.getReference(), entry.getBookedAt()));
            }
        }
    }

    private static BigDecimal moved(List<Leg> legs) {
        return legs.stream().map(Leg::amount).filter(amount -> amount.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Takes a row lock on each account, one at a time in the order PostgreSQL sorts UUIDs (their bytes,
     * unsigned, which is also the order of their hex text), so concurrent entries never deadlock. An
     * account the caller has already read is read again under the lock: its balance may have changed
     * since, and the lock is what makes it current.
     */
    private Map<UUID, BankAccount> lockInOrder(List<UUID> ids) {
        // Refreshing re-reads the row and would drop balance changes this transaction has not written yet,
        // for example from an earlier entry in the same transaction: write them first
        entityManager.flush();
        Map<UUID, BankAccount> locked = new HashMap<>();
        for (UUID id : ids.stream().sorted(Comparator.comparing(UUID::toString)).toList()) {
            BankAccount account = entityManager.find(BankAccount.class, id);
            if (account == null) {
                throw new NotFoundException("Account not found.");
            }
            entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);
            locked.put(id, account);
        }
        return locked;
    }

    /** One of the bank's own accounts, by its code. */
    UUID internal(InternalAccount account) {
        return accounts.findByCode(account.name())
            .orElseThrow(() -> new IllegalStateException("Internal account " + account + " is missing; see V8__banking_ledger.sql"))
            .getId();
    }
}
