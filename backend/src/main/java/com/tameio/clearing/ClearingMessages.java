package com.tameio.clearing;

import java.math.BigDecimal;

/**
 * The clearing house's side of the message contract with banks. Field names are the contract; the bank
 * declares its own types with the same fields.
 */
final class ClearingMessages {

    private ClearingMessages() {
    }

    static final String CLEARING_EXCHANGE = "tameio.clearing";
    static final String BANK_EXCHANGE = "tameio.banking";
    static final String SUBMITTED_QUEUE = "tameio.clearing.sct";
    static final String VOP_QUEUE = "tameio.clearing.vop";
    static final String RETURNS_QUEUE = "tameio.clearing.returns";

    /** A credit transfer a bank submits for clearing (pacs.008). */
    record CreditTransfer(String endToEndId, String debtorIban, String debtorName, String creditorIban, String creditorName,
                          BigDecimal amount, String reference) {
    }

    /** How a submitted transfer ended (pacs.002): SETTLED, or REJECTED with an ISO 20022 reason code. */
    record SettlementReport(String endToEndId, String status, String reasonCode, String reason) {
    }

    record DirectoryRequest(String iban) {
    }

    record DirectoryAnswer(boolean known, String holderName) {
    }
}
