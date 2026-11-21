package com.tameio.accounting;

import com.tameio.documents.DocumentReviewed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the expense book in step with confirmed documents. Runs after the review commits, in its own
 * transaction and thread; the event publication registry keeps the event until this succeeds, and
 * sends it again after a restart if it never did.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ExpenseBook {

    private final ExpenseRepository expenses;

    @ApplicationModuleListener
    void on(DocumentReviewed reviewed) {
        expenses.findByDocumentId(reviewed.documentId()).ifPresentOrElse(
            expense -> {
                expense.update(reviewed);
                log.info("Expense for document {} updated", reviewed.documentId());
            },
            () -> {
                expenses.save(Expense.from(reviewed));
                log.info("Expense for document {} added to business {}", reviewed.documentId(), reviewed.businessId());
            });
    }
}
