package com.tameio.lending;

import com.tameio.lending.RiskFactor.Code;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ScorecardTest {

    /** A healthy café: 9.000 € in and 7.000 € out a month, a year of history, 20.000 € in the account. */
    private static Scorecard.Inputs healthy(String amount, String instalment) {
        return new Scorecard.Inputs(new BigDecimal(amount), new BigDecimal(instalment), new BigDecimal("9000"),
            new BigDecimal("7000"), new BigDecimal("20000"), 14);
    }

    private static RiskFactor factor(Scorecard.Result result, Code code) {
        return result.factors().stream().filter(f -> f.code() == code).findFirst().orElseThrow();
    }

    @Test
    void aSmallLoanForAHealthyBusinessScoresFullMarks() {
        Scorecard.Result result = Scorecard.score(healthy("10000", "308.77"));

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.band()).isEqualTo(RiskBand.LOW);
    }

    @Test
    void eachFactorShowsWhatWasMeasured() {
        Scorecard.Result result = Scorecard.score(healthy("10000", "308.77"));

        // 308,77 of a 2.000 € monthly surplus
        assertThat(factor(result, Code.REPAYMENT_CAPACITY).measured()).isEqualByComparingTo("0.1544");
        assertThat(factor(result, Code.TRACK_RECORD).measured()).isEqualByComparingTo("14");
        assertThat(result.factors()).allSatisfy(f -> assertThat(f.maxPoints()).isEqualTo(Scorecard.MAX_POINTS));
    }

    @Test
    void anInstalmentEatingMostOfTheSurplusLosesThoseMarks() {
        Scorecard.Result result = Scorecard.score(healthy("60000", "1188.07"));

        assertThat(factor(result, Code.REPAYMENT_CAPACITY).points()).isZero();
        assertThat(result.band()).isEqualTo(RiskBand.MEDIUM);
    }

    @Test
    void aBusinessThatLosesMoneyScoresNothingForRepaymentCapacity() {
        var losing = new Scorecard.Inputs(new BigDecimal("5000"), new BigDecimal("150"), new BigDecimal("4000"),
            new BigDecimal("4500"), new BigDecimal("500"), 2);

        Scorecard.Result result = Scorecard.score(losing);

        assertThat(factor(result, Code.REPAYMENT_CAPACITY).measured()).isNull();
        assertThat(factor(result, Code.REPAYMENT_CAPACITY).points()).isZero();
        assertThat(result.band()).isEqualTo(RiskBand.HIGH);
    }

    @Test
    void bandsSplitAtSeventyFiveAndFifty() {
        assertThat(Scorecard.band(75)).isEqualTo(RiskBand.LOW);
        assertThat(Scorecard.band(74)).isEqualTo(RiskBand.MEDIUM);
        assertThat(Scorecard.band(50)).isEqualTo(RiskBand.MEDIUM);
        assertThat(Scorecard.band(49)).isEqualTo(RiskBand.HIGH);
    }
}
