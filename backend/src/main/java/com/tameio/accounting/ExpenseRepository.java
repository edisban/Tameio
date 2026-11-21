package com.tameio.accounting;

import com.tameio.documents.ExpenseCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    Optional<Expense> findByDocumentId(UUID documentId);

    @Query("""
        select e from Expense e
        where e.businessId = :businessId and e.issueDate between :from and :to
          and (:category is null or e.category = :category)""")
    Page<Expense> findInPeriod(UUID businessId, LocalDate from, LocalDate to, ExpenseCategory category, Pageable pageable);

    @Query("""
        select e from Expense e
        where e.businessId = :businessId and e.issueDate between :from and :to
        order by e.issueDate, e.createdAt""")
    List<Expense> findAllInPeriod(UUID businessId, LocalDate from, LocalDate to);

    /** Sums for one month of the year. */
    record MonthTotals(int month, BigDecimal net, BigDecimal vat, BigDecimal total, long count) {
    }

    /** Sums for one category. */
    record CategoryTotals(ExpenseCategory category, BigDecimal net, BigDecimal vat, BigDecimal total, long count) {
    }

    @Query("""
        select new com.tameio.accounting.ExpenseRepository$MonthTotals(
            extract(month from e.issueDate), sum(e.netAmount), sum(e.vatAmount), sum(e.totalAmount), count(e))
        from Expense e
        where e.businessId = :businessId and e.issueDate between :from and :to
        group by extract(month from e.issueDate)""")
    List<MonthTotals> totalsByMonth(UUID businessId, LocalDate from, LocalDate to);

    @Query("""
        select new com.tameio.accounting.ExpenseRepository$CategoryTotals(
            e.category, sum(e.netAmount), sum(e.vatAmount), sum(e.totalAmount), count(e))
        from Expense e
        where e.businessId = :businessId and e.issueDate between :from and :to
        group by e.category
        order by sum(e.totalAmount) desc""")
    List<CategoryTotals> totalsByCategory(UUID businessId, LocalDate from, LocalDate to);
}
