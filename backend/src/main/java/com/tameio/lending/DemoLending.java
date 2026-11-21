package com.tameio.lending;

import com.tameio.banking.LoanAccounts;
import com.tameio.identity.DemoBusinessReady;
import com.tameio.identity.DemoBusinessReady.Banker;
import com.tameio.platform.demo.DemoYear;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Demo loan applications, so the back office has a queue to work on: between them the demo businesses
 * have one application in every status. Each is taken through the same steps a person would take, so
 * every rule (four eyes, approval limits, the ledger) applies to the demo too.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class DemoLending {

    /** Who decides an application; above the manager's approval limit, the administrator. */
    private enum Decider { MANAGER, ADMINISTRATOR }

    /** One demo application: its terms, the status to take it to, and how it is judged on the way. */
    private record Plan(String amount, int months, LoanPurpose purpose, String description, LoanStatus until,
                        Recommendation outcome, Decider decider) {
    }

    private final LoanApplicationRepository applications;
    private final LoanAssessor assessor;
    private final LoanAccounts accounts;
    private final Clock clock;

    @EventListener
    @Order(DemoBusinessReady.LOANS)
    void on(DemoBusinessReady demo) {
        UUID accountId = accounts.accountIds(demo.businessId()).getFirst();
        for (Plan plan : plans(demo.profile())) {
            LoanApplication application = take(demo, accountId, plan);
            log.info("Demo loan application for {}: {} {}", demo.name(), plan.amount(), application.getStatus());
        }
    }

    private static List<Plan> plans(DemoYear.Profile profile) {
        return switch (profile) {
            case CAFE -> List.of(
                new Plan("25000", 36, LoanPurpose.EQUIPMENT, "Δεύτερη μηχανή espresso και ψυγεία βιτρίνας", LoanStatus.DISBURSED, Recommendation.APPROVE, Decider.MANAGER),
                new Plan("5000", 12, LoanPurpose.WORKING_CAPITAL, "Αποθέματα για τα Χριστούγεννα", LoanStatus.DRAFT, null, null));
            case BAKERY -> List.of(
                new Plan("40000", 60, LoanPurpose.EQUIPMENT, "Δεύτερος φούρνος καμπίνας", LoanStatus.RECOMMENDED, Recommendation.APPROVE, null),
                new Plan("8000", 18, LoanPurpose.WORKING_CAPITAL, "Αλεύρι για τρεις μήνες σε καλύτερη τιμή", LoanStatus.IN_REVIEW, null, null));
            case STUDIO -> List.of(
                new Plan("12000", 24, LoanPurpose.EXPANSION, "Δεύτερος γραφίστας και εξοπλισμός", LoanStatus.SUBMITTED, null, null),
                new Plan("6000", 12, LoanPurpose.EQUIPMENT, "Δύο σταθμοί εργασίας", LoanStatus.APPROVED, Recommendation.APPROVE, Decider.MANAGER));
            case TAVERNA -> List.of(
                new Plan("150000", 84, LoanPurpose.RENOVATION, "Ανακαίνιση και επέκταση αυλής", LoanStatus.DECLINED, Recommendation.DECLINE, Decider.ADMINISTRATOR));
        };
    }

    /** Takes a new application through the review up to the planned status. */
    private LoanApplication take(DemoBusinessReady demo, UUID accountId, Plan plan) {
        var terms = new LoanTerms(accountId, new BigDecimal(plan.amount()), plan.months(), plan.purpose(), plan.description());
        LoanApplication application = applications.save(LoanApplication.draft(demo.businessId(), demo.name(), terms,
            assessor.assess(demo.businessId(), accountId, terms.amount(), terms.termMonths())));
        Instant now = clock.instant();
        if (plan.until() == LoanStatus.DRAFT) {
            return application;
        }
        application.submit(assessor.assess(demo.businessId(), accountId, terms.amount(), terms.termMonths()), now);
        if (plan.until() == LoanStatus.SUBMITTED) {
            return application;
        }
        LoanApplication.Staff analyst = staff(demo.bankers().analyst());
        application.startReview(analyst, now);
        if (plan.until() == LoanStatus.IN_REVIEW) {
            return application;
        }
        application.recommend(analyst, plan.outcome(), recommendationNote(plan.outcome()), now);
        if (plan.until() == LoanStatus.RECOMMENDED) {
            return application;
        }
        Banker decider = plan.decider() == Decider.ADMINISTRATOR ? demo.bankers().administrator() : demo.bankers().manager();
        application.decide(staff(decider), plan.outcome(), decisionNote(plan.outcome()), now);
        if (plan.until() == LoanStatus.DISBURSED) {
            UUID entry = accounts.disburse(demo.businessId(), accountId, terms.amount(), "Loan " + application.getId(), decider.id());
            application.disbursed(entry, now);
        }
        return application;
    }

    private static LoanApplication.Staff staff(Banker banker) {
        return new LoanApplication.Staff(banker.id(), banker.name());
    }

    private static String recommendationNote(Recommendation outcome) {
        return outcome == Recommendation.APPROVE
            ? "Σταθερές εισπράξεις όλο τον χρόνο, η δόση καλύπτεται άνετα από το πλεόνασμα."
            : "Το ποσό είναι πολύ μεγάλο για τον τζίρο, και τον χειμώνα η επιχείρηση είναι κλειστή.";
    }

    private static String decisionNote(Recommendation outcome) {
        return outcome == Recommendation.APPROVE
            ? "Εγκρίνεται όπως προτάθηκε."
            : "Απορρίπτεται: η δόση ξεπερνά το μηνιαίο πλεόνασμα. Μια μικρότερη αίτηση θα εξεταστεί ευχαρίστως.";
    }
}
