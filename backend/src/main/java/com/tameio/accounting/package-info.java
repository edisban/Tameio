/**
 * The books: every confirmed document becomes an expense entry, and the module answers what a small
 * business and its accountant ask of them: what was spent, on what, and how much VAT can be deducted,
 * by month and by quarter.
 *
 * <p>Entries come from {@link com.tameio.documents.DocumentReviewed} through an asynchronous,
 * durable module listener: the event is stored with the review's transaction, so an entry is never
 * lost even if the app stops in between, and a second review updates the same entry.
 */
@ApplicationModule(displayName = "Accounting")
package com.tameio.accounting;

import org.springframework.modulith.ApplicationModule;
