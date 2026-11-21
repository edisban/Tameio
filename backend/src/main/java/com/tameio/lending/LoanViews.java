package com.tameio.lending;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What the lending API takes and returns. */
final class LoanViews {

    private LoanViews() {
    }

    /** The business's figures an application was assessed on. */
    record Figures(BigDecimal averageMonthlyInflow, BigDecimal averageMonthlyOutflow, BigDecimal averageMonthlyExpenses,
                   BigDecimal balance, int monthsOfHistory) {

        static Figures of(LoanApplication a) {
            return new Figures(a.getAverageMonthlyInflow(), a.getAverageMonthlyOutflow(), a.getAverageMonthlyExpenses(),
                a.getBalance(), a.getMonthsOfHistory());
        }
    }

    /** An application as the business sees it: no risk score, which is the bank's working tool. */
    record ApplicationView(UUID id, UUID accountId, BigDecimal amount, int termMonths, LoanPurpose purpose, String description,
                           BigDecimal annualRate, BigDecimal monthlyInstalment, LoanStatus status, Figures figures,
                           Instant createdAt, Instant submittedAt, String decisionNote, Instant decidedAt, Instant disbursedAt,
                           Repayment repayment) {

        static ApplicationView of(LoanApplication a, Repayment repayment) {
            return new ApplicationView(a.getId(), a.getAccountId(), a.getAmount(), a.getTermMonths(), a.getPurpose(),
                a.getDescription(), a.getAnnualRate(), a.getMonthlyInstalment(), a.getStatus(), Figures.of(a), a.getCreatedAt(),
                a.getSubmittedAt(), a.getDecisionNote(), a.getDecidedAt(), a.getDisbursedAt(), repayment);
        }
    }

    /** A disbursed loan's repayment: the next instalment (none once all are due) and how many are due now. */
    record Repayment(LocalDate nextDueOn, BigDecimal nextAmount, long instalmentsDue) {
    }

    /** One line of the back office's queue. */
    record QueueItem(UUID id, String businessName, BigDecimal amount, int termMonths, LoanPurpose purpose, LoanStatus status,
                     int riskScore, RiskBand riskBand, Instant submittedAt) {

        static QueueItem of(LoanApplication a) {
            return new QueueItem(a.getId(), a.getBusinessName(), a.getAmount(), a.getTermMonths(), a.getPurpose(), a.getStatus(),
                a.getRiskScore(), a.getRiskBand(), a.getSubmittedAt());
        }
    }

    /** Who did a step of the review, and when. */
    record Step(String id, String name, Instant at) {
    }

    /** The whole case, for the analyst and the manager. */
    record CaseView(UUID id, String businessName, BigDecimal amount, int termMonths, LoanPurpose purpose, String description,
                    BigDecimal annualRate, BigDecimal monthlyInstalment, LoanStatus status, Figures figures, int riskScore,
                    RiskBand riskBand, List<RiskFactor> riskFactors, Instant submittedAt, Step review,
                    Recommendation recommendation, String recommendationNote, Step recommended, String decisionNote,
                    Step decided, Instant disbursedAt) {

        static CaseView of(LoanApplication a) {
            return new CaseView(a.getId(), a.getBusinessName(), a.getAmount(), a.getTermMonths(), a.getPurpose(),
                a.getDescription(), a.getAnnualRate(), a.getMonthlyInstalment(), a.getStatus(), Figures.of(a), a.getRiskScore(),
                a.getRiskBand(), List.copyOf(a.getRiskFactors()), a.getSubmittedAt(),
                step(a.getReviewerId(), a.getReviewerName(), a.getReviewStartedAt()),
                a.getRecommendation(), a.getRecommendationNote(),
                step(a.getRecommendedBy(), a.getRecommendedByName(), a.getRecommendedAt()),
                a.getDecisionNote(), step(a.getDecidedBy(), a.getDecidedByName(), a.getDecidedAt()), a.getDisbursedAt());
        }

        private static Step step(String id, String name, Instant at) {
            return id == null ? null : new Step(id, name, at);
        }
    }

    /** An analyst's recommendation or a manager's decision, always with the reasons in words. */
    record Verdict(
        @NotNull(message = "Choose approve or decline") Recommendation outcome,
        @NotBlank(message = "Write the reasons") @Size(max = 1000, message = "At most 1,000 characters") String note) {

        Verdict {
            note = note == null ? null : note.strip();
        }
    }
}
