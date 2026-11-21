package com.tameio.lending;

import com.tameio.platform.persistence.AdvisoryLocks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.OptionalInt;

/**
 * Marks instalments as due once their date has come. Runs every night, and once at start-up to catch up
 * on nights the app was down.
 *
 * <p>Every instance runs it, so it takes an advisory lock first: the instance that gets it does the work,
 * the others skip. The update itself is idempotent, so a run that comes after another changes nothing.
 */
@Slf4j
@Component
class InstalmentsDue {

    private final LoanInstalmentRepository instalments;
    private final AdvisoryLocks locks;
    private final TransactionTemplate transaction;
    private final Clock clock;

    InstalmentsDue(LoanInstalmentRepository instalments, AdvisoryLocks locks, PlatformTransactionManager transactions, Clock clock) {
        this.instalments = instalments;
        this.locks = locks;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    @Scheduled(cron = "${tameio.lending.instalments-due-cron}", zone = "Europe/Athens")
    void nightly() {
        run();
    }

    @EventListener(ApplicationReadyEvent.class)
    void atStartUp() {
        run();
    }

    /** How many instalments became due, or empty when another instance holds the lock. */
    OptionalInt run() {
        LocalDate today = LocalDate.now(clock.withZone(RepaymentSchedule.ATHENS));
        OptionalInt marked = transaction.execute(status -> locks.tryAcquire(AdvisoryLocks.Key.INSTALMENTS_DUE)
            ? OptionalInt.of(instalments.markDue(today, clock.instant()))
            : OptionalInt.empty());
        if (marked != null && marked.isPresent()) {
            log.info("Marked {} instalments as due on {}", marked.getAsInt(), today);
        } else {
            log.info("Skipped marking instalments as due: another instance is doing it");
        }
        return marked == null ? OptionalInt.empty() : marked;
    }
}
