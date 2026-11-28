package com.tameio.banking;

import com.tameio.platform.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What the lending module needs from banking: an account's money flows, to assess an application, and
 * paying out an approved loan through the ledger.
 */
@Service
@RequiredArgsConstructor
public class LoanAccounts {

    /** The period an assessment looks back over. */
    private static final int MONTHS_ASSESSED = 12;

    /**
     * An account's recent money flows.
     *
     * @param averageMonthlyInflow  money in over the last twelve months, per month, loans left out
     * @param averageMonthlyOutflow money out over the same months, per month
     * @param monthsOfHistory       whole months since the account's first movement
     * @param months                money in and out in each of the last twelve months the account existed, oldest first
     */
    public record AccountFigures(BigDecimal balance, BigDecimal averageMonthlyInflow, BigDecimal averageMonthlyOutflow,
                                 int monthsOfHistory, List<MonthFlows> months) {
    }

    /** One month's money in and out, loans paid into the account left out. */
    public record MonthFlows(BigDecimal inflow, BigDecimal outflow) {
    }

    private final BankAccountRepository accounts;
    private final PostingRepository postings;
    private final Ledger ledger;
    private final Clock clock;

    /** The business's accounts, oldest first. */
    @Transactional(readOnly = true)
    public List<UUID> accountIds(UUID businessId) {
        return accounts.findByBusinessIdOrderByCreatedAt(businessId).stream().map(BankAccount::getId).toList();
    }

    /** The figures of one of the business's accounts; another business's account is not found. */
    @Transactional(readOnly = true)
    public AccountFigures figures(UUID businessId, UUID accountId) {
        BankAccount account = owned(businessId, accountId);
        Instant now = clock.instant();
        Instant since = now.atOffset(ZoneOffset.UTC).minusMonths(MONTHS_ASSESSED).toInstant();
        PostingRepository.Flows flows = postings.flowsSince(account.getId(), since);
        Optional<Instant> first = postings.firstBooking(account.getId());
        int months = first
            .map(at -> (int) ChronoUnit.MONTHS.between(at.atOffset(ZoneOffset.UTC), now.atOffset(ZoneOffset.UTC)))
            .orElse(0);
        return new AccountFigures(account.getBalance(), perMonth(flows.inflow()), perMonth(flows.outflow()), months,
            monthByMonth(account.getId(), now, first.orElse(now)));
    }

    /**
     * The last twelve one-month windows back from now, each with its money in and out; a window that ended before
     * the account's first movement is left out, since the account did not exist to earn anything in it.
     */
    private List<MonthFlows> monthByMonth(UUID accountId, Instant now, Instant firstBooking) {
        Instant since = now.atOffset(ZoneOffset.UTC).minusMonths(MONTHS_ASSESSED).toInstant();
        List<PostingRepository.Movement> movements = postings.movementsSince(accountId, since);
        List<MonthFlows> months = new ArrayList<>();
        for (int back = MONTHS_ASSESSED; back >= 1; back--) {
            Instant from = now.atOffset(ZoneOffset.UTC).minusMonths(back).toInstant();
            Instant to = now.atOffset(ZoneOffset.UTC).minusMonths(back - 1L).toInstant();
            if (!to.isAfter(firstBooking)) {
                continue;
            }
            BigDecimal in = BigDecimal.ZERO;
            BigDecimal out = BigDecimal.ZERO;
            for (PostingRepository.Movement m : movements) {
                if (!m.bookedAt().isBefore(from) && m.bookedAt().isBefore(to)) {
                    in = m.amount().signum() > 0 ? in.add(m.amount()) : in;
                    out = m.amount().signum() < 0 ? out.subtract(m.amount()) : out;
                }
            }
            months.add(new MonthFlows(in, out));
        }
        return months;
    }

    /**
     * Pays an approved loan into the account: the bank's LOANS account goes down by the amount (the
     * business now owes it), the customer's account goes up. Runs in the caller's transaction, so the
     * loan's new status and the money move together. Returns the journal entry's id.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID disburse(UUID businessId, UUID accountId, BigDecimal amount, String reference, String initiatedBy) {
        BankAccount account = owned(businessId, accountId);
        BigDecimal value = amount.setScale(2, RoundingMode.UNNECESSARY);
        return ledger.book(EntryKind.LOAN_DISBURSEMENT, "Loan disbursement", reference, initiatedBy, List.of(
            new Ledger.Leg(ledger.internal(InternalAccount.LOANS), value.negate(), account.getHolderName(), account.getIban()),
            new Ledger.Leg(account.getId(), value, "Tameio · δάνειο", null))).getId();
    }

    private BankAccount owned(UUID businessId, UUID accountId) {
        return accounts.findByIdAndBusinessId(accountId, businessId).orElseThrow(() -> new NotFoundException("Account not found."));
    }

    private static BigDecimal perMonth(BigDecimal total) {
        return total.divide(BigDecimal.valueOf(MONTHS_ASSESSED), 2, RoundingMode.HALF_UP);
    }
}
