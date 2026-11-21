package com.tameio.banking;

/** The bank's own ledger accounts, created by V8__banking_ledger.sql. */
enum InternalAccount {
    /** The bank's account at the Bank of Greece: money entering or leaving the bank as a whole. */
    SETTLEMENT,
    /** Payments on their way through SEPA clearing; unused since payments were withdrawn (ADR 0010). */
    SEPA_OUT,
    /** Unused since payments were withdrawn (ADR 0010). */
    SEPA_IN,
    /** What customers owe the bank on their loans. */
    LOANS,
    INTEREST_INCOME,
    FEE_INCOME
}
