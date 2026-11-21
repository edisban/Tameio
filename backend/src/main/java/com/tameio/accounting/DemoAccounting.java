package com.tameio.accounting;

import com.tameio.documents.DocumentReviewed;
import com.tameio.documents.ExpenseCategory;
import com.tameio.identity.DemoBusinessReady;
import com.tameio.platform.demo.DemoYear;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fills a demo business's expense book with the expenses of its {@link DemoYear}: the same purchases the
 * banking module pays from its account, as if each receipt had been uploaded and confirmed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class DemoAccounting {

    private final ExpenseRepository expenses;

    @EventListener
    @Order(DemoBusinessReady.EXPENSES)
    void on(DemoBusinessReady demo) {
        long count = DemoYear.plan(demo.profile(), demo.end(), demo.seed()).movements().stream()
            .filter(m -> m.flow() == DemoYear.Flow.EXPENSE)
            .map(m -> Expense.from(new DocumentReviewed(UUID.randomUUID(), demo.businessId(), m.counterpartyAfm(), m.date(),
                m.net(), m.vat(), m.total(), ExpenseCategory.valueOf(m.category()))))
            .map(expenses::save)
            .count();
        log.info("Demo expense book for {}: {} expenses", demo.name(), count);
    }
}
