package com.tameio.lending;

import com.tameio.platform.web.ConflictException;
import com.tameio.platform.web.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The application's rules on their own, without a database. */
class LoanApplicationTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static final LoanApplication.Staff ANALYST = new LoanApplication.Staff("analyst-1", "Sofia Rossi");
    private static final LoanApplication.Staff MANAGER = new LoanApplication.Staff("manager-1", "Daniel Novak");

    private LoanApplication application;

    private static Assessment assessment() {
        return new Assessment(new BigDecimal("0.07"), new BigDecimal("308.77"), new BigDecimal("9000"), new BigDecimal("7000"),
            new BigDecimal("3000"), new BigDecimal("20000"), 14, new Scorecard.Result(100, RiskBand.LOW, List.of()),
            new CreditModel.Estimate(new BigDecimal("0.0400"), "1", List.of()));
    }

    private static LoanTerms terms(String amount) {
        return new LoanTerms(UUID.randomUUID(), new BigDecimal(amount), 36, LoanPurpose.EQUIPMENT, null);
    }

    @BeforeEach
    void aDraft() {
        application = LoanApplication.draft(UUID.randomUUID(), "Καφέ Το Στέκι", terms("10000"), assessment());
    }

    private void recommendedBy(LoanApplication.Staff analyst) {
        application.submit(assessment(), NOW);
        application.startReview(analyst, NOW);
        application.recommend(analyst, Recommendation.APPROVE, "Strong cash flow", NOW);
    }

    @Test
    void aDraftCanBeChanged() {
        application.change(terms("15000"), assessment());

        assertThat(application.getAmount()).isEqualByComparingTo("15000");
    }

    @Test
    void aSubmittedApplicationCanNoLongerBeChanged() {
        application.submit(assessment(), NOW);

        assertThatThrownBy(() -> application.change(terms("15000"), assessment())).isInstanceOf(ConflictException.class);
    }

    @Test
    void aStepCannotBeSkipped() {
        application.submit(assessment(), NOW);

        assertThatThrownBy(() -> application.recommend(ANALYST, Recommendation.APPROVE, "x", NOW))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("SUBMITTED cannot become RECOMMENDED");
    }

    @Test
    void theRecommenderCannotAlsoDecide() {
        recommendedBy(ANALYST);

        assertThatThrownBy(() -> application.decide(ANALYST, Recommendation.APPROVE, "x", NOW)).isInstanceOf(ForbiddenException.class);
        assertThat(application.getStatus()).isEqualTo(LoanStatus.RECOMMENDED);
    }

    @Test
    void aSecondPersonDecidesAndIsRecorded() {
        recommendedBy(ANALYST);

        application.decide(MANAGER, Recommendation.DECLINE, "Too large for the turnover", NOW);

        assertThat(application.getStatus()).isEqualTo(LoanStatus.DECLINED);
        assertThat(application.getDecidedByName()).isEqualTo("Daniel Novak");
        assertThat(application.getRecommendedByName()).isEqualTo("Sofia Rossi");
    }

    @Test
    void onlyAnApprovedApplicationIsDisbursed() {
        recommendedBy(ANALYST);
        application.decide(MANAGER, Recommendation.DECLINE, "x", NOW);

        assertThatThrownBy(() -> application.disbursed(UUID.randomUUID(), NOW)).isInstanceOf(ConflictException.class);
    }
}
