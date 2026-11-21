package com.tameio.banking;

/** The bank's own ledger accounts, created by V8__banking_ledger.sql. */
enum InternalAccount {
    /** The bank's account at the Bank of Greece: money entering or leaving the bank as a whole. */
    SETTLEMENT,
    /** Customer payments to other banks, on their way through SEPA clearing. */
    SEPA_OUT,
    /** Payments from other banks, received but not yet credited. */
    SEPA_IN,
    /** What customers owe the bank on their loans. */
    LOANS,
    INTEREST_INCOME,
    FEE_INCOME
}
