package com.tameio.banking;

import com.tameio.platform.validation.Iban;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * What the clearing house tells the bank: how its outgoing payments ended (pacs.002) and which payments
 * from other banks arrived (pacs.008). Each message is handled in one transaction, and a message seen
 * twice changes nothing the second time.
 */
@Slf4j
@Component
class PaymentEvents {

    /** A settlement report for an outgoing payment. */
    record SettlementReport(String endToEndId, String status, String reasonCode, String reason) {
    }

    /** A credit transfer from another bank to an account here. */
    record IncomingCreditTransfer(String endToEndId, String debtorIban, String debtorName, String creditorIban,
                                  String creditorName, BigDecimal amount, String reference) {
    }

    private static final String SYSTEM = "clearing";

    private final PaymentRepository payments;
    private final BankAccountRepository accounts;
    private final Ledger ledger;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transaction;

    PaymentEvents(PaymentRepository payments, BankAccountRepository accounts, Ledger ledger, RabbitTemplate rabbit,
                  PlatformTransactionManager transactions) {
        this.payments = payments;
        this.accounts = accounts;
        this.ledger = ledger;
        this.rabbit = rabbit;
        this.transaction = new TransactionTemplate(transactions);
    }

    @RabbitListener(id = "sct-reports", queues = BankingMessaging.REPORTS_QUEUE)
    void onReport(SettlementReport report) {
        transaction.executeWithoutResult(status -> settle(report));
    }

    private void settle(SettlementReport report) {
        Optional<Payment> found = payments.findByDirectionAndEndToEndId(Payment.Direction.OUTGOING, report.endToEndId());
        if (found.isEmpty() || !found.get().isPending()) {
            log.info("Ignored a report for {}: unknown or already final", report.endToEndId());
            return;
        }
        Payment payment = found.get();
        BankAccount account = accounts.findById(payment.getAccountId()).orElseThrow();
        if ("SETTLED".equals(report.status())) {
            JournalEntry entry = ledger.book(EntryKind.SEPA_SETTLEMENT, "Settled " + payment.getEndToEndId(), payment.getReference(), SYSTEM, null, List.of(
                new Ledger.Leg(ledger.internal(InternalAccount.SEPA_OUT), payment.getAmount().negate(), payment.getCounterpartyName(), payment.getCounterpartyIban()),
                new Ledger.Leg(ledger.internal(InternalAccount.SETTLEMENT), payment.getAmount(), account.getHolderName(), account.getIban())));
            payment.settled(entry.getId());
            log.info("Payment {} settled", payment.getEndToEndId());
        } else {
            // Credited back even to a frozen account: it is the customer's own money coming home
            JournalEntry entry = ledger.book(EntryKind.SEPA_RETURN, "Returned " + report.reasonCode() + ": " + report.reason(),
                payment.getReference(), SYSTEM, null, List.of(
                    new Ledger.Leg(ledger.internal(InternalAccount.SEPA_OUT), payment.getAmount().negate(), account.getHolderName(), account.getIban()),
                    new Ledger.Leg(account.getId(), payment.getAmount(), payment.getCounterpartyName(), payment.getCounterpartyIban(), true)));
            payment.returned(entry.getId(), report.reasonCode(), report.reason());
            log.info("Payment {} returned: {} {}", payment.getEndToEndId(), report.reasonCode(), report.reason());
        }
    }

    @RabbitListener(id = "sct-incoming", queues = BankingMessaging.INCOMING_QUEUE)
    void onIncoming(IncomingCreditTransfer transfer) {
        String rejection = transaction.execute(status -> credit(transfer));
        if (rejection != null) {
            // Told after the transaction, which booked nothing: the paying bank gets its money back
            String[] parts = rejection.split(":", 2);
            rabbit.convertAndSend(BankingMessaging.CLEARING_EXCHANGE, "sct.return",
                new SettlementReport(transfer.endToEndId(), "REJECTED", parts[0], parts[1]));
            log.info("Returned incoming payment {}: {}", transfer.endToEndId(), rejection);
        }
    }

    /** Books the payment, or says why it must go back ("CODE:reason"). */
    private String credit(IncomingCreditTransfer transfer) {
        if (payments.findByDirectionAndEndToEndId(Payment.Direction.INCOMING, transfer.endToEndId()).isPresent()) {
            log.info("Ignored a repeated incoming payment {}", transfer.endToEndId());
            return null;
        }
        Optional<BankAccount> found = accounts.findByIban(Iban.normalise(transfer.creditorIban())).filter(BankAccount::isCustomer);
        if (found.isEmpty()) {
            return "AC01:No such account at Kyma Bank";
        }
        BankAccount account = found.get();
        if (account.getStatus() != BankAccount.Status.ACTIVE) {
            return "AC04:The account cannot receive payments";
        }
        BigDecimal amount = transfer.amount().setScale(2);
        JournalEntry entry = ledger.book(EntryKind.INCOMING_PAYMENT, "Incoming SEPA payment from " + transfer.debtorName(),
            transfer.reference(), SYSTEM, null, List.of(
                new Ledger.Leg(ledger.internal(InternalAccount.SETTLEMENT), amount.negate(), account.getHolderName(), account.getIban()),
                new Ledger.Leg(account.getId(), amount, transfer.debtorName(), Iban.normalise(transfer.debtorIban()))));
        payments.save(Payment.incoming(account, transfer.endToEndId(), amount, Iban.normalise(transfer.debtorIban()),
            transfer.debtorName(), transfer.reference(), entry.getId()));
        log.info("Credited incoming payment {} of {} to account {}", transfer.endToEndId(), amount, account.getId());
        return null;
    }
}
