package com.tameio.lending;

import com.tameio.identity.BusinessService;
import com.tameio.lending.LoanViews.ApplicationView;
import com.tameio.platform.security.CurrentUser;
import com.tameio.platform.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** A business's own applications: drafting, changing and submitting them. Every method checks membership first. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class LoanApplicationService {

    private final LoanApplicationRepository applications;
    private final LoanAssessor assessor;
    private final BusinessService businesses;
    private final Clock clock;

    List<ApplicationView> applications(CurrentUser user, UUID businessId) {
        businesses.checkAccess(user, businessId);
        return applications.findByBusinessIdOrderByCreatedAtDesc(businessId).stream().map(ApplicationView::of).toList();
    }

    ApplicationView application(CurrentUser user, UUID businessId, UUID id) {
        businesses.checkAccess(user, businessId);
        return ApplicationView.of(owned(businessId, id));
    }

    /** A new draft, filled in from the business's books and the chosen account. */
    @Transactional
    ApplicationView draft(CurrentUser user, UUID businessId, LoanTerms terms) {
        businesses.checkAccess(user, businessId);
        String name = businesses.registeredName(businessId).orElseThrow(() -> new NotFoundException("Business not found."));
        LoanApplication application = applications.save(LoanApplication.draft(businessId, name, terms, assess(businessId, terms)));
        log.info("Loan application {} drafted for business {} by user {}", application.getId(), businessId, user.id());
        return ApplicationView.of(application);
    }

    @Transactional
    ApplicationView change(CurrentUser user, UUID businessId, UUID id, LoanTerms terms) {
        businesses.checkAccess(user, businessId);
        LoanApplication application = owned(businessId, id);
        application.change(terms, assess(businessId, terms));
        return ApplicationView.of(application);
    }

    /** Sends the draft to the bank, assessed again on the figures of today. */
    @Transactional
    ApplicationView submit(CurrentUser user, UUID businessId, UUID id) {
        businesses.checkAccess(user, businessId);
        LoanApplication application = owned(businessId, id);
        application.submit(assessor.assess(businessId, application.getAccountId(), application.getAmount(), application.getTermMonths()),
            clock.instant());
        log.info("Loan application {} submitted by user {}", id, user.id());
        return ApplicationView.of(application);
    }

    private Assessment assess(UUID businessId, LoanTerms terms) {
        return assessor.assess(businessId, terms.accountId(), terms.amount(), terms.termMonths());
    }

    private LoanApplication owned(UUID businessId, UUID id) {
        return applications.findByIdAndBusinessId(id, businessId).orElseThrow(() -> new NotFoundException("Loan application not found."));
    }
}
