package com.tameio.lending;

import com.tameio.lending.LoanViews.Repayment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

/** A disbursed loan's monthly instalments: written once at disbursement, read by the business's loans page. */
@Component
@RequiredArgsConstructor
class RepaymentSchedule {

    static final ZoneId ATHENS = ZoneId.of("Europe/Athens");

    private final LoanInstalmentRepository instalments;

    /** One instalment a month from a month after the money arrived, each the annuity amount of the application. */
    @Transactional(propagation = Propagation.MANDATORY)
    void create(LoanApplication application, Instant disbursedAt) {
        LocalDate disbursedOn = LocalDate.ofInstant(disbursedAt, ATHENS);
        List<LoanInstalment> schedule = IntStream.rangeClosed(1, application.getTermMonths())
            .mapToObj(n -> LoanInstalment.scheduled(application.getId(), n, disbursedOn.plusMonths(n), application.getMonthlyInstalment()))
            .toList();
        instalments.saveAll(schedule);
    }

    /** Where repayment stands, or null for a loan not paid out yet. */
    Repayment of(LoanApplication application) {
        if (application.getStatus() != LoanStatus.DISBURSED) {
            return null;
        }
        long due = instalments.countByLoanApplicationIdAndStatus(application.getId(), LoanInstalment.Status.DUE);
        return instalments.findFirstByLoanApplicationIdAndStatusOrderByNumber(application.getId(), LoanInstalment.Status.SCHEDULED)
            .map(next -> new Repayment(next.getDueOn(), next.getAmount(), due))
            .orElse(new Repayment(null, null, due));
    }
}
