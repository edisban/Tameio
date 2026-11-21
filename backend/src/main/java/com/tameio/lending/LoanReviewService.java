package com.tameio.lending;

import com.tameio.banking.LoanAccounts;
import com.tameio.identity.StaffService;
import com.tameio.lending.LoanViews.CaseView;
import com.tameio.lending.LoanViews.QueueItem;
import com.tameio.lending.LoanViews.Verdict;
import com.tameio.platform.security.CurrentUser;
import com.tameio.platform.web.BadRequestException;
import com.tameio.platform.web.ForbiddenException;
import com.tameio.platform.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * The bank's side: the queue of applications, the case, and the review steps. An analyst recommends; a
 * manager or administrator whose approval limit covers the amount decides, and never the person who
 * recommended; an approved loan is then paid into the business's account.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class LoanReviewService {

    private static final Set<LoanStatus> VISIBLE_TO_THE_BANK = EnumSet.complementOf(EnumSet.of(LoanStatus.DRAFT));

    private final LoanApplicationRepository applications;
    private final StaffService staff;
    private final LoanAccounts accounts;
    private final RepaymentSchedule schedule;
    private final Clock clock;

    /** The queue, newest first; with a status, only that status. Drafts stay with the business. */
    Page<QueueItem> queue(LoanStatus status, Pageable pageable) {
        if (status == LoanStatus.DRAFT) {
            throw new BadRequestException("Drafts are not sent to the bank.");
        }
        Set<LoanStatus> statuses = status == null ? VISIBLE_TO_THE_BANK : EnumSet.of(status);
        return applications.findByStatusIn(statuses, pageable).map(QueueItem::of);
    }

    CaseView caseOf(UUID id) {
        return CaseView.of(submitted(id));
    }

    @Transactional
    CaseView startReview(CurrentUser analyst, UUID id) {
        LoanApplication application = submitted(id);
        application.startReview(as(analyst), clock.instant());
        log.info("Review of loan application {} started by {}", id, analyst.id());
        return CaseView.of(application);
    }

    @Transactional
    CaseView recommend(CurrentUser analyst, UUID id, Verdict verdict) {
        LoanApplication application = submitted(id);
        application.recommend(as(analyst), verdict.outcome(), verdict.note(), clock.instant());
        log.info("Loan application {}: {} recommended by {}", id, verdict.outcome(), analyst.id());
        return CaseView.of(application);
    }

    @Transactional
    CaseView decide(CurrentUser manager, UUID id, Verdict verdict) {
        LoanApplication application = submitted(id);
        requireApprovalLimit(manager, application.getAmount());
        application.decide(as(manager), verdict.outcome(), verdict.note(), clock.instant());
        log.info("Loan application {}: {} decided by {}", id, verdict.outcome(), manager.id());
        return CaseView.of(application);
    }

    /** Pays the approved loan into the account; the money and the new status commit together or not at all. */
    @Transactional
    CaseView disburse(CurrentUser manager, UUID id) {
        LoanApplication application = submitted(id);
        UUID entry = accounts.disburse(application.getBusinessId(), application.getAccountId(), application.getAmount(),
            "Loan " + application.getId(), manager.id());
        Instant now = clock.instant();
        application.disbursed(entry, now);
        schedule.create(application, now);
        log.info("Loan application {} disbursed by {}: {}", id, manager.id(), application.getAmount());
        return CaseView.of(application);
    }

    private void requireApprovalLimit(CurrentUser manager, BigDecimal amount) {
        BigDecimal limit = staff.approvalLimit(manager.id()).orElse(null);
        if (limit == null || limit.compareTo(amount) < 0) {
            throw new ForbiddenException("Your approval limit does not cover this amount.");
        }
    }

    private LoanApplication submitted(UUID id) {
        return applications.findById(id).filter(a -> a.getStatus() != LoanStatus.DRAFT)
            .orElseThrow(() -> new NotFoundException("Loan application not found."));
    }

    private static LoanApplication.Staff as(CurrentUser user) {
        return new LoanApplication.Staff(user.id(), user.name());
    }
}
