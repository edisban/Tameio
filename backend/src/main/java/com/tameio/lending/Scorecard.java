package com.tameio.lending;

import com.tameio.lending.RiskFactor.Code;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * A points scorecard, the oldest and most explainable kind of credit score: four measures, each worth
 * up to 25 points by fixed tiers, added up to 0 to 100. A person can redo it on paper, which is the
 * point: the analyst and the manager see exactly why a score is what it is (ADR 0005).
 */
final class Scorecard {

    static final int MAX_POINTS = 25;
    private static final int LOW_RISK_FROM = 75;
    private static final int MEDIUM_RISK_FROM = 50;
    private static final MathContext RATIO = new MathContext(8, RoundingMode.HALF_UP);

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    /** A threshold and the points for being within it. */
    private record Tier(BigDecimal limit, int points) {
    }

    private static Tier tier(String limit, int points) {
        return new Tier(new BigDecimal(limit), points);
    }

    // Instalment as a share of the monthly surplus: the lower the better
    private static final List<Tier> REPAYMENT = List.of(tier("0.20", 25), tier("0.35", 18), tier("0.50", 10));
    // Loan as a share of a year's inflow: the lower the better
    private static final List<Tier> SIZE = List.of(tier("0.25", 25), tier("0.50", 15), tier("1.00", 8));
    // Months of account history: the more the better
    private static final List<Tier> HISTORY = List.of(tier("12", 25), tier("6", 15), tier("3", 8));
    // Instalments the balance covers: the more the better
    private static final List<Tier> CUSHION = List.of(tier("6", 25), tier("3", 15), tier("1", 8));

    record Inputs(BigDecimal amount, BigDecimal monthlyInstalment, BigDecimal averageMonthlyInflow,
                  BigDecimal averageMonthlyOutflow, BigDecimal balance, int monthsOfHistory) {
    }

    record Result(int score, RiskBand band, List<RiskFactor> factors) {
    }

    private Scorecard() {
    }

    static Result score(Inputs in) {
        BigDecimal surplus = in.averageMonthlyInflow().subtract(in.averageMonthlyOutflow());
        BigDecimal yearlyInflow = in.averageMonthlyInflow().multiply(MONTHS_PER_YEAR);
        List<RiskFactor> factors = List.of(
            atMost(Code.REPAYMENT_CAPACITY, ratio(in.monthlyInstalment(), surplus), REPAYMENT),
            atLeast(Code.TRACK_RECORD, BigDecimal.valueOf(in.monthsOfHistory()), HISTORY),
            atLeast(Code.CASH_CUSHION, ratio(in.balance(), in.monthlyInstalment()), CUSHION),
            atMost(Code.LOAN_SIZE, ratio(in.amount(), yearlyInflow), SIZE));
        int score = factors.stream().mapToInt(RiskFactor::points).sum();
        return new Result(score, band(score), factors);
    }

    static RiskBand band(int score) {
        if (score >= LOW_RISK_FROM) {
            return RiskBand.LOW;
        }
        return score >= MEDIUM_RISK_FROM ? RiskBand.MEDIUM : RiskBand.HIGH;
    }

    /** A share that cannot be worked out (nothing to divide by, or a loss) scores nothing. */
    private static BigDecimal ratio(BigDecimal part, BigDecimal whole) {
        return whole.signum() <= 0 ? null : part.divide(whole, RATIO).setScale(4, RoundingMode.HALF_UP);
    }

    private static RiskFactor atMost(Code code, BigDecimal measured, List<Tier> tiers) {
        int points = measured == null ? 0 : tiers.stream()
            .filter(t -> measured.compareTo(t.limit()) <= 0).mapToInt(Tier::points).findFirst().orElse(0);
        return new RiskFactor(code, measured, points, MAX_POINTS);
    }

    private static RiskFactor atLeast(Code code, BigDecimal measured, List<Tier> tiers) {
        int points = measured == null ? 0 : tiers.stream()
            .filter(t -> measured.compareTo(t.limit()) >= 0).mapToInt(Tier::points).findFirst().orElse(0);
        return new RiskFactor(code, measured, points, MAX_POINTS);
    }
}
