package com.tameio.banking;

import com.tameio.banking.BankingViews.AccountView;
import com.tameio.banking.BankingViews.PaymentView;
import com.tameio.banking.BankingViews.TransactionView;
import com.tameio.banking.BankingViews.TransferRequest;
import com.tameio.identity.BusinessService;
import com.tameio.platform.security.CurrentUser;
import com.tameio.platform.validation.Iban;
import com.tameio.platform.web.BadRequestException;
import com.tameio.platform.web.NotFoundException;
import com.tameio.platform.web.UnprocessableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
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

/** A business's accounts: opening them, reading statements and paying. Every method checks membership first. */
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
    private final JournalEntryRepository entries;
    private final PostingRepository postings;
    private final PaymentRepository payments;
    private final Ledger ledger;
    private final PayeeCheck payeeCheck;
    private final BusinessService businesses;
    private final BankingProperties properties;
    private final ApplicationEventPublisher events;

    /** A payment, and whether it is the answer to an earlier request with the same Idempotency-Key. */
    record Transfer(PaymentView view, boolean replayed) {
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

    Page<PaymentView> payments(CurrentUser user, UUID businessId, UUID accountId, Pageable pageable) {
        businesses.checkAccess(user, businessId);
        BankAccount account = owned(businessId, accountId);
        return payments.findByAccountId(account.getId(), pageable).map(p -> PaymentView.of(p, account, null));
    }

    /** Verification of Payee, asked before a payment so the payer sees whether the name matches. */
    PayeeCheck.Answer verifyPayee(String iban, String name) {
        return payeeCheck.check(iban, name);
    }

    /**
     * Pays another account: at this bank, booked at once; at another bank, sent to SEPA clearing and
     * settled or returned later. The payee's name is checked first (Verification of Payee); a name that
     * does not match needs the payer's explicit confirmation. The Idempotency-Key makes a retry safe:
     * the same key with the same request returns the first payment, never a second one.
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
            Payment payment = payments.findByBookingEntryId(earlier.get().getId()).orElseThrow();
            return new Transfer(PaymentView.of(payment, debtor, balanceAfter(earlier.get(), debtor)), true);
        }

        if (creditorIban.equals(debtor.getIban())) {
            throw new BadRequestException("The payee's account is the same as the paying one.");
        }
        BankAccount creditor = null;
        if (isOurs(creditorIban)) {
            creditor = accounts.findByIban(creditorIban)
                .orElseThrow(() -> new UnprocessableException("There is no account with this IBAN at Tameio."));
        }
        PayeeCheck.Answer check = payeeCheck.check(creditorIban, request.creditorName());
        if (check.result() == PayeeCheck.Result.NO_MATCH && !Boolean.TRUE.equals(request.confirmedMismatch())) {
            throw new UnprocessableException("The name does not match the payee's account. Check it, or confirm to pay anyway.");
        }

        BigDecimal amount = request.amount().setScale(2);
        var idempotency = new Ledger.Idempotency(idempotencyKey, hash);
        // ISO 20022 allows 35 characters: the bank's name, then 29 random ones
        String endToEndId = "TAMEIO" + UUID.randomUUID().toString().replace("-", "").substring(0, 29).toUpperCase();
        JournalEntry entry;
        Payment payment;
        if (creditor != null) {
            entry = ledger.book(EntryKind.TRANSFER, "Transfer to " + request.creditorName(), request.reference(), user.id(), idempotency, List.of(
                new Ledger.Leg(debtor.getId(), amount.negate(), request.creditorName(), creditorIban),
                new Ledger.Leg(creditor.getId(), amount, debtor.getHolderName(), debtor.getIban())));
            payment = Payment.outgoing(debtor, Payment.Scheme.INTERNAL, endToEndId, amount, creditorIban, request.creditorName(),
                request.reference(), check.result(), entry.getId());
        } else {
            entry = ledger.book(EntryKind.SEPA_PAYMENT, "SEPA payment to " + request.creditorName(), request.reference(), user.id(), idempotency, List.of(
                new Ledger.Leg(debtor.getId(), amount.negate(), request.creditorName(), creditorIban),
                new Ledger.Leg(ledger.internal(InternalAccount.SEPA_OUT), amount, debtor.getHolderName(), debtor.getIban())));
            payment = Payment.outgoing(debtor, Payment.Scheme.SEPA, endToEndId, amount, creditorIban, request.creditorName(),
                request.reference(), check.result(), entry.getId());
            events.publishEvent(new SepaCreditTransferSubmitted(endToEndId, debtor.getIban(), debtor.getHolderName(), creditorIban,
                request.creditorName(), amount, request.reference()));
        }
        payments.save(payment);
        log.info("{} payment {} of {} from account {} by user {} (payee check {})", payment.getScheme(), endToEndId, amount,
            debtor.getId(), user.id(), check.result());
        return new Transfer(PaymentView.of(payment, debtor, balanceAfter(entry, debtor)), false);
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

    private static BigDecimal balanceAfter(JournalEntry entry, BankAccount debtor) {
        return entry.getPostings().stream().filter(p -> p.getAccountId().equals(debtor.getId()))
            .findFirst().map(Posting::getBalanceAfter).orElse(null);
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime provides SHA-256", e);
        }
    }

    /** An IBAN issued by this bank: Greek, with Tameio's bank code in the bank part. */
    static boolean isOurs(String iban) {
        String normalised = Iban.normalise(iban);
        return normalised != null && normalised.startsWith("GR") && normalised.length() == 27 && normalised.substring(4, 7).equals(BANK_CODE);
    }
}
