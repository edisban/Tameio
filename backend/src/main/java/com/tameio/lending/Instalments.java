package com.tameio.lending;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** The fixed monthly instalment of an amortising loan (an annuity), as Greek banks quote it. */
final class Instalments {

    private static final MathContext PRECISION = MathContext.DECIMAL64;
    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    private Instalments() {
    }

    /** amount × r / (1 − (1 + r)^−n), with r the monthly rate and n the number of months. */
    static BigDecimal monthly(BigDecimal amount, BigDecimal annualRate, int months) {
        if (annualRate.signum() == 0) {
            return amount.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        }
        BigDecimal r = annualRate.divide(MONTHS_PER_YEAR, PRECISION);
        BigDecimal growth = BigDecimal.ONE.add(r).pow(months, PRECISION);
        BigDecimal discount = BigDecimal.ONE.subtract(BigDecimal.ONE.divide(growth, PRECISION));
        return amount.multiply(r, PRECISION).divide(discount, PRECISION).setScale(2, RoundingMode.HALF_UP);
    }
}
