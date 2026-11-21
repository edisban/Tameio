package com.tameio.banking;

import com.tameio.platform.validation.Iban;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

/**
 * Verification of Payee. For an account at this bank the name is checked here; for another bank the
 * clearing house is asked for the account holder's name (request-reply over RabbitMQ), with a short
 * timeout: a payment must never wait long on it.
 */
@Slf4j
@Component
class PayeeCheck {

    enum Result {
        MATCH,
        /** Nearly the name on the account, which is returned so the payer can see it. */
        CLOSE_MATCH,
        NO_MATCH,
        /** The payee's bank did not answer or does not know the account. */
        NOT_POSSIBLE
    }

    /** The answer, with the account holder's name when the payer should see it (a close match). */
    record Answer(Result result, String actualName) {
    }

    /** What the clearing house is asked, and what it answers (JSON over RabbitMQ). */
    record DirectoryRequest(String iban) {
    }

    record DirectoryAnswer(boolean known, String holderName) {
    }

    private final BankAccountRepository accounts;
    private final RabbitTemplate rabbit;

    PayeeCheck(BankAccountRepository accounts, RabbitTemplate rabbit) {
        this.accounts = accounts;
        this.rabbit = rabbit;
    }

    Answer check(String iban, String typedName) {
        String normalised = Iban.normalise(iban);
        String holder = BankingService.isOurs(normalised)
            ? accounts.findByIban(normalised).map(BankAccount::getHolderName).orElse(null)
            : askClearingHouse(normalised);
        if (holder == null) {
            return new Answer(Result.NOT_POSSIBLE, null);
        }
        Result result = PayeeNames.compare(typedName, holder);
        return new Answer(result, result == Result.CLOSE_MATCH ? holder : null);
    }

    private String askClearingHouse(String iban) {
        try {
            DirectoryAnswer answer = rabbit.convertSendAndReceiveAsType(BankingMessaging.CLEARING_EXCHANGE, BankingMessaging.VOP_KEY,
                new DirectoryRequest(iban), new ParameterizedTypeReference<DirectoryAnswer>() { });
            return answer != null && answer.known() ? answer.holderName() : null;
        } catch (AmqpException e) {
            log.warn("Verification of Payee could not reach the clearing house: {}", e.getMessage());
            return null;
        }
    }
}
