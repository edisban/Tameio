package com.tameio.banking;

import org.springframework.modulith.events.Externalized;

import java.math.BigDecimal;

/**
 * A SEPA credit transfer leaving the bank for clearing (a pacs.008 in ISO 20022 terms). Stored with the
 * booking's transaction and sent to the clearing house after it commits, so a payment is never debited
 * without being sent, nor sent without being debited.
 */
@Externalized(BankingMessaging.CLEARING_EXCHANGE + "::" + BankingMessaging.SUBMITTED_KEY)
public record SepaCreditTransferSubmitted(String endToEndId, String debtorIban, String debtorName, String creditorIban,
                                          String creditorName, BigDecimal amount, String reference) {
}
