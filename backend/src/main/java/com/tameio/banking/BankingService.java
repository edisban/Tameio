package com.tameio.banking;

import com.tameio.banking.BankingViews.AccountView;
import com.tameio.banking.BankingViews.TransactionView;
import com.tameio.banking.BankingViews.TransferRequest;
import com.tameio.banking.BankingViews.TransferView;
import com.tameio.identity.BusinessService;
import com.tameio.platform.security.CurrentUser;
import com.tameio.platform.validation.Iban;
import com.tameio.platform.web.BadRequestException;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A business's accounts: opening them, reading statements and moving money. Every method checks membership first. */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(BankingProperties.class)
@Transactional(readOnly = true)
class BankingService {

    /** Kyma Bank's code in Greek IBANs, and its one branch. Fictional: no real bank uses 999. */
    static final String BANK_CODE = "999";
    static final String BRANCH = "0001";

    private final BankAccountRepository accounts;
    private final JournalEntryRepository entries;
    private final PostingRepository postings;
    private final Ledger ledger;
    private final BusinessService businesses;
    private final BankingProperties properties;

    /** A transfer, and whether it is the answer to an earlier request with the same Idempotency-Key. */
    record Transfer(TransferView view, boolean replayed) {
    }

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

    /**
     * A credit transfer to another account at this bank, booked at once. The Idempotency-Key makes a
     * retry safe: the same key with the same request returns the first transfer, never a second one.
     */
    @Transactional
    Transfer transfer(CurrentUser user, UUID businessId, UUID accountId, String idempotencyKey, TransferRequest request) {
        businesses.checkAccess(user, businessId);
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 100) {
            throw new BadRequestException("Send an Idempotency-Key header of 1 to 100 characters with every transfer.");
        }
        BankAccount debtor = owned(businessId, accountId);
        String creditorIban = Iban.normalise(request.creditorIban());
        String hash = hash(accountId + "|" + creditorIban + "|" + request.creditorName() + "|"
            + request.amount().setScale(2).toPlainString() + "|" + Objects.toString(request.reference(), ""));

        var earlier = entries.findByIdempotencyKey(user.id(), idempotencyKey);
        if (earlier.isPresent()) {
            if (!hash.equals(earlier.get().getRequestHash())) {
                throw new UnprocessableException("This Idempotency-Key was already used for a different request.");
            }
            return new Transfer(view(earlier.get(), debtor, request), true);
        }

        if (creditorIban.equals(debtor.getIban())) {
            throw new BadRequestException("The payee's account is the same as the paying one.");
        }
        if (!isOurs(creditorIban)) {
            throw new UnprocessableException("Transfers to other banks arrive with SEPA payments; for now, pay an account at Kyma Bank.");
        }
        BankAccount creditor = accounts.findByIban(creditorIban)
            .orElseThrow(() -> new UnprocessableException("There is no account with this IBAN at Kyma Bank."));

        BigDecimal amount = request.amount().setScale(2);
        JournalEntry entry = ledger.book(EntryKind.TRANSFER, "Transfer to " + request.creditorName(), request.reference(), user.id(),
            new Ledger.Idempotency(idempotencyKey, hash), List.of(
                new Ledger.Leg(debtor.getId(), amount.negate(), request.creditorName(), creditorIban),
                new Ledger.Leg(creditor.getId(), amount, debtor.getHolderName(), debtor.getIban())));
        log.info("Transfer {} of {} from account {} by user {}", entry.getId(), amount, debtor.getId(), user.id());
        return new Transfer(view(entry, debtor, request), false);
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

    private static TransferView view(JournalEntry entry, BankAccount debtor, TransferRequest request) {
        Posting debit = entry.getPostings().stream().filter(p -> p.getAccountId().equals(debtor.getId())).findFirst().orElseThrow();
        return new TransferView(entry.getId(), "COMPLETED", debit.getAmount().negate(), debtor.getIban(), debit.getCounterpartyIban(),
            debit.getCounterpartyName(), entry.getReference(), entry.getBookedAt(), debit.getBalanceAfter());
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime provides SHA-256", e);
        }
    }

    /** An IBAN issued by this bank: Greek, with Kyma Bank's code in the bank part. */
    static boolean isOurs(String iban) {
        String normalised = Iban.normalise(iban);
        return normalised != null && normalised.startsWith("GR") && normalised.length() == 27 && normalised.substring(4, 7).equals(BANK_CODE);
    }
}
