package com.tameio.lending;

import com.tameio.accounting.ExpenseFigures;
import com.tameio.banking.LoanAccounts;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Fills in an application from what the bank already knows: the account's money flows (banking) and the
 * confirmed expenses (accounting); then quotes the instalment and scores the risk.
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(LendingProperties.class)
class LoanAssessor {

    private final LoanAccounts accounts;
    private final ExpenseFigures expenses;
    private final LendingProperties properties;
    private final CreditModel creditModel;

    Assessment assess(UUID businessId, UUID accountId, BigDecimal amount, int termMonths) {
        LoanAccounts.AccountFigures figures = accounts.figures(businessId, accountId);
        ExpenseFigures.LastYear lastYear = expenses.lastYear(businessId);
        BigDecimal instalment = Instalments.monthly(amount, properties.annualRate(), termMonths);
        Scorecard.Result risk = Scorecard.score(new Scorecard.Inputs(amount, instalment, figures.averageMonthlyInflow(),
            figures.averageMonthlyOutflow(), figures.balance(), figures.monthsOfHistory()));
        // The model's estimate, beside the scorecard: an extra view for the analyst, not the score itself
        List<CreditFeatures.Month> months = figures.months().stream()
            .map(m -> new CreditFeatures.Month(m.inflow().doubleValue(), m.outflow().doubleValue())).toList();
        CreditModel.Estimate credit = creditModel.estimate(
            CreditFeatures.of(amount, instalment, figures.balance(), figures.monthsOfHistory(), months));
        return new Assessment(properties.annualRate(), instalment, figures.averageMonthlyInflow(), figures.averageMonthlyOutflow(),
            lastYear.averageMonthly(), figures.balance(), figures.monthsOfHistory(), risk, credit);
    }
}
