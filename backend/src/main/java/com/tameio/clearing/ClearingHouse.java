package com.tameio.clearing;

import com.tameio.clearing.ClearingMessages.CreditTransfer;
import com.tameio.clearing.ClearingMessages.DirectoryAnswer;
import com.tameio.clearing.ClearingMessages.DirectoryRequest;
import com.tameio.clearing.ClearingMessages.SettlementReport;
import com.tameio.platform.validation.Iban;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Clears the transfers banks submit and reports back after a short delay, as a real clearing cycle
 * would; answers Verification of Payee lookups at once.
 */
@Slf4j
@Component
class ClearingHouse {

    private final RabbitTemplate rabbit;
    private final Duration delay;
    private final ScheduledExecutorService cycle = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());

    ClearingHouse(RabbitTemplate rabbit, @Value("${tameio.clearing.settlement-delay:3s}") Duration delay) {
        this.rabbit = rabbit;
        this.delay = delay;
    }

    @RabbitListener(id = "clearing-sct", queues = ClearingMessages.SUBMITTED_QUEUE)
    void onSubmitted(CreditTransfer transfer) {
        SettlementReport report = decide(transfer);
        log.info("Clearing {}: {} {}", transfer.endToEndId(), report.status(), report.reasonCode() == null ? "" : report.reasonCode());
        cycle.schedule(() -> rabbit.convertAndSend(ClearingMessages.BANK_EXCHANGE, "sct.report", report), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Verification of Payee: the name on an account at one of the other banks, if it exists. */
    @RabbitListener(id = "clearing-vop", queues = ClearingMessages.VOP_QUEUE)
    DirectoryAnswer onDirectoryRequest(DirectoryRequest request) {
        return OtherBanks.find(request.iban())
            .map(account -> new DirectoryAnswer(true, account.holderName()))
            .orElse(new DirectoryAnswer(false, null));
    }

    /** A bank sent back a payment it could not credit; the paying bank would be refunded here. */
    @RabbitListener(id = "clearing-returns", queues = ClearingMessages.RETURNS_QUEUE)
    void onReturn(SettlementReport report) {
        log.info("Payment {} returned by the receiving bank: {} {}", report.endToEndId(), report.reasonCode(), report.reason());
    }

    static SettlementReport decide(CreditTransfer transfer) {
        String iban = Iban.normalise(transfer.creditorIban());
        if (iban.endsWith("0000")) {
            return new SettlementReport(transfer.endToEndId(), "REJECTED", "AC04", "Closed account number");
        }
        if (iban.endsWith("9999")) {
            return new SettlementReport(transfer.endToEndId(), "REJECTED", "AC01", "Incorrect account number");
        }
        return new SettlementReport(transfer.endToEndId(), "SETTLED", null, null);
    }

    @PreDestroy
    void stop() {
        cycle.shutdown();
    }
}
