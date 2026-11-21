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
import java.util.List;
import java.util.Comparator;
import java.util.HashMap;
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

    /**
     * One side of an entry: a signed amount on an account, and who is on the other side.
     *
     * @param returnOfFunds the customer's own money coming back from a returned payment, which a frozen
     *                      account still takes
     */
    record Leg(UUID accountId, BigDecimal amount, String counterpartyName, String counterpartyIban, boolean returnOfFunds) {

        Leg(UUID accountId, BigDecimal amount, String counterpartyName, String counterpartyIban) {
            this(accountId, amount, counterpartyName, counterpartyIban, false);
        }
    }

    /** The client's Idempotency-Key and a hash of what it asked for. */
    record Idempotency(String key, String requestHash) {
    }

    private final BankAccountRepository accounts;
    private final JournalEntryRepository entries;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    JournalEntry book(EntryKind kind, String description, String reference, String initiatedBy, Idempotency idempotency,
                      List<Leg> legs) {
        return book(kind, description, reference, initiatedBy, idempotency, legs, clock.instant());
    }

    /** As {@link #book}, dated in the past: only for loading a year of demo history. */
    @Transactional(propagation = Propagation.MANDATORY)
    JournalEntry book(EntryKind kind, String description, String reference, String initiatedBy, Idempotency idempotency,
                      List<Leg> legs, Instant bookedAt) {
        if (legs.size() < 2) {
            throw new IllegalArgumentException("An entry needs at least two legs");
        }
        BigDecimal total = legs.stream().map(Leg::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() != 0 || legs.stream().anyMatch(leg -> leg.amount().signum() == 0 || leg.amount().scale() > 2)) {
            throw new IllegalArgumentException("Legs must be non-zero amounts in cents that add up to zero, not " + total);
        }

        Map<UUID, BankAccount> locked = lockInOrder(legs.stream().map(Leg::accountId).distinct().toList());

        Instant now = bookedAt;
        JournalEntry entry = JournalEntry.of(kind, description, reference, initiatedBy, idempotency, now);
        int position = 0;
        for (Leg leg : legs) {
            BankAccount account = locked.get(leg.accountId());
            BigDecimal balanceAfter = account.apply(leg.amount(), leg.returnOfFunds());
            entry.add(Posting.of(entry, position++, account.getId(), leg.amount(), balanceAfter,
                leg.counterpartyName(), leg.counterpartyIban()));
        }
        entries.save(entry);

        for (Posting posting : entry.getPostings()) {
            BankAccount account = locked.get(posting.getAccountId());
            if (account.isCustomer()) {
                events.publishEvent(new AccountTransactionBooked(account.getId(), account.getBusinessId(), entry.getId(), kind,
                    posting.getAmount(), posting.getBalanceAfter(), posting.getCounterpartyName(), reference, now));
            }
        }
        log.info("Booked {} entry {} moving {} across {} accounts", kind, entry.getId(),
            legs.stream().map(Leg::amount).filter(a -> a.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add), locked.size());
        return entry;
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
