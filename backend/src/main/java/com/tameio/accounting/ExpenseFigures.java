package com.tameio.accounting;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/** What other modules (lending) may know of a business's books: its confirmed expenses of the last year. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpenseFigures {

    private static final int MONTHS = 12;

    /** Confirmed expenses with an issue date in the last twelve months, in total and per month. */
    public record LastYear(BigDecimal total, BigDecimal averageMonthly, long documents) {
    }

    private final ExpenseRepository expenses;
    private final Clock clock;

    public LastYear lastYear(UUID businessId) {
        LocalDate today = LocalDate.now(clock);
        var totals = expenses.totalsByCategory(businessId, today.minusMonths(MONTHS), today);
        BigDecimal total = totals.stream().map(ExpenseRepository.CategoryTotals::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        long documents = totals.stream().mapToLong(ExpenseRepository.CategoryTotals::count).sum();
        return new LastYear(total.setScale(2, RoundingMode.HALF_UP),
            total.divide(BigDecimal.valueOf(MONTHS), 2, RoundingMode.HALF_UP), documents);
    }
}
