package com.tameio.accounting;

import com.tameio.documents.DocumentReviewed;
import com.tameio.documents.ExpenseCategory;
import com.tameio.platform.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One line in a business's expense book, from one confirmed document. */
@Entity
@Table(name = "expense")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Expense extends AuditedEntity {

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    // By id only: the documents module owns documents
    @Column(name = "document_id", nullable = false, updatable = false, unique = true)
    private UUID documentId;

    @Column(name = "supplier_afm", nullable = false, length = 9)
    private String supplierAfm;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "net_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "vat_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal vatAmount;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ExpenseCategory category;

    static Expense from(DocumentReviewed reviewed) {
        var expense = new Expense();
        expense.businessId = reviewed.businessId();
        expense.documentId = reviewed.documentId();
        expense.update(reviewed);
        return expense;
    }

    /** A document reviewed again: its entry takes the new values. */
    void update(DocumentReviewed reviewed) {
        supplierAfm = reviewed.issuerAfm();
        issueDate = reviewed.issueDate();
        netAmount = reviewed.netAmount();
        vatAmount = reviewed.vatAmount();
        totalAmount = reviewed.totalAmount();
        category = reviewed.category();
    }
}
