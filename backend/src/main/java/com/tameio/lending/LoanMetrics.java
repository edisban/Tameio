package com.tameio.lending;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Lending figures for the Grafana dashboard, counted in the database at each scrape. Every instance
 * reports the same numbers, so the dashboard takes the maximum across instances, not the sum.
 */
@Component
@RequiredArgsConstructor
class LoanMetrics implements MeterBinder {

    private final LoanApplicationRepository applications;
    private final LoanInstalmentRepository instalments;

    @Override
    public void bindTo(MeterRegistry registry) {
        for (LoanStatus status : LoanStatus.values()) {
            Gauge.builder("tameio.loan.applications", applications, repository -> repository.countByStatus(status))
                .tag("status", status.name())
                .description("Loan applications by status")
                .register(registry);
        }
        Gauge.builder("tameio.loans.disbursed", applications, repository -> repository.totalDisbursed().doubleValue())
            .baseUnit("euros")
            .description("Money paid out in loans")
            .register(registry);
        for (LoanInstalment.Status status : LoanInstalment.Status.values()) {
            Gauge.builder("tameio.loan.instalments", instalments, repository -> repository.countByStatus(status))
                .tag("status", status.name())
                .description("Instalments of disbursed loans by status")
                .register(registry);
        }
    }
}
