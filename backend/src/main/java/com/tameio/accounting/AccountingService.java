package com.tameio.accounting;

import com.tameio.accounting.AccountingViews.CategoryLine;
import com.tameio.accounting.AccountingViews.ExpenseView;
import com.tameio.accounting.AccountingViews.Period;
import com.tameio.accounting.AccountingViews.Totals;
import com.tameio.accounting.AccountingViews.YearSummary;
import com.tameio.documents.ExpenseCategory;
import com.tameio.identity.BusinessService;
import com.tameio.platform.security.CurrentUser;
import com.tameio.platform.web.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reading a business's books. Every method first checks that the user belongs to the business. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class AccountingService {

    /** An export covers at most a little over a year, which keeps it a small, in-memory file. */
    private static final long MAX_EXPORT_DAYS = 400;

    private static final Map<ExpenseCategory, String> GREEK_NAMES = new EnumMap<>(Map.of(
        ExpenseCategory.FUEL, "Καύσιμα",
        ExpenseCategory.GROCERIES, "Τρόφιμα και σούπερ μάρκετ",
        ExpenseCategory.RESTAURANT, "Εστίαση",
        ExpenseCategory.UTILITIES, "Ρεύμα, νερό, φυσικό αέριο",
        ExpenseCategory.TELECOM, "Τηλεφωνία και internet",
        ExpenseCategory.RENT, "Ενοίκιο",
        ExpenseCategory.OFFICE_SUPPLIES, "Αναλώσιμα γραφείου",
        ExpenseCategory.TRANSPORT, "Μεταφορές",
        ExpenseCategory.PROFESSIONAL_SERVICES, "Αμοιβές τρίτων",
        ExpenseCategory.EQUIPMENT, "Εξοπλισμός"));

    private final ExpenseRepository expenses;
    private final BusinessService businesses;

    public Page<ExpenseView> expenses(CurrentUser user, UUID businessId, LocalDate from, LocalDate to,
                                      ExpenseCategory category, Pageable pageable) {
        businesses.checkAccess(user, businessId);
        checkPeriod(from, to);
        return expenses.findInPeriod(businessId, from, to, category, pageable).map(ExpenseView::of);
    }

    public YearSummary summary(CurrentUser user, UUID businessId, int year) {
        businesses.checkAccess(user, businessId);
        if (year < 2000 || year > 2100) {
            throw new BadRequestException("The year must be between 2000 and 2100.");
        }
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);

        Totals[] byMonth = new Totals[12];
        Arrays.fill(byMonth, Totals.ZERO);
        for (ExpenseRepository.MonthTotals m : expenses.totalsByMonth(businessId, from, to)) {
            byMonth[m.month() - 1] = new Totals(m.net(), m.vat(), m.total(), m.count());
        }

        List<Period> months = new ArrayList<>(12);
        List<Period> quarters = new ArrayList<>(4);
        Totals yearTotals = Totals.ZERO;
        for (int quarter = 0; quarter < 4; quarter++) {
            Totals sum = Totals.ZERO;
            for (int m = quarter * 3; m < quarter * 3 + 3; m++) {
                months.add(new Period(m + 1, byMonth[m]));
                sum = sum.plus(byMonth[m]);
            }
            quarters.add(new Period(quarter + 1, sum));
            yearTotals = yearTotals.plus(sum);
        }

        List<CategoryLine> categories = expenses.totalsByCategory(businessId, from, to).stream()
            .map(c -> new CategoryLine(c.category(), new Totals(c.net(), c.vat(), c.total(), c.count())))
            .toList();
        return new YearSummary(year, yearTotals, months, quarters, categories);
    }

    /**
     * The expense book as a CSV file the way Greek Excel opens it: semicolons between columns, decimal
     * commas, and a byte order mark so Greek letters show correctly.
     */
    public byte[] exportCsv(CurrentUser user, UUID businessId, LocalDate from, LocalDate to) {
        businesses.checkAccess(user, businessId);
        checkPeriod(from, to);
        if (ChronoUnit.DAYS.between(from, to) > MAX_EXPORT_DAYS) {
            throw new BadRequestException("Export at most a year at a time.");
        }
        DateTimeFormatter date = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("Ημερομηνία;ΑΦΜ εκδότη;Κατηγορία;Καθαρό;ΦΠΑ;Σύνολο;Παραστατικό\r\n");
        for (Expense e : expenses.findAllInPeriod(businessId, from, to)) {
            csv.append(date.format(e.getIssueDate())).append(';')
                .append(e.getSupplierAfm()).append(';')
                .append(GREEK_NAMES.get(e.getCategory())).append(';')
                .append(amount(e.getNetAmount())).append(';')
                .append(amount(e.getVatAmount())).append(';')
                .append(amount(e.getTotalAmount())).append(';')
                .append(e.getDocumentId()).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String amount(BigDecimal value) {
        return value.setScale(2).toPlainString().replace('.', ',');
    }

    private static void checkPeriod(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BadRequestException("The start of the period is after its end.");
        }
    }
}
