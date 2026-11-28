package com.tameio.lending;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The six measures the credit model reads, worked out exactly as the Python package does
 * (ml/credit-risk/src/credit_risk/features.py); shared examples in a test keep the two equal.
 *
 * <p>Four are the scorecard's; two it does not use: how much the money coming in swings from month to month, and
 * the share of months in which more went out than came in.
 */
record CreditFeatures(Map<String, Double> values) {

    /** Beyond its cap a measure says nothing more; the same caps are in the exported model. */
    static final Map<String, Double> CAPS = orderedCaps();

    private static final int DECIMALS = 6;
    private static final int MONTHS_PER_YEAR = 12;

    /** One month's money in and out. */
    record Month(double inflow, double outflow) {
    }

    static CreditFeatures of(double amount, double instalment, double balance, int historyMonths, List<Month> months) {
        double meanIn = months.stream().mapToDouble(Month::inflow).average().orElse(0);
        double meanOut = months.stream().mapToDouble(Month::outflow).average().orElse(0);
        double surplus = meanIn - meanOut;
        Map<String, Double> raw = new LinkedHashMap<>();
        // With no surplus or no inflow there is nothing to measure against: the worst value
        raw.put("repayment_ratio", surplus > 0 ? instalment / surplus : CAPS.get("repayment_ratio"));
        raw.put("loan_size", meanIn > 0 ? amount / (MONTHS_PER_YEAR * meanIn) : CAPS.get("loan_size"));
        raw.put("history_months", (double) historyMonths);
        raw.put("cash_cushion", instalment > 0 ? balance / instalment : CAPS.get("cash_cushion"));
        raw.put("inflow_volatility", meanIn > 0 ? standardDeviation(months, meanIn) / meanIn : CAPS.get("inflow_volatility"));
        raw.put("negative_months", months.isEmpty()
            ? CAPS.get("negative_months")
            : months.stream().filter(m -> m.outflow() > m.inflow()).count() / (double) months.size());
        Map<String, Double> values = new LinkedHashMap<>();
        raw.forEach((name, value) -> values.put(name, round(Math.min(Math.max(value, 0), CAPS.get(name)))));
        return new CreditFeatures(values);
    }

    static CreditFeatures of(BigDecimal amount, BigDecimal instalment, BigDecimal balance, int historyMonths,
                             List<Month> months) {
        return of(amount.doubleValue(), instalment.doubleValue(), balance.doubleValue(), historyMonths, months);
    }

    /** Population standard deviation (the Python side uses ddof=0 as well). */
    private static double standardDeviation(List<Month> months, double mean) {
        double variance = months.stream().mapToDouble(m -> (m.inflow() - mean) * (m.inflow() - mean)).average().orElse(0);
        return Math.sqrt(variance);
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(DECIMALS, RoundingMode.HALF_EVEN).doubleValue();
    }

    private static Map<String, Double> orderedCaps() {
        Map<String, Double> caps = new LinkedHashMap<>();
        caps.put("repayment_ratio", 2.0);
        caps.put("loan_size", 3.0);
        caps.put("history_months", 36.0);
        caps.put("cash_cushion", 24.0);
        caps.put("inflow_volatility", 2.0);
        caps.put("negative_months", 1.0);
        return Collections.unmodifiableMap(caps);
    }
}
