package com.tameio.lending;

import com.tameio.platform.persistence.AuditedEntity;
import com.tameio.platform.web.ConflictException;
import com.tameio.platform.web.ForbiddenException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A business's loan application and its review. Every change of status goes through {@link #moveTo},
 * which refuses any move {@link LoanStatus} does not allow; the people involved are recorded at each step.
 */
@Entity
@Table(name = "loan_application")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class LoanApplication extends AuditedEntity {

    /** The refusal code when whoever recommended tries to decide too; clients explain it in their own words. */
    static final String SAME_PERSON = "same-person";

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "term_months", nullable = false)
    private int termMonths;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LoanPurpose purpose;

    @Column(length = 500)
    private String description;

    @Column(name = "annual_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal annualRate;

    @Column(name = "monthly_instalment", nullable = false, precision = 14, scale = 2)
    private BigDecimal monthlyInstalment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoanStatus status;

    @Column(name = "average_monthly_inflow", nullable = false, precision = 14, scale = 2)
    private BigDecimal averageMonthlyInflow;

    @Column(name = "average_monthly_outflow", nullable = false, precision = 14, scale = 2)
    private BigDecimal averageMonthlyOutflow;

    @Column(name = "average_monthly_expenses", nullable = false, precision = 14, scale = 2)
    private BigDecimal averageMonthlyExpenses;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balance;

    @Column(name = "months_of_history", nullable = false)
    private int monthsOfHistory;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_band", nullable = false, length = 10)
    private RiskBand riskBand;

    @ElementCollection
    @CollectionTable(name = "loan_risk_factor", joinColumns = @JoinColumn(name = "loan_application_id"))
    @OrderColumn(name = "position")
    private List<RiskFactor> riskFactors = new ArrayList<>();

    @Column(name = "default_probability", precision = 5, scale = 4)
    private BigDecimal defaultProbability;

    @Column(name = "credit_model_version", length = 20)
    private String creditModelVersion;

    @ElementCollection
    @CollectionTable(name = "loan_credit_factor", joinColumns = @JoinColumn(name = "loan_application_id"))
    @OrderColumn(name = "position")
    private List<CreditFactor> creditFactors = new ArrayList<>();

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewer_id", length = 64)
    private String reviewerId;

    @Column(name = "reviewer_name", length = 200)
    private String reviewerName;

    @Column(name = "review_started_at")
    private Instant reviewStartedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Recommendation recommendation;

    @Column(name = "recommendation_note", length = 1000)
    private String recommendationNote;

    @Column(name = "recommended_by", length = 64)
    private String recommendedBy;

    @Column(name = "recommended_by_name", length = 200)
    private String recommendedByName;

    @Column(name = "recommended_at")
    private Instant recommendedAt;

    @Column(name = "decision_note", length = 1000)
    private String decisionNote;

    @Column(name = "decided_by", length = 64)
    private String decidedBy;

    @Column(name = "decided_by_name", length = 200)
    private String decidedByName;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "disbursement_entry_id")
    private UUID disbursementEntryId;

    @Column(name = "disbursed_at")
    private Instant disbursedAt;

    /** A member of bank staff acting on the application. */
    record Staff(String id, String name) {
    }

    static LoanApplication draft(UUID businessId, String businessName, LoanTerms terms, Assessment assessment) {
        var application = new LoanApplication();
        application.businessId = businessId;
        application.businessName = businessName;
        application.status = LoanStatus.DRAFT;
        application.change(terms, assessment);
        return application;
    }

    /** New terms for a draft, with the assessment they lead to. */
    void change(LoanTerms terms, Assessment assessment) {
        if (status != LoanStatus.DRAFT) {
            throw new ConflictException("Only a draft can be changed.");
        }
        accountId = terms.accountId();
        amount = terms.amount();
        termMonths = terms.termMonths();
        purpose = terms.purpose();
        description = terms.description();
        assess(assessment);
    }

    /** Sent to the bank with the figures as they are at that moment. */
    void submit(Assessment assessment, Instant at) {
        moveTo(LoanStatus.SUBMITTED);
        assess(assessment);
        submittedAt = at;
    }

    void startReview(Staff analyst, Instant at) {
        moveTo(LoanStatus.IN_REVIEW);
        reviewerId = analyst.id();
        reviewerName = analyst.name();
        reviewStartedAt = at;
    }

    void recommend(Staff analyst, Recommendation what, String note, Instant at) {
        moveTo(LoanStatus.RECOMMENDED);
        recommendation = what;
        recommendationNote = note;
        recommendedBy = analyst.id();
        recommendedByName = analyst.name();
        recommendedAt = at;
    }

    /** The second pair of eyes: whoever recommended can never decide. */
    void decide(Staff manager, Recommendation decision, String note, Instant at) {
        if (manager.id().equals(recommendedBy)) {
            throw new ForbiddenException(SAME_PERSON, "The person who recommended an application cannot also decide it.");
        }
        moveTo(decision == Recommendation.APPROVE ? LoanStatus.APPROVED : LoanStatus.DECLINED);
        decisionNote = note;
        decidedBy = manager.id();
        decidedByName = manager.name();
        decidedAt = at;
    }

    void disbursed(UUID journalEntryId, Instant at) {
        moveTo(LoanStatus.DISBURSED);
        disbursementEntryId = journalEntryId;
        disbursedAt = at;
    }

    private void moveTo(LoanStatus next) {
        if (!status.canMoveTo(next)) {
            throw new ConflictException("An application that is " + status + " cannot become " + next + ".");
        }
        status = next;
    }

    private void assess(Assessment assessment) {
        annualRate = assessment.annualRate();
        monthlyInstalment = assessment.monthlyInstalment();
        averageMonthlyInflow = assessment.averageMonthlyInflow();
        averageMonthlyOutflow = assessment.averageMonthlyOutflow();
        averageMonthlyExpenses = assessment.averageMonthlyExpenses();
        balance = assessment.balance();
        monthsOfHistory = assessment.monthsOfHistory();
        riskScore = assessment.risk().score();
        riskBand = assessment.risk().band();
        riskFactors.clear();
        riskFactors.addAll(assessment.risk().factors());
        defaultProbability = assessment.credit().probability();
        creditModelVersion = assessment.credit().modelVersion();
        creditFactors.clear();
        creditFactors.addAll(assessment.credit().factors());
    }
}
