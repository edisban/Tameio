package com.tameio.banking;

/** What a journal entry records. Clients show their own label for each. */
public enum EntryKind {
    /** Between two accounts at this bank, booked at once. */
    TRANSFER,
    /** Money arriving from outside the bank; on a local stack, a demo deposit stands in for one. */
    INCOMING_PAYMENT
}
