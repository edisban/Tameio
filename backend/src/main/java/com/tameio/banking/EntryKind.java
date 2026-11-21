package com.tameio.banking;

/** What a journal entry records. Clients show their own label for each. */
public enum EntryKind {
    /** Between two accounts at this bank, booked at once. */
    TRANSFER,
    /** Money arriving from outside the bank; on a local stack, a demo deposit stands in for one. */
    INCOMING_PAYMENT,
    /** A SEPA credit transfer leaving the payer's account for clearing. */
    SEPA_PAYMENT,
    /** The clearing house settled a SEPA payment: the money leaves the bank. */
    SEPA_SETTLEMENT,
    /** The other bank rejected a SEPA payment: the money goes back to the payer. */
    SEPA_RETURN
}
