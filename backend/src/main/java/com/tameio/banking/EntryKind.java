package com.tameio.banking;

/**
 * What a journal entry records. Clients show their own label for each. Entries are append-only, so a
 * kind stays here as long as entries of that kind may exist, even when nothing books it any more.
 */
public enum EntryKind {
    /** Between two accounts at this bank. Not booked since payments were withdrawn (ADR 0010). */
    TRANSFER,
    /** Money arriving from outside the bank; on a local stack, a demo deposit stands in for one. */
    INCOMING_PAYMENT,
    /** Money paid to another bank; the demo history books its expenses this way. */
    SEPA_PAYMENT,
    /** Not booked since payments were withdrawn (ADR 0010). */
    SEPA_SETTLEMENT,
    /** Not booked since payments were withdrawn (ADR 0010). */
    SEPA_RETURN
}
