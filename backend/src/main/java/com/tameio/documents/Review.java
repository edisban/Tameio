package com.tameio.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** The values a person confirmed for a document. These, not what document AI read, go into the books. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Review {

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by", length = 64)
    private String reviewedBy;

    @Column(name = "confirmed_issuer_afm", length = 9)
    private String issuerAfm;

    @Column(name = "confirmed_issue_date")
    private LocalDate issueDate;

    @Column(name = "confirmed_net_amount", precision = 14, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "confirmed_vat_amount", precision = 14, scale = 2)
    private BigDecimal vatAmount;

    @Column(name = "confirmed_total_amount", precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmed_category", length = 40)
    private ExpenseCategory category;

    static Review of(ReviewRequest request, String reviewedBy, Instant reviewedAt) {
        var review = new Review();
        review.reviewedAt = reviewedAt;
        review.reviewedBy = reviewedBy;
        review.issuerAfm = request.issuerAfm();
        review.issueDate = request.issueDate();
        review.netAmount = request.netAmount();
        review.vatAmount = request.vatAmount();
        review.totalAmount = request.totalAmount();
        review.category = request.category();
        return review;
    }
}
