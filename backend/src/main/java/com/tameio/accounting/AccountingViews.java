package com.tameio.accounting;

import com.tameio.documents.ExpenseCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What the accounting API returns. */
final class AccountingViews {

    private AccountingViews() {
    }

    record ExpenseView(UUID id, UUID documentId, String supplierAfm, LocalDate issueDate, BigDecimal netAmount,
                       BigDecimal vatAmount, BigDecimal totalAmount, ExpenseCategory category) {

        static ExpenseView of(Expense e) {
            return new ExpenseView(e.getId(), e.getDocumentId(), e.getSupplierAfm(), e.getIssueDate(), e.getNetAmount(),
                e.getVatAmount(), e.getTotalAmount(), e.getCategory());
        }
    }

    record Totals(BigDecimal net, BigDecimal vat, BigDecimal total, long count) {

        static final Totals ZERO = new Totals(BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), 0);

        Totals plus(Totals other) {
            return new Totals(net.add(other.net), vat.add(other.vat), total.add(other.total), count + other.count);
        }
    }

    /** A month (1 to 12) or a quarter (1 to 4) of the year. */
    record Period(int number, Totals totals) {
    }

    record CategoryLine(ExpenseCategory category, Totals totals) {
    }

    /**
     * A year of expenses. Every month and quarter is listed, empty ones with zeros, so charts and
     * tables need no gap filling. The VAT is what the business paid on its purchases (input VAT).
     */
    record YearSummary(int year, Totals totals, List<Period> months, List<Period> quarters, List<CategoryLine> categories) {
    }
}
