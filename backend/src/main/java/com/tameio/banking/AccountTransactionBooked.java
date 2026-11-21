package com.tameio.banking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Money moved on a business's account: published once per posting, in the transaction that booked it.
 * A positive amount came in, a negative one went out.
 */
public record AccountTransactionBooked(UUID accountId, UUID businessId, UUID entryId, EntryKind kind, BigDecimal amount,
                                       BigDecimal balanceAfter, String counterpartyName, String reference, Instant bookedAt) {
}
