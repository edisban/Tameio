/**
 * Loans for businesses: the application, filled in from the business's books and account; its review
 * by the bank (an analyst recommends, a manager within their approval limit decides, never the same
 * person); and paying an approved loan into the account through the ledger. A simple, explained risk
 * score helps the people deciding; it never decides (ADR 0005).
 */
@ApplicationModule(displayName = "Lending")
package com.tameio.lending;

import org.springframework.modulith.ApplicationModule;
