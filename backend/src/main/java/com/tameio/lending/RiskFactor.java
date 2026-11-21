package com.tameio.lending;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;

/**
 * One line of the scorecard: what was measured and the points it earned. Clients explain each code in
 * their own words.
 *
 * @param measured the ratio or count the points come from; null when it could not be measured
 */
@Embeddable
public record RiskFactor(
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) Code code,
    @Column(precision = 14, scale = 4) BigDecimal measured,
    @Column(nullable = false) int points,
    @Column(name = "max_points", nullable = false) int maxPoints) {

    public enum Code {
        /** The monthly instalment as a share of what the business keeps each month. */
        REPAYMENT_CAPACITY,
        /** Months since the account's first movement. */
        TRACK_RECORD,
        /** How many instalments today's balance would cover. */
        CASH_CUSHION,
        /** The loan as a share of a year's money coming in. */
        LOAN_SIZE
    }
}
