package com.tameio.lending;

import com.tameio.accounting.ExpenseFigures;
import com.tameio.banking.LoanAccounts;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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

    Assessment assess(UUID businessId, UUID accountId, BigDecimal amount, int termMonths) {
        LoanAccounts.AccountFigures figures = accounts.figures(businessId, accountId);
        ExpenseFigures.LastYear lastYear = expenses.lastYear(businessId);
        BigDecimal instalment = Instalments.monthly(amount, properties.annualRate(), termMonths);
        Scorecard.Result risk = Scorecard.score(new Scorecard.Inputs(amount, instalment, figures.averageMonthlyInflow(),
            figures.averageMonthlyOutflow(), figures.balance(), figures.monthsOfHistory()));
        return new Assessment(properties.annualRate(), instalment, figures.averageMonthlyInflow(), figures.averageMonthlyOutflow(),
            lastYear.averageMonthly(), figures.balance(), figures.monthsOfHistory(), risk);
    }
}
