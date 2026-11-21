package com.tameio.banking;

import com.tameio.platform.validation.ValidIban;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** What the banking API takes and returns. */
final class BankingViews {

    private BankingViews() {
    }

    record AccountView(UUID id, String iban, String name, String holderName, String currency, BigDecimal balance,
                       BankAccount.Status status, String statusReason, Instant openedAt) {

        static AccountView of(BankAccount a) {
            return new AccountView(a.getId(), a.getIban(), a.getName(), a.getHolderName(), a.getCurrency(), a.getBalance(),
                a.getStatus(), a.getStatusReason(), a.getCreatedAt());
        }
    }

    record OpenAccount(@Size(max = 100, message = "At most 100 characters") String name) {
    }

    record TransactionView(UUID id, UUID entryId, EntryKind kind, String description, String reference, BigDecimal amount,
                           BigDecimal balanceAfter, String counterpartyName, String counterpartyIban, Instant bookedAt) {

        static TransactionView of(PostingRepository.Line l) {
            return new TransactionView(l.id(), l.entryId(), l.kind(), l.description(), l.reference(), l.amount(),
                l.balanceAfter(), l.counterpartyName(), l.counterpartyIban(), l.bookedAt());
        }
    }

    /** A credit transfer. The reference is what the payee sees, such as an invoice number. */
    record TransferRequest(
        @NotBlank(message = "The payee's IBAN is required") @ValidIban String creditorIban,
        @NotBlank(message = "The payee's name is required") @Size(max = 140, message = "At most 140 characters") String creditorName,
        @NotNull(message = "The amount is required")
        @DecimalMin(value = "0.01", message = "The amount must be at least 0.01")
        @DecimalMax(value = "1000000.00", message = "At most 1,000,000.00 in one transfer")
        @Digits(integer = 7, fraction = 2, message = "At most two decimals") BigDecimal amount,
        @Size(max = 140, message = "At most 140 characters") String reference) {

        TransferRequest {
            creditorName = creditorName == null ? null : creditorName.strip();
            reference = reference == null || reference.isBlank() ? null : reference.strip();
        }
    }

    record TransferView(UUID id, String status, BigDecimal amount, String debtorIban, String creditorIban, String creditorName,
                        String reference, Instant bookedAt, BigDecimal balanceAfter) {
    }

    record DemoDeposit(
        @NotNull(message = "The amount is required")
        @DecimalMin(value = "0.01", message = "The amount must be at least 0.01")
        @DecimalMax(value = "10000.00", message = "At most 10,000.00 in one demo deposit")
        @Digits(integer = 5, fraction = 2, message = "At most two decimals") BigDecimal amount) {
    }

    // The back office

    record InternalBalance(String code, String name, BigDecimal balance) {
    }

    /**
     * The bank's books in one view: every internal account, the customers' deposits, and their sum, which
     * double entry keeps at exactly zero.
     */
    record TrialBalance(List<InternalBalance> internal, BigDecimal customerDeposits, long customerAccounts,
                        BigDecimal total, boolean balanced, long journalEntries) {
    }

    record Freeze(@NotBlank(message = "Give a reason") @Size(max = 200) String reason) {
    }
}
