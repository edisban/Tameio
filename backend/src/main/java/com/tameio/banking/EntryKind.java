package com.tameio.banking;

/** What a journal entry records. Clients show their own label for each. */
public enum EntryKind {
    /** Money arriving from outside the bank; on a local stack, a demo deposit stands in for one. */
    INCOMING_PAYMENT,
    /** Money paid out to a supplier at another bank; the demo history books its expenses this way. */
    OUTGOING_PAYMENT
}
