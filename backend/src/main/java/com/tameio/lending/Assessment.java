package com.tameio.lending;

import java.math.BigDecimal;

/** The figures an application was assessed on, its quoted instalment, and the scorecard's result. */
record Assessment(BigDecimal annualRate, BigDecimal monthlyInstalment, BigDecimal averageMonthlyInflow,
                  BigDecimal averageMonthlyOutflow, BigDecimal averageMonthlyExpenses, BigDecimal balance,
                  int monthsOfHistory, Scorecard.Result risk) {
}
