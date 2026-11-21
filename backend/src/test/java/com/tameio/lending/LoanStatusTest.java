package com.tameio.lending;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LoanStatusTest {

    @ParameterizedTest
    @CsvSource({
        "DRAFT, SUBMITTED",
        "SUBMITTED, IN_REVIEW",
        "IN_REVIEW, RECOMMENDED",
        "RECOMMENDED, APPROVED",
        "RECOMMENDED, DECLINED",
        "APPROVED, DISBURSED",
    })
    void allowsEachStepOfTheReview(LoanStatus from, LoanStatus to) {
        assertThat(from.canMoveTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "DRAFT, APPROVED",
        "SUBMITTED, RECOMMENDED",
        "IN_REVIEW, APPROVED",
        "RECOMMENDED, DISBURSED",
        "DECLINED, APPROVED",
        "DISBURSED, DRAFT",
        "APPROVED, DECLINED",
    })
    void refusesSkippingOrGoingBack(LoanStatus from, LoanStatus to) {
        assertThat(from.canMoveTo(to)).isFalse();
    }

    @Test
    void aDeclinedOrDisbursedApplicationIsFinal() {
        for (LoanStatus next : LoanStatus.values()) {
            assertThat(LoanStatus.DECLINED.canMoveTo(next)).isFalse();
            assertThat(LoanStatus.DISBURSED.canMoveTo(next)).isFalse();
        }
    }
}
