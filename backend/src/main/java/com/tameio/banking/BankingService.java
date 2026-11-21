package com.tameio.banking;

import com.tameio.banking.BankingViews.AccountView;
import com.tameio.banking.BankingViews.TransactionView;
import com.tameio.identity.BusinessService;
import com.tameio.platform.security.CurrentUser;
import com.tameio.platform.validation.Iban;
import com.tameio.platform.web.NotFoundException;
import com.tameio.platform.web.UnprocessableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A business's accounts: opening them and reading their statements. Money comes in through the ledger
 * (demo history, demo deposits, and later loan disbursements). Every method checks membership first.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(BankingProperties.class)
@Transactional(readOnly = true)
class BankingService {

    /** Tameio's bank code in Greek IBANs, and its one branch. Fictional: no real bank uses 999. */
    static final String BANK_CODE = "999";
    static final String BRANCH = "0001";

    private final BankAccountRepository accounts;
    private final PostingRepository postings;
    private final Ledger ledger;
    private final BusinessService businesses;
    private final BankingProperties properties;

    List<AccountView> accounts(CurrentUser user, UUID businessId) {
        businesses.checkAccess(user, businessId);
        return accounts.findByBusinessIdOrderByCreatedAt(businessId).stream().map(AccountView::of).toList();
    }

    @Transactional
    AccountView open(CurrentUser user, UUID businessId, String name) {
        businesses.checkAccess(user, businessId);
        if (accounts.countByBusinessId(businessId) >= properties.maxAccounts()) {
            throw new UnprocessableException("A business can have at most " + properties.maxAccounts() + " accounts.");
        }
        String holder = businesses.registeredName(businessId).orElseThrow(() -> new NotFoundException("Business not found."));
        String iban = Iban.of("GR", BANK_CODE + BRANCH + "%016d".formatted(accounts.nextAccountNumber()));
        BankAccount account = accounts.save(BankAccount.openCurrent(businessId, holder, iban,
            StringUtils.hasText(name) ? name.strip() : "Λογαριασμός όψεως"));
        log.info("Account {} opened for business {} by user {}", account.getId(), businessId, user.id());
        return AccountView.of(account);
    }

    Page<TransactionView> statement(CurrentUser user, UUID businessId, UUID accountId, Pageable pageable) {
        businesses.checkAccess(user, businessId);
        BankAccount account = owned(businessId, accountId);
        return postings.statement(account.getId(), pageable).map(TransactionView::of);
    }

    /** A demo payment from a customer, for trying the app on a local stack. Off in production. */
    @Transactional
    TransactionView demoDeposit(CurrentUser user, UUID businessId, UUID accountId, BigDecimal amount) {
        businesses.checkAccess(user, businessId);
        if (!properties.demoDeposits()) {
            throw new NotFoundException("Demo deposits are not available.");
        }
        BankAccount account = owned(businessId, accountId);
        String payer = "Πελάτης Demo Α.Ε.";
        String payerIban = "GR9601100400000004012345678";
        JournalEntry entry = ledger.book(EntryKind.INCOMING_PAYMENT, "Incoming payment from " + payer, "Demo", user.id(), null, List.of(
            new Ledger.Leg(ledger.internal(InternalAccount.SETTLEMENT), amount.setScale(2).negate(), account.getHolderName(), account.getIban()),
            new Ledger.Leg(account.getId(), amount.setScale(2), payer, payerIban)));
        Posting credit = entry.getPostings().get(1);
        return new TransactionView(credit.getId(), entry.getId(), entry.getKind(), entry.getDescription(), entry.getReference(),
            credit.getAmount(), credit.getBalanceAfter(), payer, payerIban, entry.getBookedAt());
    }

    private BankAccount owned(UUID businessId, UUID accountId) {
        return accounts.findByIdAndBusinessId(accountId, businessId).orElseThrow(() -> new NotFoundException("Account not found."));
    }
}
