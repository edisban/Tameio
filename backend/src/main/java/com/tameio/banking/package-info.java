/**
 * Core banking: current accounts with IBANs, and the double-entry ledger that records every movement
 * of money (ADR 0009). Only {@code Ledger} books entries; every entry's postings add up to zero, and
 * the database refuses anything else. Payments between accounts and to other banks were withdrawn for
 * now (ADR 0010).
 *
 * <p>Each posting to a customer account publishes {@link com.tameio.banking.AccountTransactionBooked},
 * from which other modules (the business overview, the bank's analytics) learn about money movements.
 */
@ApplicationModule(displayName = "Banking")
package com.tameio.banking;

import org.springframework.modulith.ApplicationModule;
